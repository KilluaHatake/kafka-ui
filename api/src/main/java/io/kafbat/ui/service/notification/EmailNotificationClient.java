package io.kafbat.ui.service.notification;

import io.kafbat.ui.config.ConnectAlertProperties;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@Slf4j
public class EmailNotificationClient {

  private static final int MAX_TRACE_LENGTH = 1500;

  public Mono<Void> sendAlert(ConnectAlertProperties.EmailProperties config, ConnectAlertPayload payload) {
    if (!config.isEnabled() || !StringUtils.hasText(config.getSmtpHost()) || config.getRecipients().isEmpty()) {
      log.debug("Email alerts disabled, SMTP host not configured, or recipients empty");
      return Mono.empty();
    }

    return Mono.fromRunnable(() -> {
      try {
        JavaMailSenderImpl mailSender = createMailSender(config);
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

        helper.setFrom(config.getFrom());
        helper.setTo(config.getRecipients().toArray(new String[0]));
        helper.setSubject("[Kafka Connect Alert] Failure: %s [%s] in cluster %s"
            .formatted(payload.connectorName(), payload.component(), payload.clusterName()));
        
        String plainText = buildEmailBody(payload);
        String htmlBody = buildHtmlEmailBody(payload);
        helper.setText(plainText, htmlBody);

        mailSender.send(mimeMessage);
        log.info("Email alert sent to {} for connector '{}' [{}] on cluster '{}'",
            config.getRecipients(), payload.connectorName(), payload.component(), payload.clusterName());
      } catch (Exception e) {
        log.error("Failed to send email alert for connector '{}' on cluster '{}'",
            payload.connectorName(), payload.clusterName(), e);
      }
    })
    .subscribeOn(Schedulers.boundedElastic())
    .then()
    .onErrorResume(th -> Mono.empty());
  }

  private JavaMailSenderImpl createMailSender(ConnectAlertProperties.EmailProperties config) {
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(config.getSmtpHost());
    sender.setPort(config.getSmtpPort());

    if (StringUtils.hasText(config.getUsername())) {
      sender.setUsername(config.getUsername());
      sender.setPassword(config.getPassword());
    }

    Properties props = sender.getJavaMailProperties();
    props.put("mail.transport.protocol", "smtp");
    props.put("mail.smtp.auth", String.valueOf(StringUtils.hasText(config.getUsername())));
    props.put("mail.smtp.starttls.enable", String.valueOf(config.isStarttlsEnabled()));
    props.put("mail.smtp.ssl.enable", String.valueOf(config.isSslEnabled()));
    props.put("mail.smtp.connectiontimeout", "5000");
    props.put("mail.smtp.timeout", "5000");

    return sender;
  }

  private String buildEmailBody(ConnectAlertPayload payload) {
    return """
        Kafka Connect Failure Notification
        ==================================

        Cluster:           %s
        Connect Cluster:   %s
        Connector:         %s
        Component:         %s
        Timestamp:         %s

        Error Trace:
        ----------------------------------
        %s
        """.formatted(
        payload.clusterName(),
        payload.connectName(),
        payload.connectorName(),
        payload.component(),
        payload.timestamp().toString(),
        payload.getTruncatedTrace(MAX_TRACE_LENGTH)
    );
  }

  private String buildHtmlEmailBody(ConnectAlertPayload payload) {
    String escapedTrace = HtmlUtils.htmlEscape(payload.getTruncatedTrace(MAX_TRACE_LENGTH));
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="UTF-8">
          <style>
            body { font-family: -apple-system, sans-serif; background: #f4f6f8; margin: 0; padding: 20px; }
            .container { max-width: 600px; margin: 0 auto; background: #fff; border-radius: 8px; }
            .header { background: #d9534f; color: #fff; padding: 16px; font-size: 18px; font-weight: bold; }
            .content { padding: 20px; }
            .meta-table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }
            .meta-table td { padding: 8px; border-bottom: 1px solid #f0f0f0; font-size: 14px; }
            .meta-table td.label { font-weight: 600; width: 140px; color: #555; background: #fafbfc; }
            .trace-header { font-weight: bold; font-size: 14px; margin-bottom: 8px; color: #333; }
            .trace-box { background: #24292e; color: #f6f8fa; padding: 12px; border-radius: 6px;
                         font-family: monospace; font-size: 12px; white-space: pre-wrap; word-break: break-all; }
            .footer { padding: 12px; background: #f8f9fa; border-top: 1px solid #e1e4e8;
                      font-size: 12px; color: #888; text-align: center; }
          </style>
        </head>
        <body>
          <div class="container">
            <div class="header">🚨 Kafka Connect Failure Alert</div>
            <div class="content">
              <table class="meta-table">
                <tr><td class="label">Cluster</td><td>%s</td></tr>
                <tr><td class="label">Connect Cluster</td><td>%s</td></tr>
                <tr><td class="label">Connector</td><td><strong>%s</strong></td></tr>
                <tr><td class="label">Component</td><td><span style="color:#d9534f">%s</span></td></tr>
                <tr><td class="label">Timestamp</td><td>%s</td></tr>
              </table>
              <div class="trace-header">Error Trace:</div>
              <div class="trace-box">%s</div>
            </div>
            <div class="footer">Sent by Kafbat UI Monitoring System</div>
          </div>
        </body>
        </html>
        """.formatted(
        HtmlUtils.htmlEscape(payload.clusterName()),
        HtmlUtils.htmlEscape(payload.connectName()),
        HtmlUtils.htmlEscape(payload.connectorName()),
        HtmlUtils.htmlEscape(payload.component()),
        payload.timestamp().toString(),
        escapedTrace
    );
  }
}
