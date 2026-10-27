package io.kafbat.ui.service.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ConnectAlertPayloadTest {

  private static final String FULL_DEBEZIUM_TRACE =
      "io.debezium.DebeziumException: Unable to get last available log position\n"
          + "\tat io.debezium.connector.sqlserver.SqlServerConnection."
          + "validateLogPosition(SqlServerConnection.java:936)\n"
          + "\tat io.debezium.connector.common.BaseSourceTask.isLogPositionAvailable(BaseSourceTask.java:179)\n"
          + "\tat io.debezium.connector.common.BaseSourceTask.validateSchemaHistory(BaseSourceTask.java:149)\n"
          + "\tat io.debezium.connector.sqlserver.SqlServerConnectorTask.start(SqlServerConnectorTask.java:137)\n"
          + "\tat io.debezium.connector.common.BaseSourceTask.start(BaseSourceTask.java:286)\n"
          + "\tat org.apache.kafka.connect.runtime.AbstractWorkerSourceTask.start(Task.java:288)\n"
          + "\tat org.apache.kafka.connect.runtime.WorkerTask.doStart(WorkerTask.java:191)\n"
          + "\tat org.apache.kafka.connect.runtime.WorkerTask.doRun(WorkerTask.java:242)\n"
          + "\tat org.apache.kafka.connect.runtime.WorkerTask.run(WorkerTask.java:298)\n"
          + "\tat org.apache.kafka.connect.runtime.AbstractWorkerSourceTask.run(Task.java:83)\n"
          + "\tat org.apache.kafka.connect.runtime.isolation.Plugins.lambda(Plugins.java:252)\n"
          + "\tat java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:545)\n"
          + "\tat java.base/java.util.concurrent.FutureTask.run(FutureTask.java:328)\n"
          + "\tat java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1090)\n"
          + "\tat java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:614)\n"
          + "\tat java.base/java.lang.Thread.run(Thread.java:1474)\n"
          + "Caused by: com.microsoft.sqlserver.jdbc.SQLServerException: "
          + "Login failed for user 'automq_reader'. ClientConnectionId:eb2cacae-59ca-4be7-8bba-55baa950206d\n"
          + "\tat com.microsoft.sqlserver.jdbc.SQLServerException.makeFromDatabaseError(SQLServerException.java:259)\n"
          + "\tat com.microsoft.sqlserver.jdbc.TDSTokenHandler.onEOF(tdsparser.java:304)\n"
          + "\tat com.microsoft.sqlserver.jdbc.TDSParser.parse(tdsparser.java:137)\n"
          + "\tat com.microsoft.sqlserver.jdbc.TDSParser.parse(tdsparser.java:42)\n"
          + "\tat com.microsoft.sqlserver.jdbc.SQLServerConnection.sendLogon(SQLServerConnection.java:6612)\n"
          + "\tat com.microsoft.sqlserver.jdbc.SQLServerConnection.logon(SQLServerConnection.java:5154)\n"
          + "\tat com.microsoft.sqlserver.jdbc.SQLServerConnection.access$300(SQLServerConnection.java:94)\n"
          + "\tat com.microsoft.sqlserver.jdbc.SQLServerConnection$LogonCommand.doExecute(Command.java:5087)\n"
          + "\tat com.microsoft.sqlserver.jdbc.TDSCommand.execute(IOBuffer.java:7675)\n"
          + "\tat com.microsoft.sqlserver.jdbc.SQLServerConnection.executeCommand(SQLServerConnection.java:4137)\n"
          + "\tat com.microsoft.sqlserver.jdbc.SQLServerConnection.connectHelper(SQLServerConnection.java:3583)\n"
          + "\tat com.microsoft.sqlserver.jdbc.SQLServerConnection.login(SQLServerConnection.java:3172)\n"
          + "\tat com.microsoft.sqlserver.jdbc.SQLServerConnection.connectInternal(SQLServerConnection.java:3014)\n"
          + "\tat com.microsoft.sqlserver.jdbc.SQLServerConnection.connect(SQLServerConnection.java:1836)\n"
          + "\tat com.microsoft.sqlserver.jdbc.SQLServerDriver.connect(SQLServerDriver.java:1246)\n"
          + "\tat io.debezium.jdbc.JdbcConnection.lambda$patternBasedFactory$1(JdbcConnection.java:252)\n"
          + "\tat io.debezium.jdbc.JdbcConnection$ConnectionFactoryDecorator.connect(JdbcConnection.java:138)\n"
          + "\tat io.debezium.jdbc.JdbcConnection.establishConnection(JdbcConnection.java:942)\n"
          + "\tat io.debezium.jdbc.JdbcConnection.connection(JdbcConnection.java:926)\n"
          + "\tat io.debezium.connector.sqlserver.SqlServerConnection.connection(SqlServerConnection.java:336)\n"
          + "\tat io.debezium.jdbc.JdbcConnection.connection(JdbcConnection.java:920)\n"
          + "\tat io.debezium.jdbc.JdbcConnection.createStatementFromFactory(JdbcConnection.java:1597)\n"
          + "\tat io.debezium.jdbc.JdbcConnection.queryAndMap(JdbcConnection.java:668)\n"
          + "\tat io.debezium.jdbc.JdbcConnection.queryAndMap(JdbcConnection.java:543)\n"
          + "\tat io.debezium.connector.sqlserver.SqlServerConnection.single(SqlServerConnection.java:941)\n"
          + "\tat io.debezium.connector.sqlserver.SqlServerConnection.validate(SqlServerConnection.java:925)\n"
          + "\t... 15 more";

  @Test
  void extractsDeepestRootCauseFromChainedExceptions() {
    ConnectAlertPayload payload = new ConnectAlertPayload(
        "DEV_UAT_AUTOMQ",
        "UAT_LLS_KAFKA_CONNECT",
        "sqlserver-source",
        "Task #0",
        Instant.now(),
        FULL_DEBEZIUM_TRACE
    );

    assertThat(payload.getRootCause()).isEqualTo(
        "com.microsoft.sqlserver.jdbc.SQLServerException: Login failed for user 'automq_reader'. "
            + "ClientConnectionId:eb2cacae-59ca-4be7-8bba-55baa950206d"
    );
  }

  @Test
  void extractsRootCauseWhenNoCausedByPresent() {
    ConnectAlertPayload payload = new ConnectAlertPayload(
        "DEV_UAT_AUTOMQ",
        "UAT_LLS_KAFKA_CONNECT",
        "my-connector",
        "Connector",
        Instant.now(),
        "org.apache.kafka.common.errors.TimeoutException: Failed to connect to broker\n\tat ...\n\tat ..."
    );

    assertThat(payload.getRootCause()).isEqualTo(
        "org.apache.kafka.common.errors.TimeoutException: Failed to connect to broker"
    );
  }

  @Test
  void handlesNullOrBlankTraceGracefully() {
    ConnectAlertPayload nullPayload = new ConnectAlertPayload(
        "c", "cc", "conn", "comp", Instant.now(), null
    );
    assertThat(nullPayload.getRootCause()).isNull();
    assertThat(nullPayload.getTruncatedTrace(1000)).isEqualTo("No error trace available");

    ConnectAlertPayload blankPayload = new ConnectAlertPayload(
        "c", "cc", "conn", "comp", Instant.now(), "   "
    );
    assertThat(blankPayload.getRootCause()).isNull();
    assertThat(blankPayload.getTruncatedTrace(1000)).isEqualTo("No error trace available");
  }

  @Test
  void preservesEntireTraceWhenUnderBudget() {
    ConnectAlertPayload payload = new ConnectAlertPayload(
        "c", "cc", "conn", "comp", Instant.now(), FULL_DEBEZIUM_TRACE
    );

    assertThat(payload.getTruncatedTrace(FULL_DEBEZIUM_TRACE.length() + 100))
        .isEqualTo(FULL_DEBEZIUM_TRACE);
  }

  @Test
  void smartTruncationPreservesRootCauseWhenOverBudget() {
    ConnectAlertPayload payload = new ConnectAlertPayload(
        "c", "cc", "conn", "comp", Instant.now(), FULL_DEBEZIUM_TRACE
    );

    String truncated = payload.getTruncatedTrace(1000);
    assertThat(truncated).contains("io.debezium.DebeziumException");
    assertThat(truncated).contains("... [intermediate stack frames omitted] ...");
    assertThat(truncated)
        .contains("Caused by: com.microsoft.sqlserver.jdbc.SQLServerException")
        .contains("Login failed for user 'automq_reader'");
    assertThat(truncated.length()).isLessThanOrEqualTo(1050);
  }
}
