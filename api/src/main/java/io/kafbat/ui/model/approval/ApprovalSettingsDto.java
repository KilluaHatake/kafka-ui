package io.kafbat.ui.model.approval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.kafbat.ui.config.ApprovalProperties;
import java.util.List;
import javax.annotation.Nullable;
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
  private boolean requireBrokerConfigUpdates;
  private boolean requireAlertSettingsUpdates;
  private boolean requireConsumerGroupDelete;
  private boolean requireConnectorResetOffsets;
  private boolean alertNotificationsEnabled;
  private boolean sendSlackAlerts;
  private boolean sendEmailAlerts;
  @Nullable
  private String slackWebhookUrl;
  @Nullable
  private String slackChannel;
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
        .requireBrokerConfigUpdates(props.isRequireBrokerConfigUpdates())
        .requireAlertSettingsUpdates(props.isRequireAlertSettingsUpdates())
        .requireConsumerGroupDelete(props.isRequireConsumerGroupDelete())
        .requireConnectorResetOffsets(props.isRequireConnectorResetOffsets())
        .alertNotificationsEnabled(props.isAlertNotificationsEnabled())
        .sendSlackAlerts(props.isSendSlackAlerts())
        .sendEmailAlerts(props.isSendEmailAlerts())
        .slackWebhookUrl(props.getSlackWebhookUrl())
        .slackChannel(props.getSlackChannel())
        .directApplyRoles(props.getDirectApplyRoles())
        .approverRoles(props.getApproverRoles())
        .build();
  }
}
