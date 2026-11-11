package io.kafbat.ui.service.metrics.scrape;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.kafbat.ui.config.ClustersProperties;
import io.kafbat.ui.model.ConnectorStateDTO;
import io.kafbat.ui.model.ConnectorStatusDTO;
import io.kafbat.ui.model.ConnectorTaskStatusDTO;
import io.kafbat.ui.model.ConnectorTypeDTO;
import io.kafbat.ui.service.metrics.scrape.jmx.JmxMetricsRetriever;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class MetricsScraperTest {

  @Test
  void scrapeForwardsKafkaConnectStateToInferredMetrics() {
    var clusterProps = new ClustersProperties.Cluster();
    clusterProps.setName("local");
    MetricsScraper scraper = MetricsScraper.create(clusterProps, mock(JmxMetricsRetriever.class));

    var connectState = KafkaConnectState.builder()
        .scrapeFinishedAt(Instant.ofEpochSecond(1700000000L))
        .name("main-connect")
        .version("7.6.0")
        .up(true)
        .connectors(List.of(
            new KafkaConnectState.ConnectorState(
                "sink-connector",
                ConnectorTypeDTO.SINK,
                new ConnectorStatusDTO(ConnectorStateDTO.RUNNING, "worker-1:8083", null),
                List.of("orders"),
                List.of(new KafkaConnectState.TaskState(0, ConnectorTaskStatusDTO.RUNNING, "worker-1:8083"))
            )
        ))
        .build();

    StepVerifier.create(scraper.scrape(ScrapedClusterState.empty(), List.of(connectState), List.of()))
        .assertNext(metrics -> {
          List<String> names = metrics.getInferredMetrics().asStream()
              .map(m -> m.getMetadata().getName())
              .toList();
          assertThat(names).contains(
              "kafka_connect_up",
              "kafka_connect_connectors_count",
              "kafka_connect_connector_status",
              "kafka_connect_connector_tasks_count",
              "kafka_connect_task_status"
          );
        })
        .verifyComplete();
  }
}
