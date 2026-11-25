package io.kafbat.ui.service.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.kafbat.ui.config.ApprovalProperties;
import io.kafbat.ui.config.ClustersProperties;
import io.kafbat.ui.config.auth.AuthenticatedUser;
import io.kafbat.ui.model.KafkaCluster;
import io.kafbat.ui.model.TopicCreationDTO;
import io.kafbat.ui.model.approval.ApprovalRequest;
import io.kafbat.ui.model.approval.ApprovalStatus;
import io.kafbat.ui.model.approval.ApprovalTargetType;
import io.kafbat.ui.model.rbac.Permission;
import io.kafbat.ui.service.AdminClientService;
import io.kafbat.ui.service.ClustersStorage;
import io.kafbat.ui.service.KafkaConfigSanitizer;
import io.kafbat.ui.service.KafkaConnectService;
import io.kafbat.ui.service.ReactiveAdminClient;
import io.kafbat.ui.service.TopicsService;
import io.kafbat.ui.service.audit.AuditService;
import io.kafbat.ui.service.rbac.AccessControlService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ApprovalServiceTest {

  private ApprovalProperties approvalProperties;
  private ClustersStorage clustersStorage;
  private AdminClientService adminClientService;
  private AuditService auditService;
  private TopicsService topicsService;
  private KafkaConnectService kafkaConnectService;
  private io.kafbat.ui.service.BrokerService brokerService;
  private io.kafbat.ui.service.ConsumerGroupService consumerGroupService;
  private AccessControlService accessControlService;
  private ApprovalService approvalService;

  private KafkaCluster cluster;

  @BeforeEach
  void setUp() {
    approvalProperties = new ApprovalProperties();
    approvalProperties.setRequireTopicCreation(true);
    approvalProperties.setRequireTopicConfigUpdates(true);
    approvalProperties.setRequireConnectorDeployments(true);
    approvalProperties.setRequireBrokerConfigUpdates(true);
    approvalProperties.setRequireConsumerGroupDelete(true);
    approvalProperties.setRequireConnectorResetOffsets(true);
    approvalProperties.setApproverRoles(List.of("Architect", "Admin"));
    approvalProperties.setDirectApplyRoles(List.of("Architect", "Admin"));

    clustersStorage = mock(ClustersStorage.class);
    adminClientService = mock(AdminClientService.class);
    auditService = mock(AuditService.class);
    topicsService = mock(TopicsService.class);
    kafkaConnectService = mock(KafkaConnectService.class);
    brokerService = mock(io.kafbat.ui.service.BrokerService.class);
    consumerGroupService = mock(io.kafbat.ui.service.ConsumerGroupService.class);
    accessControlService = mock(AccessControlService.class);
    when(accessControlService.isRbacEnabled()).thenReturn(true);

    cluster = KafkaCluster.builder().name("local-cluster").build();

    KafkaConfigSanitizer sanitizer = new KafkaConfigSanitizer(true, List.of());

    approvalService = new ApprovalService(
        approvalProperties,
        clustersStorage,
        adminClientService,
        auditService,
        topicsService,
        kafkaConnectService,
        brokerService,
        consumerGroupService,
        accessControlService,
        sanitizer
    );
  }

  @Test
  void isApprovalRequiredReturnsTrueForDeveloperAndFalseForArchitect() {
    final AuthenticatedUser devUser = new AuthenticatedUser("dev@company.com", List.of("Developer"));
    AuthenticatedUser architectUser = new AuthenticatedUser("arch@company.com", List.of("Architect"));

    assertThat(approvalService.isApprovalRequired(ApprovalTargetType.TOPIC_CREATE, devUser)).isTrue();
    assertThat(approvalService.isApprovalRequired(ApprovalTargetType.TOPIC_CREATE, architectUser)).isFalse();
    assertThat(approvalService.isApprovalRequired(ApprovalTargetType.CONNECTOR_CREATE, devUser)).isTrue();
    assertThat(approvalService.isApprovalRequired(ApprovalTargetType.CONNECTOR_CREATE, architectUser)).isFalse();
    assertThat(approvalService.isApprovalRequired(ApprovalTargetType.BROKER_CONFIG_UPDATE, devUser)).isTrue();
    assertThat(approvalService.isApprovalRequired(ApprovalTargetType.BROKER_CONFIG_UPDATE, architectUser)).isFalse();
    assertThat(approvalService.isApprovalRequired(ApprovalTargetType.CONSUMER_GROUP_DELETE, devUser)).isTrue();
    assertThat(approvalService.isApprovalRequired(ApprovalTargetType.CONSUMER_GROUP_DELETE, architectUser)).isFalse();
    assertThat(approvalService.isApprovalRequired(ApprovalTargetType.CONNECTOR_RESET_OFFSETS, devUser)).isTrue();
    assertThat(approvalService.isApprovalRequired(ApprovalTargetType.CONNECTOR_RESET_OFFSETS, architectUser)).isFalse();

    approvalProperties.setRequireBrokerConfigUpdates(false);
    assertThat(approvalService.isApprovalRequired(ApprovalTargetType.BROKER_CONFIG_UPDATE, devUser)).isFalse();
  }

  @Test
  void isApproverValidatesRoles() {
    final AuthenticatedUser devUser = new AuthenticatedUser("dev@company.com", List.of("Developer"));
    AuthenticatedUser architectUser = new AuthenticatedUser("arch@company.com", List.of("Architect"));

    assertThat(approvalService.isApprover(devUser)).isFalse();
    assertThat(approvalService.isApprover(architectUser)).isTrue();
  }

  @Test
  void createRequestAndQueryLifecycle() {
    final AuthenticatedUser devUser = new AuthenticatedUser("dev@company.com", List.of("Developer"));

    ApprovalRequest request = approvalService.createRequest(
        "local-cluster",
        ApprovalTargetType.TOPIC_CREATE,
        "new-orders",
        null,
        devUser,
        null,
        Map.of("cleanup.policy", "delete"),
        Map.of("partitions", 3, "replicationFactor", 1)
    ).block();

    assertThat(request).isNotNull();
    assertThat(request.getId()).isNotBlank();
    assertThat(request.getStatus()).isEqualTo(ApprovalStatus.PENDING);
    assertThat(request.getTargetName()).isEqualTo("new-orders");
    assertThat(request.getRequesterEmail()).isEqualTo("dev@company.com");

    List<ApprovalRequest> list = approvalService.getRequests("local-cluster", null, null).collectList().block();
    assertThat(list).hasSize(1);
    assertThat(list.get(0).getId()).isEqualTo(request.getId());

    ApprovalRequest fetched = approvalService.getRequest("local-cluster", request.getId()).block();
    assertThat(fetched).isNotNull();
    assertThat(fetched.getId()).isEqualTo(request.getId());
  }

  @Test
  void approveRequestExecutesActionAndSendsAudit() {
    final AuthenticatedUser devUser = new AuthenticatedUser("dev@company.com", List.of("Developer"));
    AuthenticatedUser architectUser = new AuthenticatedUser("arch@company.com", List.of("Architect"));

    when(topicsService.createTopic(eq(cluster), any())).thenReturn(Mono.empty());

    ApprovalRequest request = approvalService.createRequest(
        "local-cluster",
        ApprovalTargetType.TOPIC_CREATE,
        "new-orders",
        null,
        devUser,
        null,
        Map.of("cleanup.policy", "delete"),
        Map.of("partitions", 3, "replicationFactor", 1)
    ).block();

    ApprovalRequest approved = approvalService.approveRequest(cluster, request.getId(), architectUser).block();

    assertThat(approved).isNotNull();
    assertThat(approved.getStatus()).isEqualTo(ApprovalStatus.EXECUTED);
    assertThat(approved.getApproverEmail()).isEqualTo("arch@company.com");

    verify(topicsService).createTopic(eq(cluster), any(TopicCreationDTO.class));
    verify(auditService).sendAuditRecord(any(), eq(architectUser));
  }

  @Test
  void rejectRequestUpdatesStatusAndSendsAudit() {
    final AuthenticatedUser devUser = new AuthenticatedUser("dev@company.com", List.of("Developer"));
    AuthenticatedUser architectUser = new AuthenticatedUser("arch@company.com", List.of("Architect"));

    ApprovalRequest request = approvalService.createRequest(
        "local-cluster",
        ApprovalTargetType.CONNECTOR_CREATE,
        "s3-sink",
        "connect-01",
        devUser,
        null,
        Map.of("connector.class", "io.confluent.connect.s3.S3SinkConnector"),
        null
    ).block();

    ApprovalRequest rejected = approvalService.rejectRequest(
        cluster,
        request.getId(),
        architectUser,
        "Invalid bucket configuration"
    ).block();

    assertThat(rejected).isNotNull();
    assertThat(rejected.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
    assertThat(rejected.getApproverEmail()).isEqualTo("arch@company.com");
    assertThat(rejected.getRejectionReason()).isEqualTo("Invalid bucket configuration");

    verify(auditService).sendAuditRecord(any(), eq(architectUser));
  }

  @Test
  void nonApproverCannotApproveOrReject() {
    final AuthenticatedUser devUser = new AuthenticatedUser("dev@company.com", List.of("Developer"));

    ApprovalRequest request = approvalService.createRequest(
        "local-cluster",
        ApprovalTargetType.TOPIC_CREATE,
        "test-topic",
        null,
        devUser,
        null,
        Map.of(),
        null
    ).block();

    StepVerifier.create(approvalService.approveRequest(cluster, request.getId(), devUser))
        .expectError(AccessDeniedException.class)
        .verify();

    StepVerifier.create(approvalService.rejectRequest(cluster, request.getId(), devUser, "No"))
        .expectError(AccessDeniedException.class)
        .verify();
  }

  @Test
  void testRbacPermissionsChecksForDifferentRoles() {
    when(accessControlService.isRbacEnabled()).thenReturn(true);

    final AuthenticatedUser supportUser = new AuthenticatedUser("support@company.com", List.of("Support"));
    Permission supportTopicPerm = new Permission();
    supportTopicPerm.setResource("TOPIC");
    supportTopicPerm.setActions(List.of("view", "messages_read"));
    Permission supportConnectPerm = new Permission();
    supportConnectPerm.setResource("CONNECT");
    supportConnectPerm.setActions(List.of("view"));
    when(accessControlService.getUserPermissions(supportUser, "local-cluster"))
        .thenReturn(List.of(supportTopicPerm, supportConnectPerm));

    assertThat(approvalService.isApprover(supportUser)).isFalse();
    assertThat(approvalService.canManageWorkflowSettings(supportUser)).isFalse();
    assertThat(approvalService.canAccessApprovals("local-cluster", supportUser)).isFalse();

    final AuthenticatedUser devUser = new AuthenticatedUser("dev@company.com", List.of("Developer"));
    Permission devTopicPerm = new Permission();
    devTopicPerm.setResource("TOPIC");
    devTopicPerm.setActions(List.of("all"));
    Permission devConnectPerm = new Permission();
    devConnectPerm.setResource("CONNECT");
    devConnectPerm.setActions(List.of("all"));
    when(accessControlService.getUserPermissions(devUser, "local-cluster"))
        .thenReturn(List.of(devTopicPerm, devConnectPerm));

    assertThat(approvalService.isApprover(devUser)).isFalse();
    assertThat(approvalService.canManageWorkflowSettings(devUser)).isFalse();
    assertThat(approvalService.canAccessApprovals("local-cluster", devUser)).isTrue();

    AuthenticatedUser archUser = new AuthenticatedUser("arch@company.com", List.of("Architect"));
    assertThat(approvalService.isApprover(archUser)).isTrue();
    assertThat(approvalService.canManageWorkflowSettings(archUser)).isTrue();
    assertThat(approvalService.canAccessApprovals("local-cluster", archUser)).isTrue();
  }

  @Test
  void createTopicIfNeededReturnsTrueWhenTopicAlreadyExists() {
    ReactiveAdminClient ac = mock(ReactiveAdminClient.class);
    when(ac.listTopics(true)).thenReturn(Mono.just(Set.of("__kui-approval-requests")));

    boolean result = approvalService.createTopicIfNeeded(cluster, () -> ac, "__kui-approval-requests");
    assertThat(result).isTrue();
  }

  @Test
  void createTopicIfNeededCreatesTopicWhenNotExists() {
    ReactiveAdminClient ac = mock(ReactiveAdminClient.class);
    when(ac.listTopics(true)).thenReturn(Mono.just(Set.of()));
    when(ac.createTopic(eq("__kui-approval-requests"), eq(1), any(), any())).thenReturn(Mono.empty());

    boolean result = approvalService.createTopicIfNeeded(cluster, () -> ac, "__kui-approval-requests");
    assertThat(result).isTrue();
    verify(ac).createTopic(eq("__kui-approval-requests"), eq(1), any(), any());
  }

  @Test
  void sanitizesSensitivePropertiesForDisplay() {
    ApprovalRequest raw = ApprovalRequest.builder()
        .id("req-1")
        .clusterName("local-cluster")
        .type(ApprovalTargetType.TOPIC_CONFIG_UPDATE)
        .targetName("topic-a")
        .requesterEmail("dev@test.com")
        .oldConfig(Map.of("retention.ms", "1000", "ssl.keystore.password", "secret123"))
        .newConfig(Map.of("retention.ms", "2000", "ssl.keystore.password", "newsecret", "api.token", "xyz"))
        .metadata(Map.of("partitions", 3, "admin.password", "admin123"))
        .build();

    ApprovalRequest sanitized = approvalService.sanitizeForDisplay(raw);

    assertThat(sanitized.getOldConfig().get("retention.ms")).isEqualTo("1000");
    assertThat(sanitized.getOldConfig().get("ssl.keystore.password")).isEqualTo("******");

    assertThat(sanitized.getNewConfig().get("retention.ms")).isEqualTo("2000");
    assertThat(sanitized.getNewConfig().get("ssl.keystore.password")).isEqualTo("******");
    assertThat(sanitized.getNewConfig().get("api.token")).isEqualTo("******");

    assertThat(sanitized.getMetadata().get("partitions")).isEqualTo(3);
    assertThat(sanitized.getMetadata().get("admin.password")).isEqualTo("******");
  }

  @Test
  void isApproverAndDirectApplyRespectClusterScoping() {
    io.kafbat.ui.model.rbac.Role devRole = new io.kafbat.ui.model.rbac.Role();
    devRole.setName("Admin");
    devRole.setClusters(List.of("dev-cluster"));

    when(accessControlService.getRoles()).thenReturn(List.of(devRole));

    AuthenticatedUser adminUser = new AuthenticatedUser("admin@company.com", List.of("Admin"));

    // Allowed on dev-cluster
    assertThat(approvalService.isApprover("dev-cluster", adminUser)).isTrue();
    assertThat(approvalService.isDirectApply("dev-cluster", adminUser)).isTrue();
    assertThat(approvalService.isApprovalRequired(ApprovalTargetType.TOPIC_CREATE, "dev-cluster", adminUser)).isFalse();

    // Denied on prod-cluster
    assertThat(approvalService.isApprover("prod-cluster", adminUser)).isFalse();
    assertThat(approvalService.isDirectApply("prod-cluster", adminUser)).isFalse();
    assertThat(approvalService.isApprovalRequired(ApprovalTargetType.TOPIC_CREATE, "prod-cluster", adminUser)).isTrue();
  }

  @Test
  void isApprovalTopicMatchesDefaultAndConfiguredTopic() {
    assertThat(approvalService.isApprovalTopic("__kui-approval-requests")).isTrue();
    assertThat(approvalService.isApprovalTopic("normal-topic")).isFalse();
    assertThat(approvalService.isApprovalTopic(null)).isFalse();
  }

  @Test
  void clusterSpecificApprovalPropertiesOverrideGlobal() {
    var clusterProps = new ClustersProperties.Cluster();
    var clusterApproval = ApprovalProperties.builder()
        .enabled(true)
        .topic("__cluster-approval-topic")
        .partitions(2)
        .topicProperties(Map.of("cleanup.policy", "compact"))
        .requireTopicCreation(true)
        .requireTopicConfigUpdates(true)
        .requireConnectorDeployments(true)
        .requireConsumerGroupDelete(true)
        .requireConnectorResetOffsets(true)
        .approverRoles(List.of("DE"))
        .directApplyRoles(List.of("DE"))
        .build();
    clusterProps.setApproval(clusterApproval);

    var customCluster = KafkaCluster.builder()
        .name("custom-cluster")
        .originalProperties(clusterProps)
        .build();
    when(clustersStorage.getClusterByName("custom-cluster")).thenReturn(java.util.Optional.of(customCluster));

    // Global settings have requireTopicCreation=false for testing contrast
    approvalProperties.setRequireTopicCreation(false);

    assertThat(approvalService.getApprovalProperties("custom-cluster")).isSameAs(clusterApproval);
    assertThat(approvalService.getApprovalProperties("non-existent-cluster")).isSameAs(approvalProperties);

    AuthenticatedUser deUser = new AuthenticatedUser("de@company.com", List.of("DE"));
    AuthenticatedUser devUser = new AuthenticatedUser("dev@company.com", List.of("Developer"));

    // For custom-cluster, DE has direct apply and approver permissions
    assertThat(approvalService.isApprover("custom-cluster", deUser)).isTrue();
    assertThat(approvalService.isDirectApply("custom-cluster", deUser)).isTrue();
    assertThat(approvalService.isApprovalRequired(ApprovalTargetType.TOPIC_CREATE, "custom-cluster", deUser)).isFalse();

    // Developer requires approval on custom-cluster
    assertThat(approvalService.isApprover("custom-cluster", devUser)).isFalse();
    assertThat(approvalService.isDirectApply("custom-cluster", devUser)).isFalse();
    assertThat(approvalService.isApprovalRequired(ApprovalTargetType.TOPIC_CREATE, "custom-cluster", devUser)).isTrue();
    assertThat(approvalService.isApprovalRequired(
        ApprovalTargetType.CONSUMER_GROUP_DELETE, "custom-cluster", devUser)).isTrue();
    assertThat(approvalService.isApprovalRequired(
        ApprovalTargetType.CONNECTOR_RESET_OFFSETS, "custom-cluster", devUser)).isTrue();

    // Verify topic check respects cluster-specific topic
    assertThat(approvalService.isApprovalTopic("custom-cluster", "__cluster-approval-topic")).isTrue();
  }

  @Test
  void createTopicIfNeededUsesClusterSpecificPartitionsAndConfigs() {
    var clusterProps = new ClustersProperties.Cluster();
    var clusterApproval = ApprovalProperties.builder()
        .enabled(true)
        .topic("__custom-approval-topic")
        .partitions(3)
        .topicProperties(Map.of("cleanup.policy", "compact", "min.insync.replicas", "2"))
        .build();
    clusterProps.setApproval(clusterApproval);

    var customCluster = KafkaCluster.builder()
        .name("custom-cluster")
        .originalProperties(clusterProps)
        .build();
    when(clustersStorage.getClusterByName("custom-cluster")).thenReturn(java.util.Optional.of(customCluster));

    ReactiveAdminClient ac = mock(ReactiveAdminClient.class);
    when(ac.listTopics(true)).thenReturn(Mono.just(Set.of()));
    when(ac.createTopic(eq("__custom-approval-topic"), eq(3), any(), any())).thenReturn(Mono.empty());

    boolean created = approvalService.createTopicIfNeeded(
        customCluster, () -> ac, "__custom-approval-topic", clusterApproval);
    assertThat(created).isTrue();
    verify(ac).createTopic(
        eq("__custom-approval-topic"),
        eq(3),
        any(),
        eq(Map.of("cleanup.policy", "compact", "min.insync.replicas", "2"))
    );
  }
}
