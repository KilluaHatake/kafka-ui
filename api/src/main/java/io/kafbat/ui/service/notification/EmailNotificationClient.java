package io.kafbat.ui.service.notification;

import io.kafbat.ui.config.ConnectAlertProperties;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
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
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(config.getFrom());
        message.setTo(config.getRecipients().toArray(new String[0]));
        message.setSubject("[Kafka Connect Alert] Failure: %s [%s] in cluster %s"
            .formatted(payload.connectorName(), payload.component(), payload.clusterName()));
        message.setText(buildEmailBody(payload));

        mailSender.send(message);
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
}
