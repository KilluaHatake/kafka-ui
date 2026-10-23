package io.kafbat.ui.service.notification;

import io.kafbat.ui.config.ConnectAlertProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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
}
