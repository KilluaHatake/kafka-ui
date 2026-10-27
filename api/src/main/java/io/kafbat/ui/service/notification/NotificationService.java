package io.kafbat.ui.service.notification;

import io.kafbat.ui.config.ApprovalProperties;
import io.kafbat.ui.config.ConnectAlertProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

  private final ConnectAlertProperties alertProperties;
  private final SlackNotificationClient slackClient;
  private final EmailNotificationClient emailClient;

  public Mono<Void> sendConnectAlert(ConnectAlertPayload payload) {
    Mono<Void> slackMono = slackClient.sendAlert(alertProperties.getSlack(), payload);
    Mono<Void> emailMono = emailClient.sendAlert(alertProperties.getEmail(), payload);

    return Mono.when(slackMono, emailMono);
  }

  public Mono<Void> sendApprovalAlert(
      ApprovalAlertPayload payload,
      ApprovalProperties approvalProps,
      List<String> approverEmails
  ) {
    if (!approvalProps.isAlertNotificationsEnabled()) {
      return Mono.empty();
    }

    Mono<Void> slackMono = Mono.empty();
    if (approvalProps.isSendSlackAlerts()) {
      var slackConfig = new ConnectAlertProperties.SlackProperties();
      slackConfig.setEnabled(true);
      String webhookUrl = StringUtils.hasText(approvalProps.getSlackWebhookUrl())
          ? approvalProps.getSlackWebhookUrl()
          : alertProperties.getSlack().getWebhookUrl();
      String channel = StringUtils.hasText(approvalProps.getSlackChannel())
          ? approvalProps.getSlackChannel()
          : alertProperties.getSlack().getChannel();
      slackConfig.setWebhookUrl(webhookUrl);
      slackConfig.setChannel(channel);

      slackMono = slackClient.sendApprovalAlert(slackConfig, payload);
    }

    Mono<Void> emailMono = Mono.empty();
    if (approvalProps.isSendEmailAlerts()) {
      var emailConfig = alertProperties.getEmail();
      if (emailConfig.isEnabled() && StringUtils.hasText(emailConfig.getSmtpHost())) {
        List<String> allRecipients = new ArrayList<>();
        if (approverEmails != null) {
          allRecipients.addAll(approverEmails);
        }
        if (emailConfig.getRecipients() != null) {
          for (String r : emailConfig.getRecipients()) {
            if (!allRecipients.contains(r)) {
              allRecipients.add(r);
            }
          }
        }
        if (!allRecipients.isEmpty()) {
          emailMono = emailClient.sendApprovalAlert(emailConfig, payload, allRecipients);
        }
      }
    }

    return Mono.when(slackMono, emailMono);
  }
}
