package io.kafbat.ui.model.approval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.kafbat.ui.config.ApprovalProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApprovalSettingsDto {
  private boolean enabled;
  private String topic;
  private int partitions;
  private boolean requireTopicCreation;
  private boolean requireTopicConfigUpdates;
  private boolean requireConnectorDeployments;
  private List<String> directApplyRoles;
  private List<String> approverRoles;

  public static ApprovalSettingsDto fromProperties(ApprovalProperties props) {
    return ApprovalSettingsDto.builder()
        .enabled(props.isEnabled())
        .topic(props.getTopic())
        .partitions(props.getPartitions())
        .requireTopicCreation(props.isRequireTopicCreation())
        .requireTopicConfigUpdates(props.isRequireTopicConfigUpdates())
        .requireConnectorDeployments(props.isRequireConnectorDeployments())
        .directApplyRoles(props.getDirectApplyRoles())
        .approverRoles(props.getApproverRoles())
        .build();
  }
}
