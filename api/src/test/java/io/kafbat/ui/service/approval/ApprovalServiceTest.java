package io.kafbat.ui.service.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.kafbat.ui.config.ApprovalProperties;
import io.kafbat.ui.config.auth.AuthenticatedUser;
import io.kafbat.ui.model.KafkaCluster;
import io.kafbat.ui.model.TopicCreationDTO;
import io.kafbat.ui.model.approval.ApprovalRequest;
import io.kafbat.ui.model.approval.ApprovalStatus;
import io.kafbat.ui.model.approval.ApprovalTargetType;
import io.kafbat.ui.service.AdminClientService;
import io.kafbat.ui.service.ClustersStorage;
import io.kafbat.ui.service.KafkaConnectService;
import io.kafbat.ui.service.TopicsService;
import io.kafbat.ui.service.audit.AuditService;
import io.kafbat.ui.service.notification.NotificationService;
import io.kafbat.ui.service.rbac.AccessControlService;
import io.kafbat.ui.model.rbac.Permission;
import io.kafbat.ui.model.rbac.Resource;
import java.util.List;
import java.util.Map;
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
  private NotificationService notificationService;
  private io.kafbat.ui.service.BrokerService brokerService;
  private io.kafbat.ui.service.ConsumerGroupService consumerGroupService;
  private io.kafbat.ui.config.ConnectAlertProperties alertProperties;
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
    approvalProperties.setRequireAlertSettingsUpdates(true);
    approvalProperties.setRequireConsumerGroupDelete(true);
    approvalProperties.setRequireConnectorResetOffsets(true);
    approvalProperties.setApproverRoles(List.of("Architect", "Admin"));
    approvalProperties.setDirectApplyRoles(List.of("Architect", "Admin"));

    clustersStorage = mock(ClustersStorage.class);
    adminClientService = mock(AdminClientService.class);
    auditService = mock(AuditService.class);
    topicsService = mock(TopicsService.class);
    kafkaConnectService = mock(KafkaConnectService.class);
    notificationService = mock(NotificationService.class);
    brokerService = mock(io.kafbat.ui.service.BrokerService.class);
    consumerGroupService = mock(io.kafbat.ui.service.ConsumerGroupService.class);
    alertProperties = new io.kafbat.ui.config.ConnectAlertProperties();
    accessControlService = mock(AccessControlService.class);
    when(accessControlService.isRbacEnabled()).thenReturn(true);

    cluster = KafkaCluster.builder().name("local-cluster").build();

    approvalService = new ApprovalService(
        approvalProperties,
        clustersStorage,
        adminClientService,
        auditService,
        topicsService,
        kafkaConnectService,
        notificationService,
        brokerService,
        consumerGroupService,
        alertProperties,
        accessControlService
    );
  }

  @Test
  void isApprovalRequiredReturnsTrueForDeveloperAndFalseForArchitect() {
    AuthenticatedUser devUser = new AuthenticatedUser("dev@company.com", List.of("Developer"));
    AuthenticatedUser architectUser = new AuthenticatedUser("arch@company.com", List.of("Architect"));

    assertThat(approvalService.isApprovalRequired(ApprovalTargetType.TOPIC_CREATE, devUser)).isTrue();
    assertThat(approvalService.isApprovalRequired(ApprovalTargetType.TOPIC_CREATE, architectUser)).isFalse();
    assertThat(approvalService.isApprovalRequired(ApprovalTargetType.CONNECTOR_CREATE, devUser)).isTrue();
    assertThat(approvalService.isApprovalRequired(ApprovalTargetType.CONNECTOR_CREATE, architectUser)).isFalse();
    assertThat(approvalService.isApprovalRequired(ApprovalTargetType.BROKER_CONFIG_UPDATE, devUser)).isTrue();
    assertThat(approvalService.isApprovalRequired(ApprovalTargetType.BROKER_CONFIG_UPDATE, architectUser)).isFalse();
    assertThat(approvalService.isApprovalRequired(ApprovalTargetType.ALERT_SETTINGS_UPDATE, devUser)).isTrue();
    assertThat(approvalService.isApprovalRequired(ApprovalTargetType.ALERT_SETTINGS_UPDATE, architectUser)).isFalse();
    assertThat(approvalService.isApprovalRequired(ApprovalTargetType.CONSUMER_GROUP_DELETE, devUser)).isTrue();
    assertThat(approvalService.isApprovalRequired(ApprovalTargetType.CONSUMER_GROUP_DELETE, architectUser)).isFalse();
    assertThat(approvalService.isApprovalRequired(ApprovalTargetType.CONNECTOR_RESET_OFFSETS, devUser)).isTrue();
    assertThat(approvalService.isApprovalRequired(ApprovalTargetType.CONNECTOR_RESET_OFFSETS, architectUser)).isFalse();

    approvalProperties.setRequireBrokerConfigUpdates(false);
    assertThat(approvalService.isApprovalRequired(ApprovalTargetType.BROKER_CONFIG_UPDATE, devUser)).isFalse();
  }

  @Test
  void isApproverValidatesRoles() {
    AuthenticatedUser devUser = new AuthenticatedUser("dev@company.com", List.of("Developer"));
    AuthenticatedUser architectUser = new AuthenticatedUser("arch@company.com", List.of("Architect"));

    assertThat(approvalService.isApprover(devUser)).isFalse();
    assertThat(approvalService.isApprover(architectUser)).isTrue();
  }

  @Test
  void createRequestAndQueryLifecycle() {
    AuthenticatedUser devUser = new AuthenticatedUser("dev@company.com", List.of("Developer"));

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
    AuthenticatedUser devUser = new AuthenticatedUser("dev@company.com", List.of("Developer"));
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
    AuthenticatedUser devUser = new AuthenticatedUser("dev@company.com", List.of("Developer"));
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
    AuthenticatedUser devUser = new AuthenticatedUser("dev@company.com", List.of("Developer"));

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

    AuthenticatedUser supportUser = new AuthenticatedUser("support@company.com", List.of("Support"));
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
    assertThat(approvalService.canAccessAlertSettings(supportUser, "local-cluster")).isFalse();

    AuthenticatedUser devUser = new AuthenticatedUser("dev@company.com", List.of("Developer"));
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
    assertThat(approvalService.canAccessAlertSettings(devUser, "local-cluster")).isTrue();

    AuthenticatedUser archUser = new AuthenticatedUser("arch@company.com", List.of("Architect"));
    assertThat(approvalService.isApprover(archUser)).isTrue();
    assertThat(approvalService.canManageWorkflowSettings(archUser)).isTrue();
    assertThat(approvalService.canAccessApprovals("local-cluster", archUser)).isTrue();
    assertThat(approvalService.canAccessAlertSettings(archUser, "local-cluster")).isTrue();
  }
}
