package io.kafbat.ui.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("kafka.approval")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApprovalProperties {

  public static final String DEFAULT_APPROVAL_TOPIC = "__kui-approval-requests";

  @Builder.Default
  private boolean enabled = true;
  @Builder.Default
  private String topic = DEFAULT_APPROVAL_TOPIC;
  @Builder.Default
  private int partitions = 1;
  @Builder.Default
  private Map<String, String> topicProperties = new HashMap<>(Map.of("cleanup.policy", "compact"));
  @Builder.Default
  private boolean requireTopicCreation = false;
  @Builder.Default
  private boolean requireTopicConfigUpdates = false;
  @Builder.Default
  private boolean requireConnectorDeployments = false;
  @Builder.Default
  private boolean requireBrokerConfigUpdates = false;
  @Builder.Default
  private boolean requireConsumerGroupDelete = false;
  @Builder.Default
  private boolean requireConnectorResetOffsets = false;

  @Builder.Default
  private List<String> directApplyRoles = new ArrayList<>(List.of("Architect", "Admin"));
  @Builder.Default
  private List<String> approverRoles = new ArrayList<>(List.of("Architect", "Admin"));
}
