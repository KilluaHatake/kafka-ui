package io.kafbat.ui.service.notification;

import io.kafbat.ui.config.ConnectAlertProperties;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class EmailNotificationClient {

  private static final int MAX_TRACE_LENGTH = 10000;

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
        String rootCause = payload.getRootCause();
        String subjectSuffix = StringUtils.hasText(rootCause) ? " - " + rootCause : "";
        helper.setSubject(("[Kafka Connect Alert] Failure: %s [%s] in cluster %s%s")
            .formatted(payload.connectorName(), payload.component(), payload.clusterName(), subjectSuffix));

        String plainText = buildEmailBody(payload);
        String htmlText = buildHtmlEmailBody(payload);
        helper.setText(plainText, htmlText);

        mailSender.send(mimeMessage);
        log.info("Email alert sent for connector '{}' [{}] on cluster '{}' to {}",
            payload.connectorName(), payload.component(), payload.clusterName(), config.getRecipients());
      } catch (Exception e) {
        log.error("Failed to send email alert for connector '{}' on cluster '{}'",
            payload.connectorName(), payload.clusterName(), e);
      }
    });
  }

  public Mono<Void> sendApprovalAlert(
      ConnectAlertProperties.EmailProperties config,
      ApprovalAlertPayload payload,
      List<String> recipients
  ) {
    if (!config.isEnabled() || !StringUtils.hasText(config.getSmtpHost()) || recipients == null
        || recipients.isEmpty()) {
      log.debug("Email approval alerts disabled, SMTP host not configured, or recipients empty");
      return Mono.empty();
    }

    return Mono.fromRunnable(() -> {
      try {
        JavaMailSenderImpl mailSender = createMailSender(config);
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

        helper.setFrom(config.getFrom());
        helper.setTo(recipients.toArray(new String[0]));
        helper.setSubject(("[Kafbat UI Approval Required] %s in cluster %s - Requested by %s")
            .formatted(payload.getSubjectSummary(), payload.clusterName(), payload.requesterEmail()));

        String plainText = buildApprovalEmailBody(payload);
        String htmlText = buildApprovalHtmlEmailBody(payload);
        helper.setText(plainText, htmlText);

        mailSender.send(mimeMessage);
        log.info("Approval Email alert sent for [{}] '{}' on cluster '{}' to {}",
            payload.type(), payload.targetName(), payload.clusterName(), recipients);
      } catch (Exception e) {
        log.error("Failed to send approval email alert for [{}] '{}' on cluster '{}'",
            payload.type(), payload.targetName(), payload.clusterName(), e);
      }
    });
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
    String rootCause = payload.getRootCause();
    return """
        Kafka Connect Failure Notification
        ==================================

        Cluster:           %s
        Connect Cluster:   %s
        Connector:         %s
        Component:         %s
        Timestamp:         %s
        Root Cause:        %s

        Error Trace:
        ----------------------------------
        %s
        """.formatted(
        payload.clusterName(),
        payload.connectName(),
        payload.connectorName(),
        payload.component(),
        payload.timestamp().toString(),
        rootCause != null ? rootCause : "N/A",
        payload.getTruncatedTrace(MAX_TRACE_LENGTH)
    );
  }

  private String buildHtmlEmailBody(ConnectAlertPayload payload) {
    String escapedTrace = HtmlUtils.htmlEscape(payload.getTruncatedTrace(MAX_TRACE_LENGTH));
    String rootCauseHtml = "";
    if (StringUtils.hasText(payload.getRootCause())) {
      rootCauseHtml = """
          <tr><td class="label">Root Cause</td><td><strong style="color:#d9534f">%s</strong></td></tr>
          """.formatted(HtmlUtils.htmlEscape(payload.getRootCause()));
    }
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
                %s
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
        rootCauseHtml,
        escapedTrace
    );
  }

  private String buildApprovalEmailBody(ApprovalAlertPayload payload) {
    return """
        Kafka-UI Approval Request Pending
        ==================================

        Subject:           %s
        Cluster:           %s
        Target Name:       %s
        Action Type:       %s
        Requested By:      %s
        Approver Roles:    %s
        Timestamp:         %s

        A new approval request has been submitted and is pending review.
        To inspect the configuration details and approve or reject the request,
        please visit the Approvals section in Kafbat UI:
        /ui/clusters/%s/approvals
        """.formatted(
        payload.getSubjectSummary(),
        payload.clusterName(),
        payload.targetName(),
        payload.type().name(),
        payload.requesterEmail(),
        payload.approverRoles() != null ? String.join(", ", payload.approverRoles()) : "Architect, Admin",
        payload.timestamp().toString(),
        payload.clusterName()
    );
  }

  private String buildApprovalHtmlEmailBody(ApprovalAlertPayload payload) {
    String approverRolesStr = payload.approverRoles() != null && !payload.approverRoles().isEmpty()
        ? HtmlUtils.htmlEscape(String.join(", ", payload.approverRoles()))
        : "Architect, Admin";

    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="UTF-8">
          <style>
            body { font-family: -apple-system, sans-serif; background: #f4f6f8; margin: 0; padding: 20px; }
            .container { max-width: 650px; margin: 0 auto; background: #fff; border-radius: 8px; }
            .header { background: #3b82f6; color: #fff; padding: 16px; font-size: 18px; font-weight: bold; }
            .content { padding: 20px; }
            .meta-table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }
            .meta-table td { padding: 8px; border-bottom: 1px solid #f0f0f0; font-size: 14px; }
            .meta-table td.label { font-weight: 600; width: 150px; color: #555; background: #fafbfc; }
            .footer { padding: 12px; background: #f8f9fa; border-top: 1px solid #e1e4e8;
                      font-size: 12px; color: #888; text-align: center; }
          </style>
        </head>
        <body>
          <div class="container">
            <div class="header">⏳ New Approval Request: %s</div>
            <div class="content">
              <table class="meta-table">
                <tr><td class="label">Cluster</td><td>%s</td></tr>
                <tr><td class="label">Subject</td><td><strong>%s</strong></td></tr>
                <tr><td class="label">Target</td><td><strong>%s</strong></td></tr>
                <tr><td class="label">Action Type</td>
                    <td><span style="color:#2563eb;font-weight:600;">%s</span></td></tr>
                <tr><td class="label">Requested By</td><td>%s</td></tr>
                <tr><td class="label">Approver Roles</td><td>%s</td></tr>
                <tr><td class="label">Timestamp</td><td>%s</td></tr>
              </table>
              <p style="margin-top:20px; font-size:14px; color:#333;">
                A new approval request has been submitted and is pending review.<br/>
                Please visit the Kafbat UI <strong>Approvals</strong> tab to inspect the configuration
                and approve or reject the request.
              </p>
            </div>
            <div class="footer">Sent by Kafbat UI Approval Workflow System</div>
          </div>
        </body>
        </html>
        """.formatted(
        HtmlUtils.htmlEscape(payload.getSubjectSummary()),
        HtmlUtils.htmlEscape(payload.clusterName()),
        HtmlUtils.htmlEscape(payload.getSubjectSummary()),
        HtmlUtils.htmlEscape(payload.targetName()),
        HtmlUtils.htmlEscape(payload.type().name()),
        HtmlUtils.htmlEscape(payload.requesterEmail()),
        approverRolesStr,
        payload.timestamp().toString()
    );
  }
}
