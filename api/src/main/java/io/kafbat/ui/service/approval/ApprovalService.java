package io.kafbat.ui.service.approval;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import io.kafbat.ui.config.ApprovalProperties;
import io.kafbat.ui.config.ConnectAlertProperties;
import io.kafbat.ui.config.auth.AuthenticatedUser;
import io.kafbat.ui.exception.NotFoundException;
import io.kafbat.ui.exception.ValidationException;
import io.kafbat.ui.model.ConnectorActionDTO;
import io.kafbat.ui.model.KafkaCluster;
import io.kafbat.ui.model.NewConnectorDTO;
import io.kafbat.ui.model.TopicCreationDTO;
import io.kafbat.ui.model.TopicUpdateDTO;
import io.kafbat.ui.model.approval.ApprovalRequest;
import io.kafbat.ui.model.approval.ApprovalStatus;
import io.kafbat.ui.model.approval.ApprovalTargetType;
import io.kafbat.ui.model.rbac.AccessContext;
import io.kafbat.ui.model.rbac.Permission;
import io.kafbat.ui.model.rbac.Resource;
import io.kafbat.ui.service.AdminClientService;
import io.kafbat.ui.service.BrokerService;
import io.kafbat.ui.service.ClustersStorage;
import io.kafbat.ui.service.ConsumerGroupService;
import io.kafbat.ui.service.KafkaConnectService;
import io.kafbat.ui.service.MessagesService;
import io.kafbat.ui.service.ReactiveAdminClient;
import io.kafbat.ui.service.TopicsService;
import io.kafbat.ui.service.audit.AuditService;
import io.kafbat.ui.service.notification.NotificationService;
import io.kafbat.ui.service.rbac.AccessControlService;
import io.kafbat.ui.util.KafkaClientSslPropertiesUtil;
import jakarta.annotation.PostConstruct;
import java.io.Closeable;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalService implements Closeable {

  private static final Duration REACTIVE_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration TOPIC_BLOCK_TIMEOUT = Duration.ofSeconds(5);

  private final ApprovalProperties approvalProperties;
  private final ClustersStorage clustersStorage;
  private final AdminClientService adminClientService;
  private final AuditService auditService;
  private final TopicsService topicsService;
  private final KafkaConnectService kafkaConnectService;
  private final NotificationService notificationService;
  private final BrokerService brokerService;
  private final ConsumerGroupService consumerGroupService;
  private final ConnectAlertProperties alertProperties;
  private final AccessControlService accessControlService;

  // In-memory cache: clusterName -> (requestId -> ApprovalRequest)
  private final Map<String, Map<String, ApprovalRequest>> requestsCache = new ConcurrentHashMap<>();
  private final Map<String, KafkaProducer<byte[], byte[]>> producers = new ConcurrentHashMap<>();

  @PostConstruct
  public void init() {
    for (var cluster : clustersStorage.getKafkaClusters()) {
      requestsCache.computeIfAbsent(cluster.getName(), k -> new ConcurrentHashMap<>());
      initTopicAndProducer(cluster);
    }
  }

  public boolean isApprovalRequired(ApprovalTargetType type, AuthenticatedUser user) {
    if (!approvalProperties.isEnabled()) {
      return false;
    }

    boolean gated = switch (type) {
      case TOPIC_CREATE -> approvalProperties.isRequireTopicCreation();
      case TOPIC_CONFIG_UPDATE -> approvalProperties.isRequireTopicConfigUpdates();
      case CONNECTOR_CREATE, CONNECTOR_UPDATE, CONNECTOR_ACTION -> approvalProperties.isRequireConnectorDeployments();
      case BROKER_CONFIG_UPDATE -> approvalProperties.isRequireBrokerConfigUpdates();
      case ALERT_SETTINGS_UPDATE -> approvalProperties.isRequireAlertSettingsUpdates();
      case CONSUMER_GROUP_DELETE -> approvalProperties.isRequireConsumerGroupDelete();
      case CONNECTOR_RESET_OFFSETS -> approvalProperties.isRequireConnectorResetOffsets();
    };

    if (!gated) {
      return false;
    }

    return !isDirectApply(user);
  }

  public boolean isDirectApply(AuthenticatedUser user) {
    return user.groups() != null && user.groups().stream()
        .anyMatch(role -> approvalProperties.getDirectApplyRoles().contains(role));
  }

  public boolean isApprover(AuthenticatedUser user) {
    return user.groups() != null && user.groups().stream()
        .anyMatch(role -> approvalProperties.getApproverRoles().contains(role));
  }

  public boolean canManageWorkflowSettings(AuthenticatedUser user) {
    if (accessControlService != null && !accessControlService.isRbacEnabled()) {
      return true;
    }
    return isApprover(user) || isDirectApply(user);
  }

  public boolean canAccessApprovals(String clusterName, AuthenticatedUser user) {
    if (accessControlService != null && !accessControlService.isRbacEnabled()) {
      return true;
    }
    if (isApprover(user) || isDirectApply(user)) {
      return true;
    }
    if (accessControlService == null) {
      return true;
    }
    List<Permission> permissions = accessControlService.getUserPermissions(user, clusterName);
    if (permissions == null || permissions.isEmpty()) {
      return false;
    }
    return permissions.stream().anyMatch(this::hasMutationAction);
  }

  public boolean canAccessAlertSettings(AuthenticatedUser user, @Nullable String clusterName) {
    if (accessControlService != null && !accessControlService.isRbacEnabled()) {
      return true;
    }
    if (isApprover(user) || isDirectApply(user)) {
      return true;
    }
    if (accessControlService == null) {
      return true;
    }
    List<Permission> permissions = accessControlService.getUserPermissions(user, clusterName);
    if (permissions == null || permissions.isEmpty()) {
      return false;
    }
    return permissions.stream().anyMatch(p ->
        (p.getResource() == Resource.CONNECT
            || p.getResource() == Resource.CLUSTERCONFIG
            || p.getResource() == Resource.APPLICATIONCONFIG)
            && hasMutationAction(p)
    );
  }

  private boolean hasMutationAction(Permission permission) {
    if (permission.getActions() == null) {
      return false;
    }
    return permission.getActions().stream()
        .anyMatch(action -> !action.equalsIgnoreCase("view") && !action.equalsIgnoreCase("messages_read"));
  }

  public Mono<ApprovalRequest> createRequest(
      String clusterName,
      ApprovalTargetType type,
      String targetName,
      @Nullable String connectName,
      AuthenticatedUser requester,
      @Nullable Map<String, Object> oldConfig,
      Map<String, Object> newConfig,
      @Nullable Map<String, Object> metadata
  ) {
    String now = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    ApprovalRequest request = ApprovalRequest.builder()
        .id(UUID.randomUUID().toString())
        .clusterName(clusterName)
        .type(type)
        .targetName(targetName)
        .connectName(connectName)
        .requesterEmail(requester.principal())
        .requesterRoles(requester.groups() != null ? requester.groups().stream().toList() : java.util.List.of())
        .status(ApprovalStatus.PENDING)
        .createdAt(now)
        .updatedAt(now)
        .oldConfig(oldConfig)
        .newConfig(newConfig)
        .metadata(metadata)
        .build();

    requestsCache.computeIfAbsent(clusterName, k -> new ConcurrentHashMap<>()).put(request.getId(), request);
    writeRecordToTopic(clusterName, request);

    log.info("Created approval request {} for [{}] {} on cluster '{}' by '{}'",
        request.getId(), type, targetName, clusterName, requester.principal());

    return Mono.just(request);
  }

  public Flux<ApprovalRequest> getRequests(
      String clusterName,
      @Nullable ApprovalStatus status,
      @Nullable ApprovalTargetType type
  ) {
    Map<String, ApprovalRequest> clusterMap = requestsCache.getOrDefault(clusterName, Map.of());
    return Flux.fromIterable(clusterMap.values())
        .filter(r -> status == null || r.getStatus() == status)
        .filter(r -> type == null || r.getType() == type)
        .sort(Comparator.comparing(ApprovalRequest::getCreatedAt).reversed());
  }

  public Mono<ApprovalRequest> getRequest(String clusterName, String id) {
    Map<String, ApprovalRequest> clusterMap = requestsCache.get(clusterName);
    if (clusterMap == null || !clusterMap.containsKey(id)) {
      return Mono.error(new NotFoundException(
          "Approval request %s not found in cluster %s".formatted(id, clusterName)));
    }
    return Mono.just(clusterMap.get(id));
  }

  public Mono<ApprovalRequest> approveRequest(
      KafkaCluster cluster,
      String id,
      AuthenticatedUser approver
  ) {
    if (!isApprover(approver)) {
      return Mono.error(new AccessDeniedException("User is not authorized to approve configuration requests"));
    }

    return getRequest(cluster.getName(), id).flatMap(request -> {
      if (request.getStatus() != ApprovalStatus.PENDING) {
        return Mono.error(new ValidationException(
            "Only PENDING requests can be approved. Current status: " + request.getStatus()));
      }

      return executeApprovedAction(cluster, request)
          .then(Mono.fromCallable(() -> {
            String now = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
            request.setStatus(ApprovalStatus.EXECUTED);
            request.setApproverEmail(approver.principal());
            request.setUpdatedAt(now);

            requestsCache.computeIfAbsent(cluster.getName(), k -> new ConcurrentHashMap<>())
                .put(request.getId(), request);
            writeRecordToTopic(cluster.getName(), request);

            auditService.sendAuditRecord(
                AccessContext.builder()
                    .cluster(cluster.getName())
                    .operationName("APPROVAL_EXECUTE")
                    .operationParams(Map.of(
                        "approvalId", request.getId(),
                        "targetType", request.getType().name(),
                        "targetName", request.getTargetName(),
                        "requestedBy", request.getRequesterEmail(),
                        "approvedBy", approver.principal()
                    ))
                    .build(),
                approver
            );

            log.info("Approval request {} executed successfully by '{}'", request.getId(), approver.principal());
            return request;
          }))
          .onErrorResume(th -> {
            log.error("Failed to execute approved action for request {}", request.getId(), th);
            String now = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
            request.setStatus(ApprovalStatus.EXECUTION_FAILED);
            request.setApproverEmail(approver.principal());
            request.setExecutionError(th.getMessage());
            request.setUpdatedAt(now);

            writeRecordToTopic(cluster.getName(), request);
            return Mono.just(request);
          });
    });
  }

  public Mono<ApprovalRequest> rejectRequest(
      KafkaCluster cluster,
      String id,
      AuthenticatedUser approver,
      @Nullable String reason
  ) {
    if (!isApprover(approver)) {
      return Mono.error(new AccessDeniedException("User is not authorized to reject configuration requests"));
    }

    return getRequest(cluster.getName(), id).map(request -> {
      if (request.getStatus() != ApprovalStatus.PENDING) {
        throw new ValidationException("Only PENDING requests can be rejected. Current status: " + request.getStatus());
      }

      String now = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
      request.setStatus(ApprovalStatus.REJECTED);
      request.setApproverEmail(approver.principal());
      request.setRejectionReason(reason);
      request.setUpdatedAt(now);

      requestsCache.computeIfAbsent(cluster.getName(), k -> new ConcurrentHashMap<>()).put(request.getId(), request);
      writeRecordToTopic(cluster.getName(), request);

      auditService.sendAuditRecord(
          AccessContext.builder()
              .cluster(cluster.getName())
              .operationName("APPROVAL_REJECT")
              .operationParams(Map.of(
                  "approvalId", request.getId(),
                  "targetType", request.getType().name(),
                  "targetName", request.getTargetName(),
                  "rejectedBy", approver.principal(),
                  "reason", reason != null ? reason : ""
              ))
              .build(),
          approver
      );

      log.info("Approval request {} rejected by '{}'", request.getId(), approver.principal());
      return request;
    });
  }

  public Mono<Void> deleteRequest(
      KafkaCluster cluster,
      String id,
      AuthenticatedUser user
  ) {
    return getRequest(cluster.getName(), id).flatMap(request -> {
      boolean isRequester = request.getRequesterEmail() != null
          && request.getRequesterEmail().equalsIgnoreCase(user.principal());
      boolean approver = isApprover(user);

      if (!isRequester && !approver) {
        return Mono.error(new AccessDeniedException("User is not authorized to delete this request"));
      }
      if (request.getStatus() != ApprovalStatus.PENDING) {
        return Mono.error(new ValidationException(
            "Only PENDING requests can be deleted. Current status: " + request.getStatus()));
      }

      requestsCache.computeIfPresent(cluster.getName(), (k, map) -> {
        map.remove(id);
        return map;
      });
      writeTombstoneToTopic(cluster.getName(), id);

      log.info("Approval request {} deleted by '{}'", id, user.principal());
      return Mono.empty();
    });
  }

  @SuppressWarnings("unchecked")
  private Mono<Void> executeApprovedAction(KafkaCluster cluster, ApprovalRequest request) {
    return switch (request.getType()) {
      case TOPIC_CREATE -> {
        TopicCreationDTO dto = new TopicCreationDTO();
        dto.setName(request.getTargetName());
        if (request.getMetadata() != null) {
          dto.setPartitions((Integer) request.getMetadata().get("partitions"));
          dto.setReplicationFactor((Integer) request.getMetadata().get("replicationFactor"));
        }
        Map<String, String> configs = new HashMap<>();
        if (request.getNewConfig() != null) {
          request.getNewConfig().forEach((k, v) -> configs.put(k, String.valueOf(v)));
        }
        dto.setConfigs(configs);
        yield topicsService.createTopic(cluster, dto).then();
      }
      case TOPIC_CONFIG_UPDATE -> {
        TopicUpdateDTO dto = new TopicUpdateDTO();
        Map<String, String> configs = new HashMap<>();
        if (request.getNewConfig() != null) {
          request.getNewConfig().forEach((k, v) -> configs.put(k, String.valueOf(v)));
        }
        dto.setConfigs(configs);
        yield topicsService.updateTopic(cluster, request.getTargetName(), Mono.just(dto)).then();
      }
      case CONNECTOR_CREATE -> {
        NewConnectorDTO dto = new NewConnectorDTO();
        dto.setName(request.getTargetName());
        dto.setConfig(request.getNewConfig());
        yield kafkaConnectService.createConnector(cluster, request.getConnectName(), Mono.just(dto)).then();
      }
      case CONNECTOR_UPDATE ->
          kafkaConnectService.setConnectorConfig(
              cluster, request.getConnectName(), request.getTargetName(), Mono.just(request.getNewConfig())).then();
      case CONNECTOR_ACTION -> {
        String actionStr = request.getMetadata() != null ? (String) request.getMetadata().get("action") : null;
        ConnectorActionDTO action = actionStr != null ? ConnectorActionDTO.fromValue(actionStr) : null;
        if (action == null) {
          yield Mono.error(new ValidationException("Missing connector action in approval request metadata"));
        }
        yield kafkaConnectService.updateConnectorState(
            cluster, request.getConnectName(), request.getTargetName(), action);
      }
      case BROKER_CONFIG_UPDATE -> {
        Integer brokerId = (Integer) request.getMetadata().get("brokerId");
        String configName = (String) request.getMetadata().get("configName");
        String configValue = (String) request.getMetadata().get("configValue");
        yield brokerService.updateBrokerConfigByName(cluster, brokerId, configName, configValue);
      }
      case ALERT_SETTINGS_UPDATE -> {
        applyAlertSettingsUpdate(request.getNewConfig());
        yield Mono.empty();
      }
      case CONSUMER_GROUP_DELETE -> consumerGroupService.deleteConsumerGroupById(cluster, request.getTargetName());
      case CONNECTOR_RESET_OFFSETS -> kafkaConnectService.resetConnectorOffsets(
          cluster, request.getConnectName(), request.getTargetName()
      );
    };
  }

  @SuppressWarnings("unchecked")
  private void applyAlertSettingsUpdate(Map<String, Object> newConfig) {
    if (newConfig == null) {
      return;
    }
    if (newConfig.containsKey("enabled")) {
      alertProperties.setEnabled(Boolean.parseBoolean(String.valueOf(newConfig.get("enabled"))));
    }
    if (newConfig.containsKey("pollingIntervalMs")) {
      try {
        alertProperties.setPollingIntervalMs(Long.parseLong(String.valueOf(newConfig.get("pollingIntervalMs"))));
      } catch (NumberFormatException ignored) {
        // ignored
      }
    }
    if (newConfig.containsKey("throttleIntervalMinutes")) {
      try {
        alertProperties.setThrottleIntervalMinutes(
            Integer.parseInt(String.valueOf(newConfig.get("throttleIntervalMinutes"))));
      } catch (NumberFormatException ignored) {
        // ignored
      }
    }
    if (newConfig.get("slack") instanceof Map<?, ?> slackMap) {
      if (slackMap.containsKey("enabled")) {
        alertProperties.getSlack().setEnabled(Boolean.parseBoolean(String.valueOf(slackMap.get("enabled"))));
      }
      if (slackMap.containsKey("webhookUrl")) {
        alertProperties.getSlack().setWebhookUrl((String) slackMap.get("webhookUrl"));
      }
      if (slackMap.containsKey("channel")) {
        alertProperties.getSlack().setChannel((String) slackMap.get("channel"));
      }
    }
    if (newConfig.get("email") instanceof Map<?, ?> emailMap) {
      if (emailMap.containsKey("enabled")) {
        alertProperties.getEmail().setEnabled(Boolean.parseBoolean(String.valueOf(emailMap.get("enabled"))));
      }
      if (emailMap.containsKey("smtpHost")) {
        alertProperties.getEmail().setSmtpHost((String) emailMap.get("smtpHost"));
      }
      if (emailMap.containsKey("smtpPort")) {
        try {
          alertProperties.getEmail().setSmtpPort(Integer.parseInt(String.valueOf(emailMap.get("smtpPort"))));
        } catch (NumberFormatException ignored) {
          // ignored
        }
      }
      if (emailMap.containsKey("username")) {
        alertProperties.getEmail().setUsername((String) emailMap.get("username"));
      }
      if (emailMap.containsKey("password")) {
        String pass = (String) emailMap.get("password");
        if (pass != null && !pass.isBlank() && !pass.equals("********")) {
          alertProperties.getEmail().setPassword(pass);
        }
      }
      if (emailMap.containsKey("starttlsEnabled")) {
        alertProperties.getEmail().setStarttlsEnabled(
            Boolean.parseBoolean(String.valueOf(emailMap.get("starttlsEnabled"))));
      }
      if (emailMap.containsKey("sslEnabled")) {
        alertProperties.getEmail().setSslEnabled(Boolean.parseBoolean(String.valueOf(emailMap.get("sslEnabled"))));
      }
      if (emailMap.containsKey("from")) {
        alertProperties.getEmail().setFrom((String) emailMap.get("from"));
      }
      if (emailMap.get("recipients") instanceof List<?> list) {
        alertProperties.getEmail().setRecipients(list.stream().map(String::valueOf).toList());
      }
    }
  }

  private void initTopicAndProducer(KafkaCluster cluster) {
    if (!approvalProperties.isEnabled()) {
      return;
    }
    String topicName = Optional.ofNullable(approvalProperties.getTopic())
        .orElse(ApprovalProperties.DEFAULT_APPROVAL_TOPIC);

    Supplier<ReactiveAdminClient> adminClientSupplier = () -> adminClientService.get(cluster)
        .timeout(REACTIVE_TIMEOUT)
        .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
            .maxBackoff(Duration.ofSeconds(10))
        )
        .block(BLOCK_TIMEOUT);

    boolean topicReady = createTopicIfNeeded(cluster, adminClientSupplier, topicName);
    if (topicReady) {
      try {
        var producer = MessagesService.createProducer(cluster, Map.of(
            ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip"
        ));
        producers.put(cluster.getName(), producer);
        replayRequestsFromTopic(cluster);
      } catch (Exception e) {
        log.warn("Error initializing approval producer for cluster '{}'", cluster.getName(), e);
      }
    } else {
      log.warn("Approval topic '{}' could not be verified or created for cluster '{}'",
          topicName, cluster.getName());
    }
  }

  @VisibleForTesting
  boolean createTopicIfNeeded(KafkaCluster cluster,
                              Supplier<ReactiveAdminClient> acSupplier,
                              String approvalTopicName) {
    ReactiveAdminClient ac;
    try {
      ac = acSupplier.get();
    } catch (Exception e) {
      log.warn("Error while connecting to cluster '{}' to create approval topic '{}'",
          cluster.getName(), approvalTopicName, e);
      return false;
    }

    try {
      boolean topicExists = Objects.requireNonNull(ac.listTopics(true).block(TOPIC_BLOCK_TIMEOUT))
          .contains(approvalTopicName);

      if (topicExists) {
        log.debug("Approval topic '{}' already exists for cluster '{}'", approvalTopicName, cluster.getName());
        return true;
      }
    } catch (Exception e) {
      log.warn("Error while checking existence of approval topic '{}' for cluster '{}'",
          approvalTopicName, cluster.getName(), e);
      return false;
    }

    try {
      Map<String, String> topicConfig = new HashMap<>();
      topicConfig.put(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT);
      Optional.ofNullable(approvalProperties.getTopicProperties())
          .ifPresent(topicConfig::putAll);

      int topicPartitions = approvalProperties.getPartitions() > 0
          ? approvalProperties.getPartitions()
          : 1;

      log.info("Creating approval topic '{}' for cluster '{}'", approvalTopicName, cluster.getName());
      ac.createTopic(approvalTopicName, topicPartitions, null, topicConfig).block(TOPIC_BLOCK_TIMEOUT);
      log.info("Approval topic '{}' created for cluster '{}'", approvalTopicName, cluster.getName());
      return true;
    } catch (Exception e) {
      log.warn("Error creating approval topic '{}' for cluster '{}'", approvalTopicName, cluster.getName(), e);
      return false;
    }
  }

  public void replayRequestsFromTopic(KafkaCluster cluster) {
    String topicName = approvalProperties.getTopic();
    try (var consumer = createConsumer(cluster)) {
      var partitionInfos = consumer.partitionsFor(topicName, Duration.ofSeconds(5));
      if (partitionInfos == null || partitionInfos.isEmpty()) {
        return;
      }
      List<TopicPartition> partitions = partitionInfos.stream()
          .map(pi -> new TopicPartition(pi.topic(), pi.partition()))
          .toList();
      consumer.assign(partitions);
      consumer.seekToBeginning(partitions);
      Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions, Duration.ofSeconds(5));

      boolean allEmpty = partitions.stream().allMatch(tp -> {
        Long end = endOffsets.get(tp);
        return end == null || end == 0L;
      });

      if (allEmpty) {
        log.debug("Approval topic '{}' on cluster '{}' is empty, nothing to replay", topicName, cluster.getName());
        return;
      }

      int count = 0;
      long startTime = System.currentTimeMillis();
      while (System.currentTimeMillis() - startTime < 10000) {
        var records = consumer.poll(Duration.ofMillis(500));
        for (var record : records) {
          if (record.value() != null) {
            try {
              ApprovalRequest request = ApprovalRequest.fromJson(new String(record.value(), UTF_8));
              requestsCache.computeIfAbsent(cluster.getName(), k -> new ConcurrentHashMap<>())
                  .put(request.getId(), request);
              count++;
            } catch (Exception ex) {
              log.warn("Failed to deserialize approval record at offset {} in {}",
                  record.offset(), record.partition(), ex);
            }
          } else if (record.key() != null) {
            String tombstoneId = new String(record.key(), UTF_8);
            requestsCache.computeIfPresent(cluster.getName(), (k, map) -> {
              map.remove(tombstoneId);
              return map;
            });
          }
        }
        boolean finished = partitions.stream()
            .allMatch(tp -> consumer.position(tp) >= endOffsets.getOrDefault(tp, 0L));
        if (finished || records.isEmpty()) {
          break;
        }
      }
      log.info("Replayed {} approval requests from topic '{}' on cluster '{}'", count, topicName, cluster.getName());
    } catch (Exception e) {
      log.warn("Failed to replay approval requests from topic '{}' for cluster '{}'",
          topicName, cluster.getName(), e);
    }
  }

  public KafkaConsumer<byte[], byte[]> createConsumer(KafkaCluster cluster) {
    Properties properties = new Properties();
    KafkaClientSslPropertiesUtil.addKafkaSslProperties(cluster.getOriginalProperties().getSsl(), properties);
    properties.putAll(cluster.getProperties());
    properties.putAll(cluster.getConsumerProperties());
    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.getBootstrapServers());
    properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    properties.put(ConsumerConfig.GROUP_ID_CONFIG, "kafbat-ui-approval-replay-" + UUID.randomUUID());
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "5000");
    return new KafkaConsumer<>(properties);
  }

  private void writeRecordToTopic(String clusterName, ApprovalRequest request) {
    KafkaProducer<byte[], byte[]> producer = producers.computeIfAbsent(clusterName, name ->
        clustersStorage.getClusterByName(name)
            .map(c -> MessagesService.createProducer(c, Map.of()))
            .orElse(null)
    );
    if (producer != null) {
      try {
        String json = request.toJson();
        producer.send(
            new ProducerRecord<>(approvalProperties.getTopic(), request.getId().getBytes(UTF_8), json.getBytes(UTF_8)),
            (metadata, ex) -> {
              if (ex != null) {
                log.warn("Error writing approval record to topic on cluster {}", clusterName, ex);
              }
            }
        );
      } catch (Exception e) {
        log.warn("Error serializing approval request for topic write", e);
      }
    }
  }

  private void writeTombstoneToTopic(String clusterName, String id) {
    KafkaProducer<byte[], byte[]> producer = producers.computeIfAbsent(clusterName, name ->
        clustersStorage.getClusterByName(name)
            .map(c -> MessagesService.createProducer(c, Map.of()))
            .orElse(null)
    );
    if (producer != null) {
      try {
        producer.send(
            new ProducerRecord<>(approvalProperties.getTopic(), id.getBytes(UTF_8), null),
            (metadata, ex) -> {
              if (ex != null) {
                log.warn("Error writing approval tombstone to topic on cluster {}", clusterName, ex);
              }
            }
        );
      } catch (Exception e) {
        log.warn("Error sending approval tombstone to topic", e);
      }
    }
  }

  @Override
  public void close() {
    producers.values().forEach(p -> Optional.ofNullable(p).ifPresent(KafkaProducer::close));
  }
}
