package io.kafbat.ui.service.notification;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FailureThrottlingTracker {

  private final Map<FailureKey, Instant> lastAlertedMap = new ConcurrentHashMap<>();

  public boolean shouldAlert(FailureKey key, int throttleIntervalMinutes) {
    Instant lastAlerted = lastAlertedMap.get(key);
    if (lastAlerted == null) {
      return true;
    }
    long elapsedMinutes = Duration.between(lastAlerted, Instant.now()).toMinutes();
    return elapsedMinutes >= throttleIntervalMinutes;
  }

  public void recordAlertSent(FailureKey key) {
    lastAlertedMap.put(key, Instant.now());
  }

  public void clearResolved(String clusterName, Set<FailureKey> activeFailuresForCluster) {
    lastAlertedMap.keySet().removeIf(key ->
        key.clusterName().equals(clusterName) && !activeFailuresForCluster.contains(key)
    );
  }

  public void clear(FailureKey key) {
    lastAlertedMap.remove(key);
  }

  public void clearAll() {
    lastAlertedMap.clear();
  }

  public Map<FailureKey, Instant> getActiveTracking() {
    return Map.copyOf(lastAlertedMap);
  }
}
