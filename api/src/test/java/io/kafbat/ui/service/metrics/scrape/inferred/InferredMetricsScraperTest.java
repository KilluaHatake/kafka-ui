package io.kafbat.ui.service.metrics.scrape.inferred;

import static io.kafbat.ui.model.InternalLogDirStats.LogDirSpaceStats;
import static io.kafbat.ui.model.InternalLogDirStats.SegmentStats;
import static io.kafbat.ui.service.metrics.scrape.ScrapedClusterState.ConsumerGroupState;
import static io.kafbat.ui.service.metrics.scrape.ScrapedClusterState.NodeState;
import static io.kafbat.ui.service.metrics.scrape.ScrapedClusterState.TopicState;
import static org.assertj.core.api.Assertions.assertThat;

import io.kafbat.ui.model.ConnectorStateDTO;
import io.kafbat.ui.model.ConnectorStatusDTO;
import io.kafbat.ui.model.ConnectorTaskStatusDTO;
import io.kafbat.ui.model.ConnectorTypeDTO;
import io.kafbat.ui.service.metrics.scrape.KafkaConnectState;
import io.kafbat.ui.service.metrics.scrape.ScrapedClusterState;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot.GaugeDataPointSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.MemberAssignment;
import org.apache.kafka.clients.admin.MemberDescription;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class InferredMetricsScraperTest {

  final InferredMetricsScraper scraper = new InferredMetricsScraper();

  @Test
  void allExpectedMetricsScraped() {
    var segmentStats = new SegmentStats(1234L, 3);
    var logDirStats = new LogDirSpaceStats(234L, 345L, Map.of(), Map.of());

    Node node1 = new Node(1, "node1", 9092);
    Node node2 = new Node(2, "node2", 9092);

    Mono<InferredMetrics> scraped = scraper.scrape(
        ScrapedClusterState.builder()
            .scrapeFinishedAt(Instant.now())
            .nodesStates(
                Map.of(
                    1, new NodeState(1, node1, segmentStats, logDirStats),
                    2, new NodeState(2, node2, segmentStats, logDirStats)
                )
            )
            .topicStates(
                Map.of(
                    "t1",
                    new TopicState(
                        "t1",
                        new TopicDescription(
                            "t1",
                            false,
                            List.of(
                                new TopicPartitionInfo(0, node1, List.of(node1, node2), List.of(node1, node2)),
                                new TopicPartitionInfo(1, node1, List.of(node1, node2), List.of(node1))
                            )
                        ),
                        List.of(),
                        Map.of(0, 100L, 1, 101L),
                        Map.of(0, 200L, 1, 201L),
                        segmentStats,
                        Map.of(0, segmentStats, 1, segmentStats)
                    )
                )
            )
            .consumerGroupsStates(
                Map.of(
                    "cg1",
                    new ConsumerGroupState(
                        "cg1",
                        new ConsumerGroupDescription(
                            "cg1",
                            true,
                            List.of(
                                new MemberDescription(
                                    "memb1", Optional.empty(), "client1", "hst1",
                                    new MemberAssignment(Set.of(new TopicPartition("t1", 0)))
                                )
                            ),
                            null,
                            org.apache.kafka.common.ConsumerGroupState.STABLE,
                            node1
                        ),
                        Map.of(new TopicPartition("t1", 0), 150L)
                    )
                )
            )
            .build()
    );

    StepVerifier.create(scraped)
        .assertNext(inferredMetrics ->
            assertThat(inferredMetrics.asStream().map(m -> m.getMetadata().getName())).containsExactlyInAnyOrder(
                "broker_count",
                "broker_bytes_disk",
                "broker_bytes_usable",
                "broker_bytes",
                "topic_count",
                "kafka_topic_partitions",
                "kafka_topic_partition_next_offset",
                "kafka_topic_partition_oldest_offset",
                "kafka_topic_partition_in_sync_replica",
                "kafka_topic_partition_replicas",
                "kafka_topic_partition_leader",
                "topic_bytes_disk",
                "group_count",
                "group_state",
                "group_member_count",
                "group_host_count",
                "kafka_consumergroup_current_offset",
                "kafka_consumergroup_lag"
            )
        )
        .verifyComplete();
  }

  @Test
  void kafkaConnectMetricsScraped() {
    Instant scrapeTime = Instant.ofEpochSecond(1700000000L);

    var sinkConnector = new KafkaConnectState.ConnectorState(
        "sink-orders",
        ConnectorTypeDTO.SINK,
        new ConnectorStatusDTO(ConnectorStateDTO.RUNNING, "worker-1:8083", null),
        List.of("orders", "orders-dlq"),
        List.of(
            new KafkaConnectState.TaskState(0, ConnectorTaskStatusDTO.RUNNING, "worker-1:8083"),
            new KafkaConnectState.TaskState(1, ConnectorTaskStatusDTO.FAILED, "worker-2:8083")
        )
    );

    var sourceConnector = new KafkaConnectState.ConnectorState(
        "source-users",
        ConnectorTypeDTO.SOURCE,
        new ConnectorStatusDTO(ConnectorStateDTO.PAUSED, "worker-2:8083", null),
        List.of("users"),
        List.of(
            new KafkaConnectState.TaskState(0, ConnectorTaskStatusDTO.PAUSED, "worker-2:8083")
        )
    );

    var primaryConnect = KafkaConnectState.builder()
        .scrapeFinishedAt(scrapeTime)
        .name("connect-primary")
        .version("7.6.0")
        .up(true)
        .connectors(List.of(sinkConnector, sourceConnector))
        .build();

    var secondaryConnect = KafkaConnectState.builder()
        .scrapeFinishedAt(scrapeTime)
        .name("connect-secondary")
        .version("Unknown")
        .up(false)
        .connectors(List.of())
        .build();

    Mono<InferredMetrics> scraped = scraper.scrape(
        ScrapedClusterState.empty(),
        List.of(primaryConnect, secondaryConnect)
    );

    StepVerifier.create(scraped)
        .assertNext(metrics -> {
          assertThat(metrics.asStream().map(m -> m.getMetadata().getName())
              .filter(name -> name.startsWith("kafka_connect")))
              .containsExactlyInAnyOrder(
                  "kafka_connect_count",
                  "kafka_connect_up",
                  "kafka_connect_metadata",
                  "kafka_connect_last_scrape_timestamp_seconds",
                  "kafka_connect_connectors_count",
                  "kafka_connect_failed_connectors_count",
                  "kafka_connect_tasks_count",
                  "kafka_connect_failed_tasks_count",
                  "kafka_connect_connector_status",
                  "kafka_connect_connector_failed",
                  "kafka_connect_connector_tasks_count",
                  "kafka_connect_connector_tasks_failed_count",
                  "kafka_connect_connector_tasks_running_count",
                  "kafka_connect_connector_topics_count",
                  "kafka_connect_connector_metadata",
                  "kafka_connect_task_status",
                  "kafka_connect_task_failed",
                  "kafka_connect_task_metadata"
              );

          assertThat(gaugeValue(metrics, "kafka_connect_count", Labels.EMPTY)).isEqualTo(2.0);
          assertThat(gaugeValue(metrics, "kafka_connect_up", Labels.of("connect", "connect-primary")))
              .isEqualTo(1.0);
          assertThat(gaugeValue(metrics, "kafka_connect_up", Labels.of("connect", "connect-secondary")))
              .isEqualTo(0.0);
          assertThat(gaugeValue(metrics, "kafka_connect_metadata",
              Labels.of("connect", "connect-primary", "version", "7.6.0")))
              .isEqualTo(1.0);
          assertThat(gaugeValue(metrics, "kafka_connect_last_scrape_timestamp_seconds",
              Labels.of("connect", "connect-primary")))
              .isEqualTo(1700000000.0);
          assertThat(hasDataPoint(metrics, "kafka_connect_last_scrape_timestamp_seconds",
              Labels.of("connect", "connect-secondary")))
              .isFalse();

          assertThat(gaugeValue(metrics, "kafka_connect_connectors_count", Labels.of("connect", "connect-primary")))
              .isEqualTo(2.0);
          assertThat(gaugeValue(metrics, "kafka_connect_failed_connectors_count",
              Labels.of("connect", "connect-primary")))
              .isEqualTo(1.0);
          assertThat(gaugeValue(metrics, "kafka_connect_tasks_count", Labels.of("connect", "connect-primary")))
              .isEqualTo(3.0);
          assertThat(gaugeValue(metrics, "kafka_connect_failed_tasks_count", Labels.of("connect", "connect-primary")))
              .isEqualTo(1.0);
          assertThat(hasDataPoint(metrics, "kafka_connect_connectors_count",
              Labels.of("connect", "connect-secondary")))
              .isFalse();

          // sink-orders has RUNNING connector status but a FAILED task -> effective state TASK_FAILED
          assertThat(gaugeValue(metrics, "kafka_connect_connector_status",
              Labels.of("connect", "connect-primary", "connector", "sink-orders",
                  "type", "SINK", "state", "TASK_FAILED")))
              .isEqualTo(1.0);
          assertThat(gaugeValue(metrics, "kafka_connect_connector_status",
              Labels.of("connect", "connect-primary", "connector", "sink-orders",
                  "type", "SINK", "state", "RUNNING")))
              .isEqualTo(0.0);
          assertThat(gaugeValue(metrics, "kafka_connect_connector_failed",
              Labels.of("connect", "connect-primary", "connector", "sink-orders", "type", "SINK")))
              .isEqualTo(1.0);
          assertThat(gaugeValue(metrics, "kafka_connect_connector_tasks_count",
              Labels.of("connect", "connect-primary", "connector", "sink-orders", "type", "SINK")))
              .isEqualTo(2.0);
          assertThat(gaugeValue(metrics, "kafka_connect_connector_tasks_failed_count",
              Labels.of("connect", "connect-primary", "connector", "sink-orders", "type", "SINK")))
              .isEqualTo(1.0);
          assertThat(gaugeValue(metrics, "kafka_connect_connector_tasks_running_count",
              Labels.of("connect", "connect-primary", "connector", "sink-orders", "type", "SINK")))
              .isEqualTo(1.0);
          assertThat(gaugeValue(metrics, "kafka_connect_connector_topics_count",
              Labels.of("connect", "connect-primary", "connector", "sink-orders", "type", "SINK")))
              .isEqualTo(2.0);
          assertThat(gaugeValue(metrics, "kafka_connect_connector_metadata",
              Labels.of("connect", "connect-primary", "connector", "sink-orders",
                  "type", "SINK", "worker_id", "worker-1:8083")))
              .isEqualTo(1.0);

          // source-users is PAUSED with 1 PAUSED task
          assertThat(gaugeValue(metrics, "kafka_connect_connector_status",
              Labels.of("connect", "connect-primary", "connector", "source-users",
                  "type", "SOURCE", "state", "PAUSED")))
              .isEqualTo(1.0);
          assertThat(gaugeValue(metrics, "kafka_connect_connector_failed",
              Labels.of("connect", "connect-primary", "connector", "source-users", "type", "SOURCE")))
              .isEqualTo(0.0);

          // Individual task status and failure gauges
          assertThat(gaugeValue(metrics, "kafka_connect_task_status",
              Labels.of("connect", "connect-primary", "connector", "sink-orders",
                  "type", "SINK", "task_id", "0", "state", "RUNNING")))
              .isEqualTo(1.0);
          assertThat(gaugeValue(metrics, "kafka_connect_task_failed",
              Labels.of("connect", "connect-primary", "connector", "sink-orders",
                  "type", "SINK", "task_id", "0")))
              .isEqualTo(0.0);
          assertThat(gaugeValue(metrics, "kafka_connect_task_status",
              Labels.of("connect", "connect-primary", "connector", "sink-orders",
                  "type", "SINK", "task_id", "1", "state", "FAILED")))
              .isEqualTo(1.0);
          assertThat(gaugeValue(metrics, "kafka_connect_task_status",
              Labels.of("connect", "connect-primary", "connector", "sink-orders",
                  "type", "SINK", "task_id", "1", "state", "RUNNING")))
              .isEqualTo(0.0);
          assertThat(gaugeValue(metrics, "kafka_connect_task_failed",
              Labels.of("connect", "connect-primary", "connector", "sink-orders",
                  "type", "SINK", "task_id", "1")))
              .isEqualTo(1.0);
          assertThat(gaugeValue(metrics, "kafka_connect_task_metadata",
              Labels.of("connect", "connect-primary", "connector", "sink-orders",
                  "type", "SINK", "task_id", "1", "worker_id", "worker-2:8083")))
              .isEqualTo(1.0);
        })
        .verifyComplete();
  }

  private double gaugeValue(InferredMetrics metrics, String metricName, Labels labels) {
    return findDataPoint(metrics, metricName, labels)
        .orElseThrow(() -> new AssertionError("Missing metric %s with labels %s".formatted(metricName, labels)))
        .getValue();
  }

  private boolean hasDataPoint(InferredMetrics metrics, String metricName, Labels labels) {
    return findDataPoint(metrics, metricName, labels).isPresent();
  }

  @SuppressWarnings("unchecked")
  private Optional<GaugeDataPointSnapshot> findDataPoint(InferredMetrics metrics, String metricName, Labels labels) {
    return metrics.asStream()
        .filter(s -> s.getMetadata().getName().equals(metricName) && s instanceof GaugeSnapshot)
        .flatMap(s -> ((List<GaugeDataPointSnapshot>) s.getDataPoints()).stream())
        .filter(dp -> dp.getLabels().equals(labels))
        .findFirst();
  }

}
