package io.kafbat.ui.service.metrics.prometheus;

import static io.kafbat.ui.service.metrics.MetricsUtils.isTheSameMetric;
import static io.kafbat.ui.service.metrics.prometheus.PrometheusMetricsExposer.prepareMetricsForGlobalExpose;
import static org.assertj.core.api.Assertions.assertThat;

import io.kafbat.ui.model.ConnectorStateDTO;
import io.kafbat.ui.model.ConnectorStatusDTO;
import io.kafbat.ui.model.ConnectorTaskStatusDTO;
import io.kafbat.ui.model.ConnectorTypeDTO;
import io.kafbat.ui.model.Metrics;
import io.kafbat.ui.service.metrics.scrape.KafkaConnectState;
import io.kafbat.ui.service.metrics.scrape.ScrapedClusterState;
import io.kafbat.ui.service.metrics.scrape.inferred.InferredMetrics;
import io.kafbat.ui.service.metrics.scrape.inferred.InferredMetricsScraper;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricMetadata;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class PrometheusExposeTest {

  @Test
  void prepareMetricsForGlobalExposeAppendsClusterAndBrokerIdLabelsToMetrics() {

    var inferredMfs = new GaugeSnapshot(new MetricMetadata("infer", "help"), List.of(
        new GaugeSnapshot.GaugeDataPointSnapshot(100, Labels.of("lbl1", "lblVal1"), null)));

    var broker1Mfs = new GaugeSnapshot(new MetricMetadata("brok", "help"), List.of(
        new GaugeSnapshot.GaugeDataPointSnapshot(101, Labels.of("broklbl1", "broklblVal1"), null)));

    var broker2Mfs = new GaugeSnapshot(new MetricMetadata("brok", "help"), List.of(
        new GaugeSnapshot.GaugeDataPointSnapshot(102, Labels.of("broklbl1", "broklblVal1"), null)));

    List<MetricSnapshot> prepared = prepareMetricsForGlobalExpose(
        "testCluster",
        Metrics.builder()
            .inferredMetrics(new InferredMetrics(List.of(inferredMfs)))
            .perBrokerScrapedMetrics(Map.of(1, List.of(broker1Mfs), 2, List.of(broker2Mfs)))
            .build()
    ).toList();

    assertThat(prepared)
        .hasSize(3)
        .anyMatch(p -> isTheSameMetric(p, new GaugeSnapshot(new MetricMetadata("infer", "help"), List.of(
            new GaugeSnapshot.GaugeDataPointSnapshot(100,
                Labels.of("cluster", "testCluster", "lbl1", "lblVal1"), null
            ))
        )))
        .anyMatch(p -> isTheSameMetric(p, new GaugeSnapshot(new MetricMetadata("brok", "help"), List.of(
            new GaugeSnapshot.GaugeDataPointSnapshot(101,
                Labels.of(
                    "cluster", "testCluster",
                    "broker_id", "1",
                    "broklbl1", "broklblVal1"
                ), null
            ))
        )))
        .anyMatch(p -> isTheSameMetric(p, new GaugeSnapshot(new MetricMetadata("brok", "help"), List.of(
                new GaugeSnapshot.GaugeDataPointSnapshot(102,
                    Labels.of(
                        "cluster", "testCluster",
                        "broker_id", "2",
                        "broklbl1", "broklblVal1"
                    ), null
                ))
            )
        ));
  }

  @Test
  void exposeAllMetricsIncludesKafkaConnectConnectorAndTaskStateWithClusterLabel() {
    var connectState = KafkaConnectState.builder()
        .scrapeFinishedAt(Instant.ofEpochSecond(1700000000L))
        .name("my-connect")
        .version("7.6.0")
        .up(true)
        .connectors(List.of(
            new KafkaConnectState.ConnectorState(
                "orders-sink",
                ConnectorTypeDTO.SINK,
                new ConnectorStatusDTO(ConnectorStateDTO.RUNNING, "connect-worker-1:8083", null),
                List.of("orders"),
                List.of(
                    new KafkaConnectState.TaskState(0, ConnectorTaskStatusDTO.FAILED, "connect-worker-1:8083")
                )
            )
        ))
        .build();

    InferredMetrics inferred = new InferredMetricsScraper()
        .scrape(ScrapedClusterState.empty(), List.of(connectState))
        .block();

    Metrics metrics = Metrics.builder()
        .ioRates(Metrics.empty().getIoRates())
        .inferredMetrics(inferred)
        .perBrokerScrapedMetrics(Map.of())
        .build();

    ResponseEntity<String> response = PrometheusMetricsExposer.exposeAllMetrics(Map.of("prod-cluster", metrics));
    assertThat(response.getBody())
        .contains("kafka_connect_up{cluster=\"prod-cluster\",connect=\"my-connect\"} 1.0")
        .contains("kafka_connect_connector_tasks_count{cluster=\"prod-cluster\",connect=\"my-connect\","
            + "connector=\"orders-sink\",type=\"SINK\"} 1.0")
        .contains("kafka_connect_connector_status{cluster=\"prod-cluster\",connect=\"my-connect\","
            + "connector=\"orders-sink\",state=\"TASK_FAILED\",type=\"SINK\"} 1.0")
        .contains("kafka_connect_task_status{cluster=\"prod-cluster\",connect=\"my-connect\","
            + "connector=\"orders-sink\",state=\"FAILED\",task_id=\"0\",type=\"SINK\"} 1.0")
        .contains("kafka_connect_task_failed{cluster=\"prod-cluster\",connect=\"my-connect\","
            + "connector=\"orders-sink\",task_id=\"0\",type=\"SINK\"} 1.0");
  }

}
