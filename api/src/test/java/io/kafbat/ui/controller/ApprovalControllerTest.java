package io.kafbat.ui.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.kafbat.ui.config.ApprovalProperties;
import io.kafbat.ui.config.ClustersProperties;
import io.kafbat.ui.model.KafkaCluster;
import io.kafbat.ui.model.approval.ApprovalSettingsDto;
import io.kafbat.ui.service.ClustersStorage;
import io.kafbat.ui.service.approval.ApprovalService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import reactor.test.StepVerifier;

class ApprovalControllerTest {

  private ApprovalProperties approvalProperties;
  private ApprovalService approvalService;
  private ClustersStorage clustersStorage;
  private ApprovalController controller;

  @BeforeEach
  void setUp() {
    approvalProperties = new ApprovalProperties();
    approvalService = mock(ApprovalService.class);
    clustersStorage = mock(ClustersStorage.class);
    when(approvalService.canManageWorkflowSettings(any())).thenReturn(true);
    when(approvalService.canManageWorkflowSettings(any(), any())).thenReturn(true);
    when(approvalService.canAccessApprovals(any(), any())).thenReturn(true);
    controller = new ApprovalController(approvalService, approvalProperties);
    controller.setClustersStorage(clustersStorage);
  }

  @Test
  void testGetApprovalSettings() {
    StepVerifier.create(controller.getApprovalSettings())
        .assertNext(resp -> {
          assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
          ApprovalSettingsDto body = resp.getBody();
          assertThat(body).isNotNull();
          assertThat(body.isEnabled()).isTrue();
          assertThat(body.getTopic()).isEqualTo(ApprovalProperties.DEFAULT_APPROVAL_TOPIC);
        })
        .verifyComplete();
  }

  @Test
  void testUpdateApprovalSettings() {
    ApprovalSettingsDto newSettings = ApprovalSettingsDto.builder()
        .enabled(false)
        .requireTopicCreation(true)
        .requireTopicConfigUpdates(false)
        .requireConnectorDeployments(true)
        .requireBrokerConfigUpdates(true)
        .requireConsumerGroupDelete(true)
        .requireConnectorResetOffsets(true)
        .directApplyRoles(List.of("Admin"))
        .approverRoles(List.of("Admin", "Lead"))
        .build();

    StepVerifier.create(controller.updateApprovalSettings(newSettings))
        .assertNext(resp -> {
          assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
          ApprovalSettingsDto body = resp.getBody();
          assertThat(body).isNotNull();
          assertThat(body.isEnabled()).isFalse();
          assertThat(body.isRequireTopicCreation()).isTrue();
          assertThat(body.isRequireConnectorDeployments()).isTrue();
          assertThat(body.isRequireBrokerConfigUpdates()).isTrue();
          assertThat(body.isRequireConsumerGroupDelete()).isTrue();
          assertThat(body.isRequireConnectorResetOffsets()).isTrue();
          assertThat(body.getApproverRoles()).containsExactly("Admin", "Lead");
        })
        .verifyComplete();
  }

  @Test
  void testUnauthorizedCannotGetOrUpdateApprovalSettings() {
    when(approvalService.canManageWorkflowSettings(any())).thenReturn(false);

    StepVerifier.create(controller.getApprovalSettings())
        .expectError(AccessDeniedException.class)
        .verify();

    StepVerifier.create(controller.updateApprovalSettings(ApprovalSettingsDto.builder().enabled(false).build()))
        .expectError(AccessDeniedException.class)
        .verify();
  }

  @Test
  void testGetClusterApprovalSettings() {
    var clusterProps = new ClustersProperties.Cluster();
    var clusterApproval = ApprovalProperties.builder()
        .enabled(true)
        .topic("__custom-cluster-approvals")
        .partitions(3)
        .requireTopicCreation(true)
        .approverRoles(List.of("DE"))
        .directApplyRoles(List.of("DE"))
        .build();
    clusterProps.setApproval(clusterApproval);
    var cluster = KafkaCluster.builder()
        .name("dev-cluster")
        .originalProperties(clusterProps)
        .build();
    when(clustersStorage.getClusterByName("dev-cluster")).thenReturn(Optional.of(cluster));
    when(approvalService.getApprovalProperties("dev-cluster")).thenReturn(clusterApproval);

    StepVerifier.create(controller.getClusterApprovalSettings("dev-cluster"))
        .assertNext(resp -> {
          assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
          ApprovalSettingsDto body = resp.getBody();
          assertThat(body).isNotNull();
          assertThat(body.getTopic()).isEqualTo("__custom-cluster-approvals");
          assertThat(body.getPartitions()).isEqualTo(3);
          assertThat(body.isRequireTopicCreation()).isTrue();
          assertThat(body.getApproverRoles()).containsExactly("DE");
          assertThat(body.getDirectApplyRoles()).containsExactly("DE");
        })
        .verifyComplete();
  }

  @Test
  void testUpdateClusterApprovalSettings() {
    var clusterProps = new ClustersProperties.Cluster();
    var cluster = KafkaCluster.builder()
        .name("dev-cluster")
        .originalProperties(clusterProps)
        .build();
    when(clustersStorage.getClusterByName("dev-cluster")).thenReturn(Optional.of(cluster));

    ApprovalSettingsDto newSettings = ApprovalSettingsDto.builder()
        .enabled(true)
        .topic("__kui-dev-approvals")
        .partitions(2)
        .requireTopicCreation(true)
        .requireConsumerGroupDelete(true)
        .approverRoles(List.of("DE"))
        .directApplyRoles(List.of("DE"))
        .build();

    StepVerifier.create(controller.updateClusterApprovalSettings("dev-cluster", newSettings))
        .assertNext(resp -> {
          assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
          ApprovalSettingsDto body = resp.getBody();
          assertThat(body).isNotNull();
          assertThat(body.getTopic()).isEqualTo("__kui-dev-approvals");
          assertThat(body.getPartitions()).isEqualTo(2);
          assertThat(body.isRequireConsumerGroupDelete()).isTrue();
          assertThat(body.getApproverRoles()).containsExactly("DE");
          assertThat(clusterProps.getApproval()).isNotNull();
          assertThat(clusterProps.getApproval().getTopic()).isEqualTo("__kui-dev-approvals");
        })
        .verifyComplete();
  }

  @Test
  void testRbacEnabledEmptyUserFailsClosed() {
    io.kafbat.ui.service.rbac.AccessControlService acs = mock(io.kafbat.ui.service.rbac.AccessControlService.class);
    when(acs.isRbacEnabled()).thenReturn(true);
    controller.setAccessControlService(acs);

    StepVerifier.create(controller.getApprovalSettings())
        .expectError(AccessDeniedException.class)
        .verify();

    StepVerifier.create(controller.getPermissions("local-cluster"))
        .assertNext(resp -> {
          assertThat(resp.getBody()).isNotNull();
          assertThat(resp.getBody().get("canApprove")).isEqualTo(false);
          assertThat(resp.getBody().get("canViewApprovals")).isEqualTo(false);
          assertThat(resp.getBody().get("canManageWorkflowSettings")).isEqualTo(false);
        })
        .verifyComplete();
  }
}
