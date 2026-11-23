package io.kafbat.ui.model.approval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.kafbat.ui.config.ApprovalProperties;
import java.util.List;
import java.util.Map;
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
  private Map<String, String> topicProperties;
  private boolean requireTopicCreation;
  private boolean requireTopicConfigUpdates;
  private boolean requireConnectorDeployments;
  private boolean requireBrokerConfigUpdates;
  private boolean requireConsumerGroupDelete;
  private boolean requireConnectorResetOffsets;
  private List<String> directApplyRoles;
  private List<String> approverRoles;

  public static ApprovalSettingsDto fromProperties(ApprovalProperties props) {
    if (props == null) {
      return null;
    }
    return ApprovalSettingsDto.builder()
        .enabled(props.isEnabled())
        .topic(props.getTopic())
        .partitions(props.getPartitions())
        .topicProperties(props.getTopicProperties())
        .requireTopicCreation(props.isRequireTopicCreation())
        .requireTopicConfigUpdates(props.isRequireTopicConfigUpdates())
        .requireConnectorDeployments(props.isRequireConnectorDeployments())
        .requireBrokerConfigUpdates(props.isRequireBrokerConfigUpdates())
        .requireConsumerGroupDelete(props.isRequireConsumerGroupDelete())
        .requireConnectorResetOffsets(props.isRequireConnectorResetOffsets())
        .directApplyRoles(props.getDirectApplyRoles())
        .approverRoles(props.getApproverRoles())
        .build();
  }

  public void applyTo(ApprovalProperties props) {
    if (props == null) {
      return;
    }
    props.setEnabled(this.isEnabled());
    if (this.getTopic() != null) {
      props.setTopic(this.getTopic());
    }
    if (this.getPartitions() > 0) {
      props.setPartitions(this.getPartitions());
    }
    if (this.getTopicProperties() != null) {
      props.setTopicProperties(this.getTopicProperties());
    }
    props.setRequireTopicCreation(this.isRequireTopicCreation());
    props.setRequireTopicConfigUpdates(this.isRequireTopicConfigUpdates());
    props.setRequireConnectorDeployments(this.isRequireConnectorDeployments());
    props.setRequireBrokerConfigUpdates(this.isRequireBrokerConfigUpdates());
    props.setRequireConsumerGroupDelete(this.isRequireConsumerGroupDelete());
    props.setRequireConnectorResetOffsets(this.isRequireConnectorResetOffsets());
    if (this.getDirectApplyRoles() != null) {
      props.setDirectApplyRoles(this.getDirectApplyRoles());
    }
    if (this.getApproverRoles() != null) {
      props.setApproverRoles(this.getApproverRoles());
    }
  }
}
