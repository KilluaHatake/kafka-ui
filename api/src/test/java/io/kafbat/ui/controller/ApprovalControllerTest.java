package io.kafbat.ui.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.kafbat.ui.config.ApprovalProperties;
import io.kafbat.ui.model.approval.ApprovalSettingsDto;
import io.kafbat.ui.service.approval.ApprovalService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import reactor.test.StepVerifier;

class ApprovalControllerTest {

  private ApprovalProperties approvalProperties;
  private ApprovalService approvalService;
  private ApprovalController controller;

  @BeforeEach
  void setUp() {
    approvalProperties = new ApprovalProperties();
    approvalService = mock(ApprovalService.class);
    when(approvalService.canManageWorkflowSettings(any())).thenReturn(true);
    when(approvalService.canAccessApprovals(any(), any())).thenReturn(true);
    controller = new ApprovalController(approvalService, approvalProperties);
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
        .requireAlertSettingsUpdates(true)
        .requireConsumerGroupDelete(true)
        .requireConnectorResetOffsets(true)
        .alertNotificationsEnabled(true)
        .sendSlackAlerts(true)
        .sendEmailAlerts(false)
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
          assertThat(body.isRequireAlertSettingsUpdates()).isTrue();
          assertThat(body.isRequireConsumerGroupDelete()).isTrue();
          assertThat(body.isAlertNotificationsEnabled()).isTrue();
          assertThat(body.isSendSlackAlerts()).isTrue();
          assertThat(body.isSendEmailAlerts()).isFalse();
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
}
