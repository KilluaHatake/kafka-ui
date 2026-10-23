package io.kafbat.ui.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("kafka.approval")
@Data
public class ApprovalProperties {

  public static final String DEFAULT_APPROVAL_TOPIC = "__kui-approval-requests";

  private String topic = DEFAULT_APPROVAL_TOPIC;
  private int partitions = 1;
  private boolean requireTopicCreation = false;
  private boolean requireTopicConfigUpdates = false;
  private boolean requireConnectorDeployments = false;

  private List<String> directApplyRoles = new ArrayList<>(List.of("Architect", "Admin"));
  private List<String> approverRoles = new ArrayList<>(List.of("Architect", "Admin"));
}
