package io.kafbat.ui.controller;

import io.kafbat.ui.config.ConnectAlertProperties;
import io.kafbat.ui.model.connect.ConnectAlertSettingsDto;
import io.kafbat.ui.service.notification.ConnectAlertPayload;
import io.kafbat.ui.service.notification.EmailNotificationClient;
import io.kafbat.ui.service.notification.SlackNotificationClient;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/connect-alerts")
@RequiredArgsConstructor
@Slf4j
public class ConnectAlertSettingsController {

  private final ConnectAlertProperties alertProperties;
  private final SlackNotificationClient slackClient;
  private final EmailNotificationClient emailClient;

  @GetMapping("/settings")
  public Mono<ResponseEntity<ConnectAlertSettingsDto>> getSettings() {
    return Mono.just(ResponseEntity.ok(ConnectAlertSettingsDto.fromProperties(alertProperties)));
  }

  @PutMapping("/settings")
  public Mono<ResponseEntity<ConnectAlertSettingsDto>> updateSettings(
      @RequestBody ConnectAlertSettingsDto newSettings
  ) {
    if (newSettings != null) {
      alertProperties.setEnabled(newSettings.isEnabled());
      if (newSettings.getPollingIntervalMs() > 0) {
        alertProperties.setPollingIntervalMs(newSettings.getPollingIntervalMs());
      }
      if (newSettings.getThrottleIntervalMinutes() > 0) {
        alertProperties.setThrottleIntervalMinutes(newSettings.getThrottleIntervalMinutes());
      }
      if (newSettings.getSlack() != null) {
        alertProperties.getSlack().setEnabled(newSettings.getSlack().isEnabled());
        alertProperties.getSlack().setWebhookUrl(newSettings.getSlack().getWebhookUrl());
        alertProperties.getSlack().setChannel(newSettings.getSlack().getChannel());
      }
      if (newSettings.getEmail() != null) {
        alertProperties.getEmail().setEnabled(newSettings.getEmail().isEnabled());
        alertProperties.getEmail().setSmtpHost(newSettings.getEmail().getSmtpHost());
        if (newSettings.getEmail().getSmtpPort() > 0) {
          alertProperties.getEmail().setSmtpPort(newSettings.getEmail().getSmtpPort());
        }
        alertProperties.getEmail().setUsername(newSettings.getEmail().getUsername());
        // Only update password if a new password is provided and not the masked placeholder
        if (newSettings.getEmail().getPassword() != null
            && !newSettings.getEmail().getPassword().isBlank()
            && !newSettings.getEmail().getPassword().equals("********")) {
          alertProperties.getEmail().setPassword(newSettings.getEmail().getPassword());
        }
        alertProperties.getEmail().setStarttlsEnabled(newSettings.getEmail().isStarttlsEnabled());
        alertProperties.getEmail().setSslEnabled(newSettings.getEmail().isSslEnabled());
        alertProperties.getEmail().setFrom(newSettings.getEmail().getFrom());
        if (newSettings.getEmail().getRecipients() != null) {
          alertProperties.getEmail().setRecipients(newSettings.getEmail().getRecipients());
        }
      }
    }
    return Mono.just(ResponseEntity.ok(ConnectAlertSettingsDto.fromProperties(alertProperties)));
  }

  @PostMapping("/test")
  public Mono<ResponseEntity<Map<String, Object>>> sendTestAlert(
      @RequestBody(required = false) Map<String, String> request
  ) {
    String channel = request != null && request.containsKey("channel")
        ? request.get("channel").toLowerCase()
        : "all";

    ConnectAlertPayload testPayload = new ConnectAlertPayload(
        "local-cluster",
        "connect-cluster",
        "sample-test-connector",
        "TestAlertVerification",
        Instant.now(),
        "Verification test alert triggered from Kafbat UI. Connect error notifications are operating normally."
    );

    Mono<Void> testMono;
    if ("slack".equals(channel)) {
      testMono = slackClient.sendAlert(alertProperties.getSlack(), testPayload);
    } else if ("email".equals(channel)) {
      testMono = emailClient.sendAlert(alertProperties.getEmail(), testPayload);
    } else {
      testMono = Mono.when(
          slackClient.sendAlert(alertProperties.getSlack(), testPayload),
          emailClient.sendAlert(alertProperties.getEmail(), testPayload)
      );
    }

    return testMono
        .thenReturn(ResponseEntity.ok(Map.<String, Object>of(
            "success", true,
            "message", "Test alert dispatched successfully to " + channel
        )))
        .onErrorResume(th -> {
          log.error("Failed to dispatch test alert", th);
          return Mono.just(ResponseEntity.ok(Map.<String, Object>of(
              "success", false,
              "message", "Failed to dispatch test alert: " + th.getMessage()
          )));
        });
  }
}
