package io.kafbat.ui.service.connect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.kafbat.ui.config.ClustersProperties;
import io.kafbat.ui.config.ConnectAlertProperties;
import io.kafbat.ui.connect.model.ConnectorStatus;
import io.kafbat.ui.connect.model.ConnectorStatusConnector;
import io.kafbat.ui.connect.model.ExpandedConnector;
import io.kafbat.ui.connect.model.TaskStatus;
import io.kafbat.ui.model.KafkaCluster;
import io.kafbat.ui.service.ClustersStorage;
import io.kafbat.ui.service.KafkaConnectService;
import io.kafbat.ui.service.notification.ConnectAlertPayload;
import io.kafbat.ui.service.notification.FailureKey;
import io.kafbat.ui.service.notification.FailureThrottlingTracker;
import io.kafbat.ui.service.notification.NotificationService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

class ConnectErrorMonitoringSchedulerTest {

  private ClustersStorage clustersStorage;
  private KafkaConnectService kafkaConnectService;
  private ConnectAlertProperties alertProperties;
  private NotificationService notificationService;
  private FailureThrottlingTracker throttlingTracker;
  private ConnectErrorMonitoringScheduler scheduler;

  private KafkaCluster testCluster;

  @BeforeEach
  void setUp() {
    clustersStorage = mock(ClustersStorage.class);
    kafkaConnectService = mock(KafkaConnectService.class);
    notificationService = mock(NotificationService.class);
    throttlingTracker = new FailureThrottlingTracker();

    alertProperties = new ConnectAlertProperties();
    alertProperties.setEnabled(true);
    alertProperties.setThrottleIntervalMinutes(15);

    ClustersProperties.Cluster clusterProps = new ClustersProperties.Cluster();
    clusterProps.setName("dev-cluster");
    ClustersProperties.ConnectCluster connectCluster = new ClustersProperties.ConnectCluster();
    connectCluster.setName("connect-local");
    clusterProps.setKafkaConnect(List.of(connectCluster));

    testCluster = KafkaCluster.builder()
        .name("dev-cluster")
        .originalProperties(clusterProps)
        .build();

    when(clustersStorage.getKafkaClusters()).thenReturn(List.of(testCluster));
    when(notificationService.sendConnectAlert(any())).thenReturn(Mono.empty());

    scheduler = new ConnectErrorMonitoringScheduler(
        clustersStorage,
        kafkaConnectService,
        alertProperties,
        notificationService,
        throttlingTracker
    );
  }

  @Test
  void doesNothingWhenAlertsDisabled() {
    alertProperties.setEnabled(false);

    scheduler.monitorConnectErrors();

    verify(kafkaConnectService, never()).getConnectorsWithErrorsSuppress(any(), any());
  }

  @Test
  void detectsFailedConnectorAndDispatchesAlert() {
    ConnectorStatus status = new ConnectorStatus();
    ConnectorStatusConnector conn = new ConnectorStatusConnector();
    conn.setState(ConnectorStatusConnector.StateEnum.FAILED);
    conn.setTrace("org.apache.kafka.connect.errors.ConnectException: Unable to connect to database");
    status.setConnector(conn);

    ExpandedConnector expanded = new ExpandedConnector();
    expanded.setStatus(status);

    when(kafkaConnectService.getConnectorsWithErrorsSuppress(testCluster, "connect-local"))
        .thenReturn(Mono.just(Map.of("postgres-source", expanded)));

    scheduler.checkClusterConnects(testCluster).block();

    ArgumentCaptor<ConnectAlertPayload> captor = ArgumentCaptor.forClass(ConnectAlertPayload.class);
    verify(notificationService, times(1)).sendConnectAlert(captor.capture());

    ConnectAlertPayload payload = captor.getValue();
    assertThat(payload.clusterName()).isEqualTo("dev-cluster");
    assertThat(payload.connectName()).isEqualTo("connect-local");
    assertThat(payload.connectorName()).isEqualTo("postgres-source");
    assertThat(payload.component()).isEqualTo("Connector");
    assertThat(payload.errorTrace()).contains("ConnectException");
  }

  @Test
  void detectsFailedTaskAndDispatchesAlert() {
    ConnectorStatus status = new ConnectorStatus();
    ConnectorStatusConnector conn = new ConnectorStatusConnector();
    conn.setState(ConnectorStatusConnector.StateEnum.RUNNING);
    status.setConnector(conn);

    TaskStatus task0 = new TaskStatus();
    task0.setId(0);
    task0.setState(TaskStatus.StateEnum.FAILED);
    task0.setTrace("java.lang.NullPointerException at com.example.Transformer.transform");
    status.setTasks(List.of(task0));

    ExpandedConnector expanded = new ExpandedConnector();
    expanded.setStatus(status);

    when(kafkaConnectService.getConnectorsWithErrorsSuppress(testCluster, "connect-local"))
        .thenReturn(Mono.just(Map.of("jdbc-sink", expanded)));

    scheduler.checkClusterConnects(testCluster).block();

    ArgumentCaptor<ConnectAlertPayload> captor = ArgumentCaptor.forClass(ConnectAlertPayload.class);
    verify(notificationService, times(1)).sendConnectAlert(captor.capture());

    ConnectAlertPayload payload = captor.getValue();
    assertThat(payload.connectorName()).isEqualTo("jdbc-sink");
    assertThat(payload.component()).isEqualTo("Task #0");
    assertThat(payload.errorTrace()).contains("NullPointerException");
  }

  @Test
  void throttlesRepeatedAlertsForSameFailure() {
    ConnectorStatus status = new ConnectorStatus();
    ConnectorStatusConnector conn = new ConnectorStatusConnector();
    conn.setState(ConnectorStatusConnector.StateEnum.FAILED);
    conn.setTrace("Connection timeout");
    status.setConnector(conn);

    ExpandedConnector expanded = new ExpandedConnector();
    expanded.setStatus(status);

    when(kafkaConnectService.getConnectorsWithErrorsSuppress(testCluster, "connect-local"))
        .thenReturn(Mono.just(Map.of("s3-sink", expanded)));

    // First cycle
    scheduler.checkClusterConnects(testCluster).block();
    verify(notificationService, times(1)).sendConnectAlert(any());

    // Second cycle (immediate) -> throttled
    scheduler.checkClusterConnects(testCluster).block();
    verify(notificationService, times(1)).sendConnectAlert(any());
  }

  @Test
  void clearsResolvedFailureWhenConnectorRecovers() {
    FailureKey key = new FailureKey("dev-cluster", "connect-local", "restored-connector", "Connector");
    throttlingTracker.recordAlertSent(key);

    ConnectorStatus status = new ConnectorStatus();
    ConnectorStatusConnector conn = new ConnectorStatusConnector();
    conn.setState(ConnectorStatusConnector.StateEnum.RUNNING);
    status.setConnector(conn);

    ExpandedConnector expanded = new ExpandedConnector();
    expanded.setStatus(status);

    when(kafkaConnectService.getConnectorsWithErrorsSuppress(testCluster, "connect-local"))
        .thenReturn(Mono.just(Map.of("restored-connector", expanded)));

    scheduler.checkClusterConnects(testCluster).block();

    // After recovery, tracker should be cleared for this key
    assertThat(throttlingTracker.shouldAlert(key, 15)).isTrue();
  }
}
