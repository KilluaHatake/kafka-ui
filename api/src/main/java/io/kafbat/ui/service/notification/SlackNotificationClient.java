package io.kafbat.ui.service.notification;

import io.kafbat.ui.config.ConnectAlertProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class SlackNotificationClient {

  private static final int MAX_TRACE_LENGTH = 1000;
  private final WebClient.Builder webClientBuilder;

  public Mono<Void> sendAlert(ConnectAlertProperties.SlackProperties config, ConnectAlertPayload payload) {
    if (!config.isEnabled() || !StringUtils.hasText(config.getWebhookUrl())) {
      log.debug("Slack alerts disabled or webhook URL not configured");
      return Mono.empty();
    }

    Map<String, Object> body = buildSlackMessage(config.getChannel(), payload);

    return webClientBuilder.build()
        .post()
        .uri(config.getWebhookUrl())
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .retrieve()
        .bodyToMono(String.class)
        .doOnSuccess(resp -> log.info("Slack alert sent for connector '{}' [{}] on cluster '{}'",
            payload.connectorName(), payload.component(), payload.clusterName()))
        .doOnError(th -> log.error("Failed to send Slack alert for connector '{}' on cluster '{}'",
            payload.connectorName(), payload.clusterName(), th))
        .then()
        .onErrorResume(th -> Mono.empty());
  }

  private Map<String, Object> buildSlackMessage(String channel, ConnectAlertPayload payload) {
    Map<String, Object> message = new HashMap<>();
    if (StringUtils.hasText(channel)) {
      message.put("channel", channel);
    }
    message.put("text", "🚨 Kafka Connect Failure: %s [%s] in cluster %s"
        .formatted(payload.connectorName(), payload.component(), payload.clusterName()));

    List<Map<String, Object>> blocks = new ArrayList<>();

    // Header block
    blocks.add(Map.of(
        "type", "header",
        "text", Map.of(
            "type", "plain_text",
            "text", "🚨 Kafka Connect Failure Alert",
            "emoji", true
        )
    ));

    // Details section block
    List<Map<String, String>> fields = List.of(
        Map.of("type", "mrkdwn", "text", "*Cluster:*\n" + payload.clusterName()),
        Map.of("type", "mrkdwn", "text", "*Connect Cluster:*\n" + payload.connectName()),
        Map.of("type", "mrkdwn", "text", "*Connector:*\n" + payload.connectorName()),
        Map.of("type", "mrkdwn", "text", "*Failing Component:*\n" + payload.component()),
        Map.of("type", "mrkdwn", "text", "*Timestamp:*\n" + payload.timestamp().toString())
    );
    blocks.add(Map.of("type", "section", "fields", fields));

    // Error trace block
    String trace = payload.getTruncatedTrace(MAX_TRACE_LENGTH);
    blocks.add(Map.of(
        "type", "section",
        "text", Map.of(
            "type", "mrkdwn",
            "text", "*Error Trace:*\n```\n" + trace + "\n```"
        )
    ));

    message.put("blocks", blocks);
    return message;
  }
}
