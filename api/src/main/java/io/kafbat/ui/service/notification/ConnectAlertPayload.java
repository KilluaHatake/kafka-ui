package io.kafbat.ui.service.notification;

import java.time.Instant;
import javax.annotation.Nullable;

public record ConnectAlertPayload(
    String clusterName,
    String connectName,
    String connectorName,
    String component,
    Instant timestamp,
    @Nullable String errorTrace
) {

  public String getTruncatedTrace(int maxLength) {
    if (errorTrace == null || errorTrace.isBlank()) {
      return "No error trace available";
    }
    if (errorTrace.length() <= maxLength) {
      return errorTrace;
    }
    return errorTrace.substring(0, maxLength) + "\n... [truncated]";
  }
}
