package io.kafbat.ui.service.metrics.scrape.inferred;

import com.google.common.annotations.VisibleForTesting;
import io.kafbat.ui.model.ConnectorStateDTO;
import io.kafbat.ui.model.ConnectorStatusDTO;
import io.kafbat.ui.model.ConnectorTaskStatusDTO;
import io.kafbat.ui.model.ConnectorTypeDTO;
import io.kafbat.ui.service.metrics.scrape.KafkaConnectState;
import io.kafbat.ui.service.metrics.scrape.ScrapedClusterState;
import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.core.metrics.Metric;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.prometheus.metrics.model.snapshots.PrometheusNaming;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.MemberDescription;
import org.apache.kafka.common.Node;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
public class InferredMetricsScraper {

  public static final String NODE_ID_TAG = "node_id";
  public static final String TOPIC_TAG = "topic";
  public static final String GROUP_TAG = "group";
  public static final String PARTITION_TAG = "partition";
  public static final String CONNECT_TAG = "connect";
  public static final String CONNECTOR_TAG = "connector";
  public static final String TYPE_TAG = "type";
  public static final String TASK_ID_TAG = "task_id";
  public static final String STATE_TAG = "state";
  public static final String VERSION_TAG = "version";
  public static final String WORKER_ID_TAG = "worker_id";
  private ScrapedClusterState prevState = null;

  public synchronized Mono<InferredMetrics> scrape(ScrapedClusterState newState) {
    return scrape(newState, List.of());
  }

  public synchronized Mono<InferredMetrics> scrape(ScrapedClusterState newState,
                                                   Collection<KafkaConnectState> connectStates) {
    var inferred = infer(prevState, newState, connectStates);
    this.prevState = newState;
    return Mono.just(inferred);
  }

  @VisibleForTesting
  static InferredMetrics infer(@Nullable ScrapedClusterState prevState, ScrapedClusterState newState) {
    return infer(prevState, newState, List.of());
  }

  @VisibleForTesting
  static InferredMetrics infer(@Nullable ScrapedClusterState prevState,
                               ScrapedClusterState newState,
                               Collection<KafkaConnectState> connectStates) {
    var registry = new MetricsRegistry();
    fillNodesMetrics(registry, newState);
    fillTopicMetrics(registry, newState);
    fillConsumerGroupsMetrics(registry, newState);
    fillKafkaConnectMetrics(registry, connectStates);
    List<MetricSnapshot> metrics = registry.metrics.values().stream().map(Metric::collect).toList();
    log.debug("{} metric families inferred from cluster state", metrics.size());
    return new InferredMetrics(metrics);
  }

  private static class MetricsRegistry {

    final Map<String, Metric> metrics = new LinkedHashMap<>();

    void gauge(String name,
               String help,
               List<String> lbls,
               List<String> lblVals,
               Number value) {


      Gauge gauge = (Gauge) metrics.computeIfAbsent(name, (n) ->
          Gauge.builder()
              .name(PrometheusNaming.sanitizeMetricName(name))
              .labelNames(lbls.toArray(new String[0]))
              .help(help).build()
      );

      gauge.labelValues(lblVals.toArray(new String[0]))
          .set(value.doubleValue());
    }
  }

  private static void fillNodesMetrics(MetricsRegistry registry, ScrapedClusterState newState) {
    registry.gauge(
        "broker_count",
        "Number of brokers in the Kafka cluster",
        List.of(),
        List.of(),
        newState.getNodesStates().size()
    );

    newState.getNodesStates().forEach((nodeId, state) -> {
      if (state.segmentStats() != null) {
        registry.gauge(
            "broker_bytes_disk",
            "Written disk size in bytes of a broker",
            List.of(NODE_ID_TAG),
            List.of(nodeId.toString()),
            state.segmentStats().getSegmentSize()
        );
      }
      if (state.logDirSpaceStats() != null) {
        if (state.logDirSpaceStats().usableBytes() != null) {
          registry.gauge(
              "broker_bytes_usable",
              "Usable disk size in bytes of a broker",
              List.of(NODE_ID_TAG),
              List.of(nodeId.toString()),
              state.logDirSpaceStats().usableBytes()
          );
        }
        if (state.logDirSpaceStats().totalBytes() != null) {
          registry.gauge(
              "broker_bytes_total",
              "Total disk size in bytes of a broker",
              List.of(NODE_ID_TAG),
              List.of(nodeId.toString()),
              state.logDirSpaceStats().totalBytes()
          );
        }
      }
    });
  }

