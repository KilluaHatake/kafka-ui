package io.kafbat.ui.service.notification;

import io.kafbat.ui.model.approval.ApprovalRequest;
import io.kafbat.ui.model.approval.ApprovalTargetType;
import java.time.Instant;
import java.util.List;
import javax.annotation.Nullable;

public record ApprovalAlertPayload(
    String id,
    String clusterName,
    ApprovalTargetType type,
    String targetName,
    @Nullable String connectName,
    String requesterEmail,
    List<String> requesterRoles,
    List<String> approverRoles,
    Instant timestamp
) {

  public static ApprovalAlertPayload fromRequest(
      ApprovalRequest request,
      List<String> approverRoles
  ) {
    return new ApprovalAlertPayload(
        request.getId(),
        request.getClusterName(),
        request.getType(),
        request.getTargetName(),
        request.getConnectName(),
        request.getRequesterEmail(),
        request.getRequesterRoles() != null ? request.getRequesterRoles() : List.of(),
        approverRoles != null ? approverRoles : List.of(),
        Instant.now()
    );
  }

  public String getSubjectSummary() {
    if (targetName != null && !targetName.isBlank()) {
      return targetName + " (" + type + ")";
    }
    return type != null ? type.name() : "Approval Request";
  }
}
