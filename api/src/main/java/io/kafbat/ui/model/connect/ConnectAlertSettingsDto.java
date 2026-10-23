package io.kafbat.ui.model.connect;

import io.kafbat.ui.config.ConnectAlertProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectAlertSettingsDto {

  private boolean enabled;
  private long pollingIntervalMs;
  private int throttleIntervalMinutes;
  private SlackDto slack;
  private EmailDto email;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SlackDto {
    private boolean enabled;
    private String webhookUrl;
    private String channel;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class EmailDto {
    private boolean enabled;
    private String smtpHost;
    private int smtpPort;
    private String username;
    private String password;
    private boolean starttlsEnabled;
    private boolean sslEnabled;
    private String from;
    private List<String> recipients;
  }

  public static ConnectAlertSettingsDto fromProperties(ConnectAlertProperties props) {
    if (props == null) {
      return new ConnectAlertSettingsDto();
    }

    SlackDto slackDto = SlackDto.builder()
        .enabled(props.getSlack().isEnabled())
        .webhookUrl(props.getSlack().getWebhookUrl())
        .channel(props.getSlack().getChannel())
        .build();

    EmailDto emailDto = EmailDto.builder()
        .enabled(props.getEmail().isEnabled())
        .smtpHost(props.getEmail().getSmtpHost())
        .smtpPort(props.getEmail().getSmtpPort())
        .username(props.getEmail().getUsername())
        .password(props.getEmail().getPassword() != null && !props.getEmail().getPassword().isBlank()
            ? "********" : "")
        .starttlsEnabled(props.getEmail().isStarttlsEnabled())
        .sslEnabled(props.getEmail().isSslEnabled())
        .from(props.getEmail().getFrom())
        .recipients(new ArrayList<>(props.getEmail().getRecipients()))
        .build();

    return ConnectAlertSettingsDto.builder()
        .enabled(props.isEnabled())
        .pollingIntervalMs(props.getPollingIntervalMs())
        .throttleIntervalMinutes(props.getThrottleIntervalMinutes())
        .slack(slackDto)
        .email(emailDto)
        .build();
  }
}
