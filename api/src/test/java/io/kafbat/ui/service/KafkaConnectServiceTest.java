package io.kafbat.ui.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.kafbat.ui.config.ClustersProperties;
import io.kafbat.ui.connect.api.KafkaConnectClientApi;
import io.kafbat.ui.connect.model.ClusterInfo;
import io.kafbat.ui.connect.model.Connector;
import io.kafbat.ui.connect.model.ConnectorStatus;
import io.kafbat.ui.connect.model.ConnectorStatusConnector;
import io.kafbat.ui.connect.model.ConnectorTopics;
import io.kafbat.ui.connect.model.ExpandedConnector;
import io.kafbat.ui.connect.model.TaskStatus;
import io.kafbat.ui.mapper.KafkaConnectMapperImpl;
import io.kafbat.ui.model.ConnectorStateDTO;
import io.kafbat.ui.model.ConnectorTaskStatusDTO;
import io.kafbat.ui.model.KafkaCluster;
import io.kafbat.ui.model.Statistics;
import io.kafbat.ui.service.metrics.scrape.KafkaConnectState;
import io.kafbat.ui.util.ReactiveFailover;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class KafkaConnectServiceTest {

  private final StatisticsCache statisticsCache = mock(StatisticsCache.class);
  private final KafkaConfigSanitizer sanitizer = mock(KafkaConfigSanitizer.class);
  private final ClustersProperties clustersProperties = new ClustersProperties();
  private final KafkaConnectService service = new KafkaConnectService(
      new KafkaConnectMapperImpl(),
      sanitizer,
      clustersProperties,
      statisticsCache
  );

  @Test
  void scrapeAllConnectsCapturesTasksWhenUpAndMarksDownOnFailure() {
    KafkaConnectClientApi client = mock(KafkaConnectClientApi.class);
    var failover = ReactiveFailover.create(
        List.of(client),
        _ -> true,
        "connect unavailable",
        Duration.ZERO
    );

    var connectCfg = ClustersProperties.ConnectCluster.builder()
        .name("connect-main")
        .address("http://localhost:8083")
        .build();
    var clusterProps = new ClustersProperties.Cluster();
    clusterProps.setName("local");
    clusterProps.setKafkaConnect(List.of(connectCfg));

    KafkaCluster cluster = KafkaCluster.builder()
        .name("local")
        .originalProperties(clusterProps)
        .connectsClients(Map.of("connect-main", failover))
        .connectsConfigs(Map.of("connect-main", connectCfg))
        .build();

    when(statisticsCache.get(cluster)).thenReturn(Statistics.empty());
    when(client.getClusterInfo()).thenReturn(Mono.just(new ClusterInfo().version("7.6.0")));
    when(client.getConnectors(any(), anyList())).thenReturn(Mono.just(Map.of(
        "sink-1",
        new ExpandedConnector()
            .info(new Connector().name("sink-1").type(Connector.TypeEnum.SINK).config(Map.of()))
            .status(new ConnectorStatus()
                .name("sink-1")
                .connector(new ConnectorStatusConnector()
                    .state(ConnectorStatusConnector.StateEnum.RUNNING)
                    .workerId("w1:8083"))
                .tasks(List.of(
                    new TaskStatus().id(0).state(TaskStatus.StateEnum.FAILED).workerId("w1:8083")
                )))
    )));
    when(client.getConnectorTopics("sink-1"))
        .thenReturn(Mono.just(Map.of("sink-1", new ConnectorTopics().topics(List.of("orders")))));

    StepVerifier.create(service.scrapeAllConnects(cluster))
        .assertNext(state -> {
          assertThat(state.isUp()).isTrue();
          assertThat(state.getName()).isEqualTo("connect-main");
          assertThat(state.getVersion()).isEqualTo("7.6.0");
          assertThat(state.getConnectors()).hasSize(1);
          var connector = state.getConnectors().getFirst();
          assertThat(connector.status().getState()).isEqualTo(ConnectorStateDTO.RUNNING);
          assertThat(connector.tasks()).containsExactly(
              new KafkaConnectState.TaskState(0, ConnectorTaskStatusDTO.FAILED, "w1:8083")
          );
        })
        .verifyComplete();

    // Subsequent failure while cached statistics exist should still mark connect down (up = false)
    when(client.getConnectors(any(), anyList()))
        .thenReturn(Mono.error(new RuntimeException("connection refused")));

    StepVerifier.create(service.scrapeAllConnects(cluster))
        .assertNext(state -> {
          assertThat(state.isUp()).isFalse();
          assertThat(state.getName()).isEqualTo("connect-main");
          assertThat(state.getConnectors()).isEmpty();
        })
        .verifyComplete();
  }

  @Test
  void getClusterInfoRefreshesWhenCachedConnectWasDown() {
    KafkaConnectClientApi client = mock(KafkaConnectClientApi.class);
    var failover = ReactiveFailover.createNoop(client);
    KafkaCluster cluster = KafkaCluster.builder()
        .name("local")
        .connectsClients(Map.of("connect-main", failover))
        .build();

    Statistics downStats = Statistics.empty().toBuilder()
        .connectStates(Map.of("connect-main", KafkaConnectState.builder()
            .name("connect-main")
            .version("Unknown")
            .up(false)
            .connectors(List.of())
            .build()))
        .build();

    when(statisticsCache.get(cluster)).thenReturn(downStats);
    when(client.getClusterInfo()).thenReturn(Mono.just(new ClusterInfo().version("7.7.0")));

    StepVerifier.create(service.getClusterInfo(cluster, "connect-main"))
        .assertNext(info -> assertThat(info.getVersion()).isEqualTo("7.7.0"))
        .verifyComplete();
    verify(client, times(1)).getClusterInfo();
  }
}