  private static void fillTopicMetrics(MetricsRegistry registry, ScrapedClusterState clusterState) {
    registry.gauge(
        "topic_count",
        "Number of topics in the Kafka cluster",
        List.of(),
        List.of(),
        clusterState.getTopicStates().size()
    );

    clusterState.getTopicStates().forEach((topicName, state) -> {
      registry.gauge(
          "kafka_topic_partitions",
          "Number of partitions for this Topic",
          List.of(TOPIC_TAG),
          List.of(topicName),
          state.description().partitions().size()
      );
      state.endOffsets().forEach((partition, endOffset) -> registry.gauge(
          "kafka_topic_partition_next_offset",
          "Current (next) Offset of a Broker at Topic/Partition",
          List.of(TOPIC_TAG, PARTITION_TAG),
          List.of(topicName, String.valueOf(partition)),
          endOffset
      ));
      state.startOffsets().forEach((partition, startOffset) -> registry.gauge(
          "kafka_topic_partition_oldest_offset",
          "Oldest Offset of a Broker at Topic/Partition",
          List.of(TOPIC_TAG, PARTITION_TAG),
          List.of(topicName, String.valueOf(partition)),
          startOffset
      ));
      state.description().partitions().forEach(p -> {
        registry.gauge(
            "kafka_topic_partition_in_sync_replica",
            "Number of In-Sync Replicas for this Topic/Partition",
            List.of(TOPIC_TAG, PARTITION_TAG),
            List.of(topicName, String.valueOf(p.partition())),
            p.isr().size()
        );
        registry.gauge(
            "kafka_topic_partition_replicas",
            "Number of Replicas for this Topic/Partition",
            List.of(TOPIC_TAG, PARTITION_TAG),
            List.of(topicName, String.valueOf(p.partition())),
            p.replicas().size()
        );
        registry.gauge(
            "kafka_topic_partition_leader",
            "Leader Broker ID of this Topic/Partition (-1, if no leader)",
            List.of(TOPIC_TAG, PARTITION_TAG),
            List.of(topicName, String.valueOf(p.partition())),
            Optional.ofNullable(p.leader()).map(Node::id).orElse(-1)
        );
      });
      if (state.segmentStats() != null) {
        registry.gauge(
            "topic_bytes_disk",
            "Disk size in bytes of a topic",
            List.of(TOPIC_TAG),
            List.of(topicName),
            state.segmentStats().getSegmentSize()
        );
      }
    });
  }

  private static void fillConsumerGroupsMetrics(MetricsRegistry registry, ScrapedClusterState clusterState) {
    registry.gauge(
        "group_count",
        "Number of consumer groups in the Kafka cluster",
        List.of(),
        List.of(),
        clusterState.getConsumerGroupsStates().size()
    );

    clusterState.getConsumerGroupsStates().forEach((groupName, state) -> {
      registry.gauge(
          "group_state",
          "State of the consumer group, value = ordinal of org.apache.kafka.common.ConsumerGroupState",
          List.of(GROUP_TAG),
          List.of(groupName),
          state.description().state().ordinal()
      );
      registry.gauge(
          "group_member_count",
          "Number of member assignments in the consumer group.",
          List.of(GROUP_TAG),
          List.of(groupName),
          state.description().members().size()
      );
      registry.gauge(
          "group_host_count",
          "Number of distinct hosts in the consumer group.",
          List.of(GROUP_TAG),
          List.of(groupName),
          state.description().members().stream().map(MemberDescription::host).distinct().count()
      );

      state.committedOffsets().forEach((tp, committedOffset) -> {
        registry.gauge(
            "kafka_consumergroup_current_offset",
            "Current Offset of a ConsumerGroup at Topic/Partition",
            List.of("consumergroup", TOPIC_TAG, PARTITION_TAG),
            List.of(groupName, tp.topic(), String.valueOf(tp.partition())),
            committedOffset
        );

        Optional.ofNullable(clusterState.getTopicStates().get(tp.topic()))
            .flatMap(s -> Optional.ofNullable(s.endOffsets().get(tp.partition())))
            .ifPresent(endOffset ->
                registry.gauge(
                    "kafka_consumergroup_lag",
                    "Current Approximate Lag of a ConsumerGroup at Topic/Partition",
                    List.of("consumergroup", TOPIC_TAG, PARTITION_TAG),
                    List.of(groupName, tp.topic(), String.valueOf(tp.partition())),
                    endOffset - committedOffset
                ));

      });
    });
  }

