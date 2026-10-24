package io.kafbat.ui.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.kafbat.ui.config.ConnectAlertProperties;
import io.kafbat.ui.model.connect.ConnectAlertSettingsDto;
import io.kafbat.ui.service.notification.EmailNotificationClient;
import io.kafbat.ui.service.notification.SlackNotificationClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ConnectAlertSettingsControllerTest {

  private ConnectAlertProperties alertProperties;
  private SlackNotificationClient slackClient;
  private EmailNotificationClient emailClient;
  private io.kafbat.ui.service.approval.ApprovalService approvalService;
  private io.kafbat.ui.service.ClustersStorage clustersStorage;
  private ConnectAlertSettingsController controller;

  @BeforeEach
  void setUp() {
    alertProperties = new ConnectAlertProperties();
    alertProperties.setEnabled(true);
    alertProperties.setPollingIntervalMs(15000);
    alertProperties.setThrottleIntervalMinutes(10);
    alertProperties.getSlack().setEnabled(true);
    alertProperties.getSlack().setWebhookUrl("https://hooks.slack.com/services/TEST/123");
    alertProperties.getSlack().setChannel("#test-alerts");

    alertProperties.getEmail().setEnabled(true);
    alertProperties.getEmail().setSmtpHost("smtp.test.com");
    alertProperties.getEmail().setSmtpPort(587);
    alertProperties.getEmail().setUsername("tester");
    alertProperties.getEmail().setPassword("secretpassword");
    alertProperties.getEmail().setRecipients(List.of("alerts@test.com"));

    slackClient = mock(SlackNotificationClient.class);
    emailClient = mock(EmailNotificationClient.class);
    approvalService = mock(io.kafbat.ui.service.approval.ApprovalService.class);
    clustersStorage = mock(io.kafbat.ui.service.ClustersStorage.class);

    when(slackClient.sendAlert(any(), any())).thenReturn(Mono.empty());
    when(emailClient.sendAlert(any(), any())).thenReturn(Mono.empty());
    when(approvalService.isApprovalRequired(any(), any())).thenReturn(false);
    when(approvalService.canAccessAlertSettings(any(), any())).thenReturn(true);

    controller = new ConnectAlertSettingsController(
        alertProperties, slackClient, emailClient, approvalService, clustersStorage
    );
  }

  @Test
  void testGetSettings() {
    StepVerifier.create(controller.getSettings())
        .assertNext(resp -> {
          assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
          ConnectAlertSettingsDto body = resp.getBody();
          assertThat(body).isNotNull();
          assertThat(body.isEnabled()).isTrue();
          assertThat(body.getPollingIntervalMs()).isEqualTo(15000);
          assertThat(body.getThrottleIntervalMinutes()).isEqualTo(10);
          assertThat(body.getSlack().isEnabled()).isTrue();
          assertThat(body.getSlack().getWebhookUrl()).isEqualTo("https://hooks.slack.com/services/TEST/123");
          assertThat(body.getEmail().isEnabled()).isTrue();
          assertThat(body.getEmail().getPassword()).isEqualTo("********"); // masked
          assertThat(body.getEmail().getRecipients()).containsExactly("alerts@test.com");
        })
        .verifyComplete();
  }

  @Test
  void testUpdateSettingsPreservesPasswordWhenMasked() {
    ConnectAlertSettingsDto updateDto = ConnectAlertSettingsDto.builder()
        .enabled(false)
        .pollingIntervalMs(45000)
        .throttleIntervalMinutes(20)
        .slack(ConnectAlertSettingsDto.SlackDto.builder()
            .enabled(false)
            .webhookUrl("https://hooks.slack.com/services/NEW/456")
            .channel("#new-channel")
            .build())
        .email(ConnectAlertSettingsDto.EmailDto.builder()
            .enabled(true)
            .smtpHost("smtp.new.com")
            .smtpPort(465)
            .username("newuser")
            .password("********") // Unchanged masked password
            .starttlsEnabled(false)
            .sslEnabled(true)
            .from("newfrom@test.com")
            .recipients(List.of("ops@test.com", "dev@test.com"))
            .build())
        .build();

    StepVerifier.create(controller.updateSettings(updateDto))
        .assertNext(resp -> {
          assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
          ConnectAlertSettingsDto body = resp.getBody();
          assertThat(body).isNotNull();
          assertThat(body.isEnabled()).isFalse();
          assertThat(body.getPollingIntervalMs()).isEqualTo(45000);
          assertThat(body.getThrottleIntervalMinutes()).isEqualTo(20);
          assertThat(body.getSlack().isEnabled()).isFalse();
          assertThat(body.getSlack().getWebhookUrl()).isEqualTo("https://hooks.slack.com/services/NEW/456");

          // Ensure original password was preserved
          assertThat(alertProperties.getEmail().getPassword()).isEqualTo("secretpassword");
          assertThat(alertProperties.getEmail().getRecipients()).containsExactly("ops@test.com", "dev@test.com");
        })
        .verifyComplete();
  }

  @Test
  void testUpdateSettingsChangesPasswordWhenNewPasswordProvided() {
    ConnectAlertSettingsDto updateDto = ConnectAlertSettingsDto.builder()
        .enabled(true)
        .email(ConnectAlertSettingsDto.EmailDto.builder()
            .enabled(true)
            .smtpHost("smtp.test.com")
            .password("brandNewPassword123")
            .recipients(List.of("alerts@test.com"))
            .build())
        .build();

    StepVerifier.create(controller.updateSettings(updateDto))
        .assertNext(resp -> {
          assertThat(alertProperties.getEmail().getPassword()).isEqualTo("brandNewPassword123");
        })
        .verifyComplete();
  }

  @Test
  void testSendTestAlertSlack() {
    StepVerifier.create(controller.sendTestAlert(Map.of("channel", "slack")))
        .assertNext(resp -> {
          assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
          assertThat(resp.getBody()).containsEntry("success", true);
          verify(slackClient).sendAlert(any(), any());
        })
        .verifyComplete();
  }

  @Test
  void testSendTestAlertEmail() {
    StepVerifier.create(controller.sendTestAlert(Map.of("channel", "email")))
        .assertNext(resp -> {
          assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
          assertThat(resp.getBody()).containsEntry("success", true);
          verify(emailClient).sendAlert(any(), any());
        })
        .verifyComplete();
  }

  @Test
  void testUnauthorizedCannotGetOrUpdateOrTestAlertSettings() {
    when(approvalService.canAccessAlertSettings(any(), any())).thenReturn(false);

    StepVerifier.create(controller.getSettings())
        .expectError(org.springframework.security.access.AccessDeniedException.class)
        .verify();

    StepVerifier.create(controller.updateSettings(new ConnectAlertSettingsDto()))
        .expectError(org.springframework.security.access.AccessDeniedException.class)
        .verify();

    StepVerifier.create(controller.sendTestAlert(Map.of()))
        .expectError(org.springframework.security.access.AccessDeniedException.class)
        .verify();
  }
}
