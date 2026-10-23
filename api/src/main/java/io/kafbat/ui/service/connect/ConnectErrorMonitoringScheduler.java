package io.kafbat.ui.service.connect;

import io.kafbat.ui.config.ClustersProperties;
import io.kafbat.ui.config.ConnectAlertProperties;
import io.kafbat.ui.connect.model.ConnectorStatus;
import io.kafbat.ui.connect.model.ExpandedConnector;
import io.kafbat.ui.connect.model.TaskStatus;
import io.kafbat.ui.model.KafkaCluster;
import io.kafbat.ui.service.ClustersStorage;
import io.kafbat.ui.service.KafkaConnectService;
import io.kafbat.ui.service.notification.ConnectAlertPayload;
import io.kafbat.ui.service.notification.FailureKey;
import io.kafbat.ui.service.notification.FailureThrottlingTracker;
import io.kafbat.ui.service.notification.NotificationService;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConnectErrorMonitoringScheduler {

  private static final String FAILED_STATE = "FAILED";

  private final ClustersStorage clustersStorage;
  private final KafkaConnectService kafkaConnectService;
  private final ConnectAlertProperties alertProperties;
  private final NotificationService notificationService;
  private final FailureThrottlingTracker throttlingTracker;

  @Scheduled(fixedRateString = "${kafka.connect-alerts.polling-interval-ms:30000}")
  public void monitorConnectErrors() {
    if (!alertProperties.isEnabled()) {
      return;
    }

    Flux.fromIterable(clustersStorage.getKafkaClusters())
        .flatMap(this::checkClusterConnects)
        .then()
        .doOnError(th -> log.error("Error during Kafka Connect failure monitoring", th))
        .onErrorResume(th -> Mono.empty())
        .subscribe();
  }

  Mono<Void> checkClusterConnects(KafkaCluster cluster) {
    List<ClustersProperties.ConnectCluster> connectClusters =
        Optional.ofNullable(cluster.getOriginalProperties().getKafkaConnect()).orElse(List.of());

    if (connectClusters.isEmpty()) {
      return Mono.empty();
    }

    Set<FailureKey> activeFailuresForCluster = new HashSet<>();

    return Flux.fromIterable(connectClusters)
        .flatMap(connect -> checkConnectInstance(cluster, connect.getName(), activeFailuresForCluster))
        .then(Mono.fromRunnable(() ->
            throttlingTracker.clearResolved(cluster.getName(), activeFailuresForCluster)
        ));
  }

  Mono<Void> checkConnectInstance(KafkaCluster cluster, String connectName, Set<FailureKey> activeFailures) {
    return kafkaConnectService.getConnectorsWithErrorsSuppress(cluster, connectName)
        .flatMap(connectors -> processConnectors(cluster.getName(), connectName, connectors, activeFailures))
        .onErrorResume(th -> {
          log.warn("Failed to check connectors for connect cluster '{}' on '{}'", connectName, cluster.getName(), th);
          return Mono.empty();
        });
  }

  private Mono<Void> processConnectors(
      String clusterName,
      String connectName,
      Map<String, ExpandedConnector> connectors,
      Set<FailureKey> activeFailures
  ) {
    List<ConnectAlertPayload> alertsToSend = new java.util.ArrayList<>();

    for (Map.Entry<String, ExpandedConnector> entry : connectors.entrySet()) {
      String connectorName = entry.getKey();
      ExpandedConnector expanded = entry.getValue();

      if (expanded == null || expanded.getStatus() == null) {
        continue;
      }

      ConnectorStatus status = expanded.getStatus();

      // Check connector process status
      if (status.getConnector() != null && isFailed(status.getConnector().getState())) {
        FailureKey key = new FailureKey(clusterName, connectName, connectorName, "Connector");
        activeFailures.add(key);

        if (throttlingTracker.shouldAlert(key, alertProperties.getThrottleIntervalMinutes())) {
          alertsToSend.add(new ConnectAlertPayload(
              clusterName,
              connectName,
              connectorName,
              "Connector",
              Instant.now(),
              status.getConnector().getTrace()
          ));
          throttlingTracker.recordAlertSent(key);
        }
      }

      // Check individual tasks status
      if (status.getTasks() != null) {
        for (TaskStatus task : status.getTasks()) {
          if (task != null && isFailed(task.getState())) {
            String component = "Task #" + task.getId();
            FailureKey key = new FailureKey(clusterName, connectName, connectorName, component);
            activeFailures.add(key);

            if (throttlingTracker.shouldAlert(key, alertProperties.getThrottleIntervalMinutes())) {
              alertsToSend.add(new ConnectAlertPayload(
                  clusterName,
                  connectName,
                  connectorName,
                  component,
                  Instant.now(),
                  task.getTrace()
              ));
              throttlingTracker.recordAlertSent(key);
            }
          }
        }
      }
    }

    if (alertsToSend.isEmpty()) {
      return Mono.empty();
    }

    return Flux.fromIterable(alertsToSend)
        .flatMap(notificationService::sendConnectAlert)
        .then();
  }

  private boolean isFailed(Object state) {
    return state != null && FAILED_STATE.equalsIgnoreCase(state.toString());
  }
}
