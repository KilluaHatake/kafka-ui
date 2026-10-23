package io.kafbat.ui.service.notification;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.kafbat.ui.config.ConnectAlertProperties;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class SlackNotificationClientTest {

  private WireMockServer wireMockServer;
  private SlackNotificationClient client;

  @BeforeEach
  void setUp() {
    wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    wireMockServer.start();

    client = new SlackNotificationClient(WebClient.builder());
  }

  @AfterEach
  void tearDown() {
    wireMockServer.stop();
  }

  @Test
  void sendsFormattedSlackWebhookAlert() {
    wireMockServer.stubFor(post(urlEqualTo("/slack-webhook"))
        .willReturn(aResponse().withStatus(200).withBody("ok")));

    ConnectAlertProperties.SlackProperties config = new ConnectAlertProperties.SlackProperties();
    config.setEnabled(true);
    config.setWebhookUrl(wireMockServer.baseUrl() + "/slack-webhook");
    config.setChannel("#kafka-alerts");

    ConnectAlertPayload payload = new ConnectAlertPayload(
        "test-cluster",
        "connect-cluster-1",
        "mongo-sink",
        "Connector",
        Instant.parse("2026-10-22T10:00:00Z"),
        "MongoTimeoutException: Could not connect to replica set"
    );

    client.sendAlert(config, payload).block();

    wireMockServer.verify(postRequestedFor(urlEqualTo("/slack-webhook"))
        .withHeader("Content-Type", equalTo("application/json")));
  }
}
