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

  private static final String CAUSED_BY_PREFIX = "Caused by: ";

  @Nullable
  public String getRootCause() {
    if (errorTrace == null || errorTrace.isBlank()) {
      return null;
    }
    int lastCauseIdx = errorTrace.lastIndexOf(CAUSED_BY_PREFIX);
    if (lastCauseIdx != -1) {
      int start = lastCauseIdx + CAUSED_BY_PREFIX.length();
      int end = errorTrace.indexOf('\n', start);
      return (end != -1 ? errorTrace.substring(start, end) : errorTrace.substring(start)).trim();
    }
    int firstNewline = errorTrace.indexOf('\n');
    return (firstNewline != -1 ? errorTrace.substring(0, firstNewline) : errorTrace).trim();
  }

  public String getTruncatedTrace(int maxLength) {
    if (errorTrace == null || errorTrace.isBlank()) {
      return "No error trace available";
    }
    if (errorTrace.length() <= maxLength) {
      return errorTrace;
    }

    int lastCauseIdx = errorTrace.lastIndexOf(CAUSED_BY_PREFIX);
    if (lastCauseIdx != -1) {
      String tail = errorTrace.substring(lastCauseIdx);
      int maxTailLength = (int) (maxLength * 0.65);
      if (tail.length() > maxTailLength) {
        int tailEnd = tail.lastIndexOf('\n', maxTailLength);
        tail = (tailEnd != -1 ? tail.substring(0, tailEnd) : tail.substring(0, maxTailLength))
            + "\n... [remaining stack frames omitted]";
      }

      String omissionMarker = "\n... [intermediate stack frames omitted] ...\n";
      int remainingHeadBudget = maxLength - tail.length() - omissionMarker.length();
      if (remainingHeadBudget > 100) {
        int headEnd = errorTrace.lastIndexOf('\n', remainingHeadBudget);
        String head = (headEnd != -1)
            ? errorTrace.substring(0, headEnd)
            : errorTrace.substring(0, remainingHeadBudget);
        return head + omissionMarker + tail;
      }
      return tail;
    }

    int end = errorTrace.lastIndexOf('\n', maxLength);
    return (end != -1 ? errorTrace.substring(0, end) : errorTrace.substring(0, maxLength))
        + "\n... [truncated]";
  }
}
