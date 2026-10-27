package io.kafbat.ui.service.notification;

import static org.assertj.core.api.Assertions.assertThat;

import io.kafbat.ui.model.approval.ApprovalRequest;
import io.kafbat.ui.model.approval.ApprovalTargetType;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApprovalAlertPayloadTest {

  @Test
  void createsPayloadAndGeneratesSubjectSummary() {
    ApprovalRequest request = ApprovalRequest.builder()
        .id("test-id")
        .clusterName("DEV_UAT_AUTOMQ")
        .type(ApprovalTargetType.TOPIC_CREATE)
        .targetName("sasrang")
        .requesterEmail("nmeshram@webuy.com")
        .requesterRoles(List.of("DBA"))
        .build();

    ApprovalAlertPayload payload = ApprovalAlertPayload.fromRequest(request, List.of("DE", "Admin"));

    assertThat(payload.id()).isEqualTo("test-id");
    assertThat(payload.clusterName()).isEqualTo("DEV_UAT_AUTOMQ");
    assertThat(payload.type()).isEqualTo(ApprovalTargetType.TOPIC_CREATE);
    assertThat(payload.targetName()).isEqualTo("sasrang");
    assertThat(payload.requesterEmail()).isEqualTo("nmeshram@webuy.com");
    assertThat(payload.requesterRoles()).containsExactly("DBA");
    assertThat(payload.approverRoles()).containsExactly("DE", "Admin");
    assertThat(payload.getSubjectSummary()).isEqualTo("sasrang (TOPIC_CREATE)");
  }

  @Test
  void generatesSubjectSummaryWithoutTargetName() {
    ApprovalRequest request = ApprovalRequest.builder()
        .id("test-id-2")
        .clusterName("DEV_UAT_AUTOMQ")
        .type(ApprovalTargetType.ALERT_SETTINGS_UPDATE)
        .targetName(null)
        .requesterEmail("dba@example.com")
        .build();

    ApprovalAlertPayload payload = ApprovalAlertPayload.fromRequest(request, List.of("Admin"));

    assertThat(payload.getSubjectSummary()).isEqualTo("ALERT_SETTINGS_UPDATE");
  }
}
