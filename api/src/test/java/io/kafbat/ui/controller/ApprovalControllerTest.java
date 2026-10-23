package io.kafbat.ui.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.kafbat.ui.config.ApprovalProperties;
import io.kafbat.ui.model.approval.ApprovalSettingsDto;
import io.kafbat.ui.service.approval.ApprovalService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ApprovalControllerTest {

  private ApprovalProperties approvalProperties;
  private ApprovalService approvalService;
  private ApprovalController controller;

  @BeforeEach
  void setUp() {
    approvalProperties = new ApprovalProperties();
    approvalService = mock(ApprovalService.class);
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
          assertThat(body.getApproverRoles()).containsExactly("Admin", "Lead");
        })
        .verifyComplete();
  }
}
