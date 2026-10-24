package io.kafbat.ui.controller;

import io.kafbat.ui.config.ConnectAlertProperties;
import io.kafbat.ui.config.auth.AuthenticatedUser;
import io.kafbat.ui.exception.ApprovalRequiredException;
import io.kafbat.ui.model.approval.ApprovalRequest;
import io.kafbat.ui.model.approval.ApprovalTargetType;
import io.kafbat.ui.model.connect.ConnectAlertSettingsDto;
import io.kafbat.ui.service.ClustersStorage;
import io.kafbat.ui.service.approval.ApprovalService;
import io.kafbat.ui.service.notification.ConnectAlertPayload;
import io.kafbat.ui.service.notification.EmailNotificationClient;
import io.kafbat.ui.service.notification.SlackNotificationClient;
import io.kafbat.ui.service.rbac.AccessControlService;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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
  private final ApprovalService approvalService;
  private final ClustersStorage clustersStorage;

  @GetMapping("/settings")
  public Mono<ResponseEntity<ConnectAlertSettingsDto>> getSettings() {
    return AccessControlService.getUser()
        .defaultIfEmpty(new AuthenticatedUser("system", java.util.List.of("Architect")))
        .flatMap(user -> {
          if (!approvalService.canAccessAlertSettings(user, null)) {
            return Mono.error(new AccessDeniedException("User is not authorized to access connect alert settings"));
          }
          return Mono.just(ResponseEntity.ok(ConnectAlertSettingsDto.fromProperties(alertProperties)));
        });
  }

  @PutMapping("/settings")
  public Mono<ResponseEntity<ConnectAlertSettingsDto>> updateSettings(
      @RequestBody ConnectAlertSettingsDto newSettings
  ) {
    return AccessControlService.getUser()
        .defaultIfEmpty(new AuthenticatedUser("system", java.util.List.of("Architect")))
        .flatMap(user -> {
          if (!approvalService.canAccessAlertSettings(user, null)) {
            return Mono.error(new AccessDeniedException("User is not authorized to modify connect alert settings"));
          }
          if (approvalService.isApprovalRequired(ApprovalTargetType.ALERT_SETTINGS_UPDATE, user)) {
            String clusterName = clustersStorage != null && !clustersStorage.getKafkaClusters().isEmpty()
                ? clustersStorage.getKafkaClusters().iterator().next().getName()
                : "default";

            ConnectAlertSettingsDto currentDto = ConnectAlertSettingsDto.fromProperties(alertProperties);
            Map<String, Object> oldMap = toMap(currentDto);
            Map<String, Object> newMap = toMap(newSettings);

            return approvalService.createRequest(
                clusterName,
                ApprovalTargetType.ALERT_SETTINGS_UPDATE,
                "Kafka Connect Alert Settings",
                null,
                user,
                oldMap,
                newMap,
                Map.of("target", "alert-settings")
            ).<ResponseEntity<ConnectAlertSettingsDto>>flatMap((ApprovalRequest req) -> Mono.error(new ApprovalRequiredException(
                "Alert settings update submitted for approval (ID: " + req.getId() + ")",
                req.getId()
            )));
          }

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
        });
  }

  private Map<String, Object> toMap(ConnectAlertSettingsDto dto) {
    if (dto == null) {
      return Map.of();
    }
    Map<String, Object> map = new HashMap<>();
    map.put("enabled", dto.isEnabled());
    map.put("pollingIntervalMs", dto.getPollingIntervalMs());
    map.put("throttleIntervalMinutes", dto.getThrottleIntervalMinutes());
    if (dto.getSlack() != null) {
      map.put("slack", Map.of(
          "enabled", dto.getSlack().isEnabled(),
          "webhookUrl", dto.getSlack().getWebhookUrl() != null ? dto.getSlack().getWebhookUrl() : "",
          "channel", dto.getSlack().getChannel() != null ? dto.getSlack().getChannel() : ""
      ));
    }
    if (dto.getEmail() != null) {
      Map<String, Object> emailMap = new HashMap<>();
      emailMap.put("enabled", dto.getEmail().isEnabled());
      emailMap.put("smtpHost", dto.getEmail().getSmtpHost() != null ? dto.getEmail().getSmtpHost() : "");
      emailMap.put("smtpPort", dto.getEmail().getSmtpPort());
      emailMap.put("username", dto.getEmail().getUsername() != null ? dto.getEmail().getUsername() : "");
      emailMap.put("password", dto.getEmail().getPassword() != null ? dto.getEmail().getPassword() : "");
      emailMap.put("starttlsEnabled", dto.getEmail().isStarttlsEnabled());
      emailMap.put("sslEnabled", dto.getEmail().isSslEnabled());
      emailMap.put("from", dto.getEmail().getFrom() != null ? dto.getEmail().getFrom() : "");
      emailMap.put("recipients", dto.getEmail().getRecipients() != null ? dto.getEmail().getRecipients() : List.of());
      map.put("email", emailMap);
    }
    return map;
  }

  @PostMapping("/test")
  public Mono<ResponseEntity<Map<String, Object>>> sendTestAlert(
      @RequestBody(required = false) Map<String, String> request
  ) {
    return AccessControlService.getUser()
        .defaultIfEmpty(new AuthenticatedUser("system", java.util.List.of("Architect")))
        .flatMap(user -> {
          if (!approvalService.canAccessAlertSettings(user, null)) {
            return Mono.error(new AccessDeniedException("User is not authorized to send test alerts"));
          }

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
        });
  }
}
