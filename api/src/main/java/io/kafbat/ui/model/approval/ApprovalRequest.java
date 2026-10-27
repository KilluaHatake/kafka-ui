package io.kafbat.ui.model.approval;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApprovalRequest {

  private static final JsonMapper MAPPER = new JsonMapper();

  private String id;
  private String clusterName;
  private ApprovalTargetType type;
  private String targetName;
  @Nullable
  private String connectName;
  private String requesterEmail;
  private List<String> requesterRoles;
  @Nullable
  private String approverEmail;
  private ApprovalStatus status;
  @Nullable
  private String rejectionReason;
  @Nullable
  private String executionError;
  private String createdAt;
  private String updatedAt;
  @Nullable
  private Map<String, Object> oldConfig;
  private Map<String, Object> newConfig;
  @Nullable
  private Map<String, Object> metadata;

  @SneakyThrows
  public String toJson() {
    return MAPPER.writeValueAsString(this);
  }

  @SneakyThrows
  public static ApprovalRequest fromJson(String json) {
    return MAPPER.readValue(json, ApprovalRequest.class);
  }
}
