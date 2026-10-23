package io.kafbat.ui.service.notification;

public record FailureKey(
    String clusterName,
    String connectName,
    String connectorName,
    String component
) {}
