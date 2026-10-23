package io.kafbat.ui.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("kafka.connect-alerts")
@Data
public class ConnectAlertProperties {

  private boolean enabled = false;
  private long pollingIntervalMs = 30000;
  private int throttleIntervalMinutes = 15;

  private SlackProperties slack = new SlackProperties();
  private EmailProperties email = new EmailProperties();

  @Data
  public static class SlackProperties {
    private boolean enabled = false;
    private String webhookUrl;
    private String channel;
  }

  @Data
  @ToString(exclude = "password")
  public static class EmailProperties {
    private boolean enabled = false;
    private String smtpHost;
    private int smtpPort = 587;
    private String username;
    private String password;
    private boolean starttlsEnabled = true;
    private boolean sslEnabled = false;
    private String from = "kafbat-alerts@localhost";
    private List<String> recipients = new ArrayList<>();
  }
}
