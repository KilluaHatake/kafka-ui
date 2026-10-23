package io.kafbat.ui.service.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FailureThrottlingTrackerTest {

  private FailureThrottlingTracker tracker;

  @BeforeEach
  void setUp() {
    tracker = new FailureThrottlingTracker();
  }

  @Test
  void alertsImmediatelyOnNewFailure() {
    FailureKey key = new FailureKey("cluster1", "connect1", "my-connector", "Connector");
    assertThat(tracker.shouldAlert(key, 15)).isTrue();
  }

  @Test
  void suppressesAlertWithinThrottlingWindow() {
    FailureKey key = new FailureKey("cluster1", "connect1", "my-connector", "Connector");
    tracker.recordAlertSent(key);

    assertThat(tracker.shouldAlert(key, 15)).isFalse();
  }

  @Test
  void clearsResolvedFailuresForTargetCluster() {
    FailureKey key1 = new FailureKey("cluster1", "connect1", "connector-1", "Connector");
    FailureKey key2 = new FailureKey("cluster1", "connect1", "connector-2", "Task #0");
    FailureKey key3 = new FailureKey("cluster2", "connect2", "connector-3", "Connector");

    tracker.recordAlertSent(key1);
    tracker.recordAlertSent(key2);
    tracker.recordAlertSent(key3);

    // connector-1 recovered, connector-2 still failing
    tracker.clearResolved("cluster1", Set.of(key2));

    // key1 was resolved, so next time it fails it should alert immediately
    assertThat(tracker.shouldAlert(key1, 15)).isTrue();
    // key2 is still in throttle window
    assertThat(tracker.shouldAlert(key2, 15)).isFalse();
    // key3 belongs to cluster2, so untouched
    assertThat(tracker.shouldAlert(key3, 15)).isFalse();
  }
}
