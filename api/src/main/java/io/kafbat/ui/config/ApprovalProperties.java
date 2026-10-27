package io.kafbat.ui.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("kafka.approval")
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApprovalProperties {

  public static final String DEFAULT_APPROVAL_TOPIC = "__kui-approval-requests";

  private boolean enabled = true;
  private String topic = DEFAULT_APPROVAL_TOPIC;
  private int partitions = 1;
  private Map<String, String> topicProperties;
  private boolean requireTopicCreation = false;
  private boolean requireTopicConfigUpdates = false;
  private boolean requireConnectorDeployments = false;
  private boolean requireBrokerConfigUpdates = false;
  private boolean requireAlertSettingsUpdates = false;
  private boolean requireConsumerGroupDelete = false;
  private boolean requireConnectorResetOffsets = false;

  private List<String> directApplyRoles = new ArrayList<>(List.of("Architect", "Admin"));
  private List<String> approverRoles = new ArrayList<>(List.of("Architect", "Admin"));
}