  private static void fillKafkaConnectMetrics(MetricsRegistry registry,
                                              @Nullable Collection<KafkaConnectState> connectStates) {
    if (connectStates == null || connectStates.isEmpty()) {
      return;
    }

    registry.gauge(
        "kafka_connect_count",
        "Number of configured Kafka Connect instances",
        List.of(),
        List.of(),
        connectStates.size()
    );

    for (KafkaConnectState connectState : connectStates) {
      String connectName = connectState.getName();
      String version = Optional.ofNullable(connectState.getVersion())
          .filter(v -> !v.isBlank())
          .orElse("Unknown");

      registry.gauge(
          "kafka_connect_up",
          "Whether scraping the Kafka Connect instance succeeded (1 = up, 0 = down)",
          List.of(CONNECT_TAG),
          List.of(connectName),
          connectState.isUp() ? 1 : 0
      );

      registry.gauge(
          "kafka_connect_metadata",
          "Metadata information about the Kafka Connect instance",
          List.of(CONNECT_TAG, VERSION_TAG),
          List.of(connectName, version),
          1
      );

      if (!connectState.isUp()) {
        continue;
      }

      if (connectState.getScrapeFinishedAt() != null) {
        registry.gauge(
            "kafka_connect_last_scrape_timestamp_seconds",
            "Unix timestamp in seconds when the Kafka Connect instance was last scraped",
            List.of(CONNECT_TAG),
            List.of(connectName),
            connectState.getScrapeFinishedAt().toEpochMilli() / 1000.0
        );
      }

      List<KafkaConnectState.ConnectorState> connectors =
          Optional.ofNullable(connectState.getConnectors()).orElse(List.of());
      int connectorsCount = connectors.size();
      int failedConnectorsCount = 0;
      int totalTasksCount = 0;
      int failedTasksCount = 0;

      for (KafkaConnectState.ConnectorState connector : connectors) {
        String connectorName = connector.name();
        String connectorType = Optional.ofNullable(connector.connectorType())
            .map(ConnectorTypeDTO::getValue)
            .orElse(ConnectorTypeDTO.UNKNOWN.getValue());
        List<KafkaConnectState.TaskState> tasks = Optional.ofNullable(connector.tasks()).orElse(List.of());

        int connectorTasksCount = tasks.size();
        int connectorFailedTasks = 0;
        int connectorRunningTasks = 0;
        for (KafkaConnectState.TaskState task : tasks) {
          ConnectorTaskStatusDTO taskState = Optional.ofNullable(task.state())
              .orElse(ConnectorTaskStatusDTO.UNASSIGNED);
          if (taskState == ConnectorTaskStatusDTO.FAILED) {
            connectorFailedTasks++;
          } else if (taskState == ConnectorTaskStatusDTO.RUNNING) {
            connectorRunningTasks++;
          }

          String taskId = String.valueOf(task.id());
          String taskWorkerId = Optional.ofNullable(task.workerId()).orElse("");

          for (ConnectorTaskStatusDTO possibleState : ConnectorTaskStatusDTO.values()) {
            registry.gauge(
                "kafka_connect_task_status",
                "Current state of the Kafka Connect task (1 for active state, 0 for inactive states)",
                List.of(CONNECT_TAG, CONNECTOR_TAG, TYPE_TAG, TASK_ID_TAG, STATE_TAG),
                List.of(connectName, connectorName, connectorType, taskId, possibleState.getValue()),
                possibleState == taskState ? 1 : 0
            );
          }

          registry.gauge(
              "kafka_connect_task_failed",
              "Whether the Kafka Connect task is in FAILED state (1 = failed, 0 = not failed)",
              List.of(CONNECT_TAG, CONNECTOR_TAG, TYPE_TAG, TASK_ID_TAG),
              List.of(connectName, connectorName, connectorType, taskId),
              taskState == ConnectorTaskStatusDTO.FAILED ? 1 : 0
          );

          registry.gauge(
              "kafka_connect_task_metadata",
              "Metadata information about the Kafka Connect task",
              List.of(CONNECT_TAG, CONNECTOR_TAG, TYPE_TAG, TASK_ID_TAG, WORKER_ID_TAG),
              List.of(connectName, connectorName, connectorType, taskId, taskWorkerId),
              1
          );
        }

        ConnectorStateDTO rawState = Optional.ofNullable(connector.status())
            .map(ConnectorStatusDTO::getState)
            .orElse(ConnectorStateDTO.UNASSIGNED);
        ConnectorStateDTO effectiveState =
            (rawState == ConnectorStateDTO.RUNNING && connectorFailedTasks > 0)
                ? ConnectorStateDTO.TASK_FAILED
                : rawState;
        boolean connectorFailed = effectiveState == ConnectorStateDTO.FAILED
            || effectiveState == ConnectorStateDTO.TASK_FAILED;

        if (connectorFailed) {
          failedConnectorsCount++;
        }
        totalTasksCount += connectorTasksCount;
        failedTasksCount += connectorFailedTasks;

        for (ConnectorStateDTO possibleState : ConnectorStateDTO.values()) {
          registry.gauge(
              "kafka_connect_connector_status",
              "Current state of the Kafka Connect connector (1 for active state, 0 for inactive states)",
              List.of(CONNECT_TAG, CONNECTOR_TAG, TYPE_TAG, STATE_TAG),
              List.of(connectName, connectorName, connectorType, possibleState.getValue()),
              possibleState == effectiveState ? 1 : 0
          );
        }

        registry.gauge(
            "kafka_connect_connector_failed",
            "Whether the Kafka Connect connector or any task is failed (1 = failed, 0 = ok)",
            List.of(CONNECT_TAG, CONNECTOR_TAG, TYPE_TAG),
            List.of(connectName, connectorName, connectorType),
            connectorFailed ? 1 : 0
        );

        registry.gauge(
            "kafka_connect_connector_tasks_count",
            "Number of tasks for the Kafka Connect connector",
            List.of(CONNECT_TAG, CONNECTOR_TAG, TYPE_TAG),
            List.of(connectName, connectorName, connectorType),
            connectorTasksCount
        );

        registry.gauge(
            "kafka_connect_connector_tasks_failed_count",
            "Number of failed tasks for the Kafka Connect connector",
            List.of(CONNECT_TAG, CONNECTOR_TAG, TYPE_TAG),
            List.of(connectName, connectorName, connectorType),
            connectorFailedTasks
        );

        registry.gauge(
            "kafka_connect_connector_tasks_running_count",
            "Number of running tasks for the Kafka Connect connector",
            List.of(CONNECT_TAG, CONNECTOR_TAG, TYPE_TAG),
            List.of(connectName, connectorName, connectorType),
            connectorRunningTasks
        );

        int topicsCount = Optional.ofNullable(connector.topics()).map(List::size).orElse(0);
        registry.gauge(
            "kafka_connect_connector_topics_count",
            "Number of topics associated with the Kafka Connect connector",
            List.of(CONNECT_TAG, CONNECTOR_TAG, TYPE_TAG),
            List.of(connectName, connectorName, connectorType),
            topicsCount
        );

        String connectorWorkerId = Optional.ofNullable(connector.status())
            .map(ConnectorStatusDTO::getWorkerId)
            .orElse("");
        registry.gauge(
            "kafka_connect_connector_metadata",
            "Metadata information about the Kafka Connect connector",
            List.of(CONNECT_TAG, CONNECTOR_TAG, TYPE_TAG, WORKER_ID_TAG),
            List.of(connectName, connectorName, connectorType, connectorWorkerId),
            1
        );
      }

      registry.gauge(
          "kafka_connect_connectors_count",
          "Number of connectors in the Kafka Connect instance",
          List.of(CONNECT_TAG),
          List.of(connectName),
          connectorsCount
      );

      registry.gauge(
          "kafka_connect_failed_connectors_count",
          "Number of connectors in FAILED or TASK_FAILED state in the Kafka Connect instance",
          List.of(CONNECT_TAG),
          List.of(connectName),
          failedConnectorsCount
      );

      registry.gauge(
          "kafka_connect_tasks_count",
          "Total number of tasks across all connectors in the Kafka Connect instance",
          List.of(CONNECT_TAG),
          List.of(connectName),
          totalTasksCount
      );

      registry.gauge(
          "kafka_connect_failed_tasks_count",
          "Total number of failed tasks across all connectors in the Kafka Connect instance",
          List.of(CONNECT_TAG),
          List.of(connectName),
          failedTasksCount
      );
    }
  }
}
