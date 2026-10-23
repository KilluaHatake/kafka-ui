package io.kafbat.ui.service.approval;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.kafbat.ui.config.ApprovalProperties;
import io.kafbat.ui.config.auth.AuthenticatedUser;
import io.kafbat.ui.exception.CustomBaseException;
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
import io.kafbat.ui.service.AdminClientService;
import io.kafbat.ui.service.ClustersStorage;
import io.kafbat.ui.service.KafkaConnectService;
import io.kafbat.ui.service.MessagesService;
import io.kafbat.ui.service.ReactiveAdminClient;
import io.kafbat.ui.service.TopicsService;
import io.kafbat.ui.service.audit.AuditService;
import io.kafbat.ui.service.notification.NotificationService;
import jakarta.annotation.PostConstruct;
import java.io.Closeable;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalService implements Closeable {

  private final ApprovalProperties approvalProperties;
  private final ClustersStorage clustersStorage;
  private final AdminClientService adminClientService;
  private final AuditService auditService;
  private final TopicsService topicsService;
  private final KafkaConnectService kafkaConnectService;
  private final NotificationService notificationService;

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
    boolean gated = switch (type) {
      case TOPIC_CREATE -> approvalProperties.isRequireTopicCreation();
      case TOPIC_CONFIG_UPDATE -> approvalProperties.isRequireTopicConfigUpdates();
      case CONNECTOR_CREATE, CONNECTOR_UPDATE, CONNECTOR_ACTION -> approvalProperties.isRequireConnectorDeployments();
    };

    if (!gated) {
      return false;
    }

    // Auto-approve if user holds a direct apply role (e.g. Architect/Admin)
    boolean isDirectApply = user.groups() != null && user.groups().stream()
        .anyMatch(role -> approvalProperties.getDirectApplyRoles().contains(role));

    return !isDirectApply;
  }

  public boolean isApprover(AuthenticatedUser user) {
    return user.groups() != null && user.groups().stream()
        .anyMatch(role -> approvalProperties.getApproverRoles().contains(role));
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
      return Mono.error(new NotFoundException("Approval request %s not found in cluster %s".formatted(id, clusterName)));
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
        return Mono.error(new ValidationException("Only PENDING requests can be approved. Current status: " + request.getStatus()));
      }

      return executeApprovedAction(cluster, request)
          .then(Mono.fromCallable(() -> {
            String now = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
            request.setStatus(ApprovalStatus.EXECUTED);
            request.setApproverEmail(approver.principal());
            request.setUpdatedAt(now);

            requestsCache.computeIfAbsent(cluster.getName(), k -> new ConcurrentHashMap<>()).put(request.getId(), request);
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
          kafkaConnectService.setConnectorConfig(cluster, request.getConnectName(), request.getTargetName(), Mono.just(request.getNewConfig())).then();
      case CONNECTOR_ACTION -> {
        String actionStr = request.getMetadata() != null ? (String) request.getMetadata().get("action") : null;
        ConnectorActionDTO action = actionStr != null ? ConnectorActionDTO.fromValue(actionStr) : null;
        if (action == null) {
          yield Mono.error(new ValidationException("Missing connector action in approval request metadata"));
        }
        yield kafkaConnectService.updateConnectorState(cluster, request.getConnectName(), request.getTargetName(), action);
      }
    };
  }

  private void initTopicAndProducer(KafkaCluster cluster) {
    String topicName = approvalProperties.getTopic();
    try {
      adminClientService.get(cluster)
          .flatMap(ac -> ac.describeTopic(topicName)
              .onErrorResume(th -> {
                log.info("Creating approval topic '{}' for cluster '{}'", topicName, cluster.getName());
                Map<String, String> topicConfig = Map.of(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT);
                return ac.createTopic(topicName, approvalProperties.getPartitions(), null, topicConfig)
                    .then(Mono.empty());
              })
          )
          .timeout(Duration.ofSeconds(10))
          .subscribe(
              s -> log.debug("Approval topic checked for cluster '{}'", cluster.getName()),
              th -> log.warn("Could not check/create approval topic '{}' for cluster '{}'", topicName, cluster.getName(), th)
          );

      var producer = MessagesService.createProducer(cluster, Map.of());
      producers.put(cluster.getName(), producer);
    } catch (Exception e) {
      log.warn("Error initializing approval producer for cluster '{}'", cluster.getName(), e);
    }
  }

  private void writeRecordToTopic(String clusterName, ApprovalRequest request) {
    KafkaProducer<byte[], byte[]> producer = producers.get(clusterName);
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

  @Override
  public void close() {
    producers.values().forEach(p -> Optional.ofNullable(p).ifPresent(KafkaProducer::close));
  }
}
