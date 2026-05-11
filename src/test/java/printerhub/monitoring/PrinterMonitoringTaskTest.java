package printerhub.monitoring;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import printerhub.PrinterPort;
import printerhub.PrinterSnapshot;
import printerhub.PrinterState;
import printerhub.SerialIOMode;
import printerhub.job.JobState;
import printerhub.job.JobType;
import printerhub.job.PrintJob;
import printerhub.job.PrintJobService;
import printerhub.persistence.DatabaseInitializer;
import printerhub.persistence.MonitoringRules;
import printerhub.persistence.PrintJobStore;
import printerhub.persistence.PrinterEventStore;
import printerhub.persistence.PrinterSnapshotStore;
import printerhub.runtime.PrinterRuntimeNode;
import printerhub.runtime.PrinterRuntimeStateCache;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class PrinterMonitoringTaskTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearDatabaseProperty() {
        System.clearProperty("printerhub.databaseFile");
    }

    @Test
    void disabledNodeUpdatesCacheAndDoesNotPersistEvent() throws Exception {
        useDatabase("disabled.db");

        MutableClock clock = new MutableClock(Instant.parse("2026-04-29T10:00:00Z"));
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache(clock);

        PrinterRuntimeNode node = new PrinterRuntimeNode(
                "printer-1",
                "Printer 1",
                "SIM_PORT",
                "sim",
                new StubPrinterPort(),
                false);

        PrinterMonitoringTask task = new PrinterMonitoringTask(
                node,
                stateCache,
                new PrinterSnapshotStore(),
                new PrinterEventStore(),
                clock,
                "M105",
                () -> false,
                new MonitoringEventPolicy(clock, Duration.ofSeconds(60)),
                MonitoringRules.defaults());

        task.run();

        PrinterSnapshot snapshot = stateCache.findByPrinterId("printer-1").orElseThrow();
        assertEquals(PrinterState.DISCONNECTED, snapshot.state());
        assertEquals("Printer node is disabled.", snapshot.errorMessage());

        // assertEquals(1, countRows("printer_snapshots"));
        assertEquals(1, countRows("printer_snapshots"), dumpPrinterSnapshots());
        assertEquals(0, countRows("printer_events"));
    }

    @Test
    void successfulPollUpdatesSnapshotWithParsedTemperatures() throws Exception {
        useDatabase("success.db");

        MutableClock clock = new MutableClock(Instant.parse("2026-04-29T10:01:00Z"));
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache(clock);

        StubPrinterPort port = new StubPrinterPort();
        port.response = "ok T:52.50 /0.00 B:23.75 /0.00 @:0 B@:0";

        PrinterRuntimeNode node = new PrinterRuntimeNode(
                "printer-1",
                "Printer 1",
                "SIM_PORT",
                "sim",
                port,
                true);

        PrinterMonitoringTask task = new PrinterMonitoringTask(
                node,
                stateCache,
                new PrinterSnapshotStore(),
                new PrinterEventStore(),
                clock,
                "M105",
                () -> false,
                new MonitoringEventPolicy(clock, Duration.ofSeconds(60)),
                MonitoringRules.defaults());

        task.run();

        PrinterSnapshot snapshot = stateCache.findByPrinterId("printer-1").orElseThrow();
        assertEquals(PrinterState.HEATING, snapshot.state());
        assertEquals(52.50, snapshot.hotendTemperature());
        assertEquals(23.75, snapshot.bedTemperature());
        assertEquals("ok T:52.50 /0.00 B:23.75 /0.00 @:0 B@:0", snapshot.lastResponse());
        assertNull(snapshot.errorMessage());

        assertEquals(1, port.connectCalls);
        assertEquals(1, port.sendCommandCalls);
        assertEquals("M105", port.lastCommand);

        assertEquals(1, countRows("printer_snapshots"));
        assertEquals(0, countRows("printer_events"));
    }

    @Test
    void blankResponseBecomesTimeoutAndPersistsEvent() throws Exception {
        useDatabase("timeout.db");

        MutableClock clock = new MutableClock(Instant.parse("2026-04-29T10:02:00Z"));
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache(clock);

        StubPrinterPort port = new StubPrinterPort();
        port.response = "";

        PrinterRuntimeNode node = new PrinterRuntimeNode(
                "printer-1",
                "Printer 1",
                "SIM_PORT",
                "sim",
                port,
                true);

        PrinterMonitoringTask task = new PrinterMonitoringTask(
                node,
                stateCache,
                new PrinterSnapshotStore(),
                new PrinterEventStore(),
                clock,
                "M105",
                () -> false,
                new MonitoringEventPolicy(clock, Duration.ofSeconds(60)),
                MonitoringRules.defaults());

        task.run();

        PrinterSnapshot snapshot = stateCache.findByPrinterId("printer-1").orElseThrow();
        assertEquals(PrinterState.ERROR, snapshot.state());
        assertEquals("No response for command M105", snapshot.errorMessage());

        assertEquals(1, countRows("printer_events"));
        assertEquals("PRINTER_TIMEOUT", firstEventType());
        assertEquals("No response for command M105", firstEventMessage());
    }

    @Test
    void errorResponseBecomesErrorAndPersistsEvent() throws Exception {
        useDatabase("error-response.db");

        MutableClock clock = new MutableClock(Instant.parse("2026-04-29T10:03:00Z"));
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache(clock);

        StubPrinterPort port = new StubPrinterPort();
        port.response = "Error: heater failure";

        PrinterRuntimeNode node = new PrinterRuntimeNode(
                "printer-1",
                "Printer 1",
                "SIM_PORT",
                "sim",
                port,
                true);

        PrinterMonitoringTask task = new PrinterMonitoringTask(
                node,
                stateCache,
                new PrinterSnapshotStore(),
                new PrinterEventStore(),
                clock,
                "M105",
                () -> false,
                new MonitoringEventPolicy(clock, Duration.ofSeconds(60)),
                MonitoringRules.defaults());

        task.run();

        PrinterSnapshot snapshot = stateCache.findByPrinterId("printer-1").orElseThrow();
        assertEquals(PrinterState.ERROR, snapshot.state());

        // assertEquals(1, countRows("printer_events"));
        assertEquals(1, countRows("printer_events"), dumpPrinterEvents());
        assertEquals("PRINTER_ERROR", firstEventType());
        assertEquals("Printer returned error response.", firstEventMessage());
    }

    @Test
    void disconnectFailureBecomesDisconnectedEvent() throws Exception {
        useDatabase("disconnect.db");

        MutableClock clock = new MutableClock(Instant.parse("2026-04-29T10:04:00Z"));
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache(clock);

        StubPrinterPort port = new StubPrinterPort();
        port.connectException = new IllegalStateException("Simulated printer is disconnected: SIM_PORT");

        PrinterRuntimeNode node = new PrinterRuntimeNode(
                "printer-1",
                "Printer 1",
                "SIM_PORT",
                "sim",
                port,
                true);

        PrinterMonitoringTask task = new PrinterMonitoringTask(
                node,
                stateCache,
                new PrinterSnapshotStore(),
                new PrinterEventStore(),
                clock,
                "M105",
                () -> false,
                new MonitoringEventPolicy(clock, Duration.ofSeconds(60)),
                MonitoringRules.defaults());

        task.run();

        PrinterSnapshot snapshot = stateCache.findByPrinterId("printer-1").orElseThrow();
        assertEquals(PrinterState.ERROR, snapshot.state());
        assertEquals("Simulated printer is disconnected: SIM_PORT", snapshot.errorMessage());

        assertEquals(1, countRows("printer_events"));
        assertEquals("PRINTER_DISCONNECTED", firstEventType());
        assertEquals("Simulated printer is disconnected: SIM_PORT", firstEventMessage());
    }

    @Test
    void duplicateIdenticalFailureWithinDedupWindowIsSuppressed() throws Exception {
        useDatabase("dedup.db");

        MutableClock clock = new MutableClock(Instant.parse("2026-04-29T10:05:00Z"));
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache(clock);

        StubPrinterPort port = new StubPrinterPort();
        port.response = "";

        MonitoringEventPolicy eventPolicy = new MonitoringEventPolicy(clock, Duration.ofSeconds(60));

        PrinterRuntimeNode node = new PrinterRuntimeNode(
                "printer-1",
                "Printer 1",
                "SIM_PORT",
                "sim",
                port,
                true);

        PrinterMonitoringTask task = new PrinterMonitoringTask(
                node,
                stateCache,
                new PrinterSnapshotStore(),
                new PrinterEventStore(),
                clock,
                "M105",
                () -> false,
                eventPolicy,
                MonitoringRules.defaults());

        task.run();
        clock.setInstant(clock.instant().plusSeconds(10));
        task.run();

        assertEquals(1, countRows("printer_events"));
    }

    @Test
    void successClearsDedupStateSoLaterRepeatedFailurePersistsAgain() throws Exception {
        useDatabase("dedup-clear.db");

        MutableClock clock = new MutableClock(Instant.parse("2026-04-29T10:06:00Z"));
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache(clock);

        StubPrinterPort port = new StubPrinterPort();
        MonitoringEventPolicy eventPolicy = new MonitoringEventPolicy(clock, Duration.ofSeconds(60));

        PrinterRuntimeNode node = new PrinterRuntimeNode(
                "printer-1",
                "Printer 1",
                "SIM_PORT",
                "sim",
                port,
                true);

        PrinterMonitoringTask task = new PrinterMonitoringTask(
                node,
                stateCache,
                new PrinterSnapshotStore(),
                new PrinterEventStore(),
                clock,
                "M105",
                () -> false,
                eventPolicy,
                MonitoringRules.defaults());

        port.response = "";
        task.run();

        port.response = "ok T:21.80 /0.00 B:21.52 /0.00 @:0 B@:0";
        clock.setInstant(clock.instant().plusSeconds(5));
        task.run();

        port.response = "";
        clock.setInstant(clock.instant().plusSeconds(5));
        task.run();

        assertEquals(2, countRows("printer_events"));
    }

    @Test
    void shutdownInterruptionSuppressesMisleadingFailureEvent() throws Exception {
        useDatabase("shutdown.db");

        MutableClock clock = new MutableClock(Instant.parse("2026-04-29T10:07:00Z"));
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache(clock);
        AtomicBoolean shutdown = new AtomicBoolean(true);

        StubPrinterPort port = new StubPrinterPort();
        port.sendInterrupted = true;

        PrinterRuntimeNode node = new PrinterRuntimeNode(
                "printer-1",
                "Printer 1",
                "SIM_PORT",
                "sim",
                port,
                true);

        PrinterMonitoringTask task = new PrinterMonitoringTask(
                node,
                stateCache,
                new PrinterSnapshotStore(),
                new PrinterEventStore(),
                clock,
                "M105",
                shutdown::get,
                new MonitoringEventPolicy(clock, Duration.ofSeconds(60)),
                MonitoringRules.defaults());

        try {
            task.run();
            assertEquals(0, countRows("printer_events"));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void invalidDatabasePathDoesNotCrashTaskAndCacheStillUpdates() {
        System.setProperty("printerhub.databaseFile", tempDir.toString());

        MutableClock clock = new MutableClock(Instant.parse("2026-04-29T10:08:00Z"));
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache(clock);

        StubPrinterPort port = new StubPrinterPort();
        port.response = "";

        PrinterRuntimeNode node = new PrinterRuntimeNode(
                "printer-1",
                "Printer 1",
                "SIM_PORT",
                "sim",
                port,
                true);

        PrinterMonitoringTask task = new PrinterMonitoringTask(
                node,
                stateCache,
                new PrinterSnapshotStore(),
                new PrinterEventStore(),
                clock,
                "M105",
                () -> false,
                new MonitoringEventPolicy(clock, Duration.ofSeconds(60)),
                MonitoringRules.defaults());

        assertDoesNotThrow(task::run);
        assertTrue(stateCache.findByPrinterId("printer-1").isPresent());
    }

    @Test
    void printingToIdleTransitionCompletesRunningAutonomousPrintJob() throws Exception {
        useDatabase("autonomous-print-complete.db");

        MutableClock clock = new MutableClock(Instant.parse("2026-04-29T10:09:00Z"));
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache(clock);
        stateCache.update(
                "printer-1",
                PrinterSnapshot.fromResponse(
                        PrinterState.PRINTING,
                        21.0,
                        21.0,
                        "busy",
                        clock.instant().minusSeconds(5)));

        PrintJobStore jobStore = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        PrintJobService jobService = new PrintJobService(jobStore, eventStore, clock);
        PrintJob job = jobService.create(
                "Autonomous print",
                JobType.PRINT_FILE,
                "printer-1",
                null,
                "printer-sd-file-1",
                null,
                null);
        jobService.markRunning(job.id());

        StubPrinterPort port = new StubPrinterPort();
        port.response = "ok T:21.80 /0.00 B:21.52 /0.00 @:0 B@:0";

        PrinterRuntimeNode node = new PrinterRuntimeNode(
                "printer-1",
                "Printer 1",
                "SIM_PORT",
                "sim",
                port,
                true);

        PrinterMonitoringTask task = new PrinterMonitoringTask(
                node,
                stateCache,
                new PrinterSnapshotStore(),
                eventStore,
                clock,
                "M105",
                () -> false,
                new MonitoringEventPolicy(clock, Duration.ofSeconds(60)),
                MonitoringRules.defaults());

        task.run();

        PrintJob completed = jobStore.findById(job.id()).orElseThrow();
        assertEquals(JobState.COMPLETED, completed.state());
        assertNotNull(completed.finishedAt());
    }

    @Test
    void sdPrintStatusTransitionCompletesRunningAutonomousPrintJob() throws Exception {
        useDatabase("autonomous-print-status-complete.db");

        MutableClock clock = new MutableClock(Instant.parse("2026-04-29T10:10:00Z"));
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache(clock);

        PrintJobStore jobStore = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        PrintJobService jobService = new PrintJobService(jobStore, eventStore, clock);
        PrintJob job = jobService.create(
                "Autonomous print",
                JobType.PRINT_FILE,
                "printer-1",
                null,
                "printer-sd-file-1",
                null,
                null);
        jobService.markRunning(job.id());

        StubPrinterPort port = new StubPrinterPort();
        port.response = "ok T:21.80 /0.00 B:21.52 /0.00 @:0 B@:0";
        port.sdPrintStatusResponse = "SD printing byte 42/230";

        PrinterRuntimeNode node = new PrinterRuntimeNode(
                "printer-1",
                "Printer 1",
                "SIM_PORT",
                "sim",
                port,
                true);

        PrinterMonitoringTask task = new PrinterMonitoringTask(
                node,
                stateCache,
                new PrinterSnapshotStore(),
                eventStore,
                clock,
                "M105",
                () -> false,
                new MonitoringEventPolicy(clock, Duration.ofSeconds(60)),
                MonitoringRules.defaults());

        task.run();

        PrintJob stillRunning = jobStore.findById(job.id()).orElseThrow();
        assertEquals(JobState.RUNNING, stillRunning.state());
        assertEquals(PrinterState.PRINTING, stateCache.findByPrinterId("printer-1").orElseThrow().state());

        port.sdPrintStatusResponse = "Not SD printing";
        clock.setInstant(clock.instant().plusSeconds(5));

        task.run();

        PrintJob completed = jobStore.findById(job.id()).orElseThrow();
        assertEquals(JobState.COMPLETED, completed.state());
        assertNotNull(completed.finishedAt());
    }

    @Test
    void notSdPrintingCompletesRunningAutonomousPrintJobWhenPrintingStateWasMissed() throws Exception {
        useDatabase("autonomous-print-missed-printing-state-complete.db");

        MutableClock clock = new MutableClock(Instant.parse("2026-04-29T10:20:00Z"));
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache(clock);

        PrintJobStore jobStore = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        PrintJobService jobService = new PrintJobService(jobStore, eventStore, clock);
        PrintJob job = jobService.create(
                "Fast autonomous print",
                JobType.PRINT_FILE,
                "printer-1",
                null,
                "printer-sd-file-1",
                null,
                null);
        jobService.markRunning(job.id());
        clock.setInstant(clock.instant().plusSeconds(6));

        StubPrinterPort port = new StubPrinterPort();
        port.response = "ok T:21.80 /0.00 B:21.52 /0.00 @:0 B@:0";
        port.sdPrintStatusResponse = "Not SD printing";

        PrinterRuntimeNode node = new PrinterRuntimeNode(
                "printer-1",
                "Printer 1",
                "SIM_PORT",
                "sim",
                port,
                true);

        PrinterMonitoringTask task = new PrinterMonitoringTask(
                node,
                stateCache,
                new PrinterSnapshotStore(),
                eventStore,
                clock,
                "M105",
                () -> false,
                new MonitoringEventPolicy(clock, Duration.ofSeconds(60)),
                MonitoringRules.defaults());

        task.run();

        PrintJob completed = jobStore.findById(job.id()).orElseThrow();
        assertEquals(JobState.COMPLETED, completed.state());
        assertNotNull(completed.finishedAt());
        assertEquals(PrinterState.IDLE, stateCache.findByPrinterId("printer-1").orElseThrow().state());
    }

    private void useDatabase(String fileName) {
        Path dbFile = tempDir.resolve(fileName);
        System.setProperty("printerhub.databaseFile", dbFile.toString());
        new DatabaseInitializer().initialize();
    }

    private int countRows(String tableName) throws Exception {
        try (Connection connection = printerhub.persistence.Database.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private String firstEventType() throws Exception {
        try (Connection connection = printerhub.persistence.Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT event_type FROM printer_events ORDER BY id ASC LIMIT 1");
                ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next());
            return resultSet.getString(1);
        }
    }

    private String firstEventMessage() throws Exception {
        try (Connection connection = printerhub.persistence.Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT message FROM printer_events ORDER BY id ASC LIMIT 1");
                ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next());
            return resultSet.getString(1);
        }
    }

    private static final class StubPrinterPort implements PrinterPort {
        private int connectCalls;
        private int sendCommandCalls;
        private String lastCommand;
        private String response = "ok";
        private String sdPrintStatusResponse = "Not SD printing";
        private RuntimeException connectException;
        private boolean sendInterrupted;

        @Override
        public void connect() {
            connectCalls++;
            if (connectException != null) {
                throw connectException;
            }
        }

        @Override
        public String sendRawLine(String line) {
            return sendRawLine(line, SerialIOMode.COMMAND_RESPONSE);
        }

        @Override
        public String sendRawLine(String line, SerialIOMode mode) {
            return "ok";
        }

        @Override
        public void writeRawLine(String line, SerialIOMode mode) {
            // not needed here
        }

        @Override
        public String readRawResponse(SerialIOMode mode) {
            return "ok";
        }

        @Override
        public java.util.List<String> sendRawLinesPipelined(java.util.List<String> lines, SerialIOMode mode) {
            if (lines == null || lines.isEmpty()) {
                return java.util.List.of();
            }

            java.util.List<String> responses = new java.util.ArrayList<>(lines.size());
            for (int i = 0; i < lines.size(); i++) {
                responses.add("ok");
            }
            return responses;
        }

        @Override
        public void discardPendingInput(int quietPeriodMs, int maxDrainMs) {
            // not needed here
        }

        @Override
        public String sendCommand(String command) {
            sendCommandCalls++;
            lastCommand = command;

            if (sendInterrupted) {
                throw new IllegalStateException("scheduler shutdown", new InterruptedException("scheduler shutdown"));
            }

            if ("M27".equals(command)) {
                return sdPrintStatusResponse;
            }

            return response;
        }

        @Override
        public void disconnect() {
            // not needed here
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private String dumpPrinterEvents() throws Exception {
        StringBuilder dump = new StringBuilder();

        try (Connection connection = printerhub.persistence.Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT id, printer_id, job_id, event_type, message, created_at "
                                + "FROM printer_events ORDER BY id ASC");
                ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                if (!dump.isEmpty()) {
                    dump.append(System.lineSeparator());
                }

                dump.append("id=").append(resultSet.getLong("id"))
                        .append(", printerId=").append(resultSet.getString("printer_id"))
                        .append(", jobId=").append(resultSet.getString("job_id"))
                        .append(", eventType=").append(resultSet.getString("event_type"))
                        .append(", message=").append(resultSet.getString("message"))
                        .append(", createdAt=").append(resultSet.getString("created_at"));
            }
        }

        if (dump.isEmpty()) {
            return "<no printer_events rows>";
        }

        return dump.toString();
    }

    private String dumpPrinterSnapshots() throws Exception {
        StringBuilder dump = new StringBuilder();

        try (Connection connection = printerhub.persistence.Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT id, printer_id, state, hotend_temperature, bed_temperature, "
                                + "last_response, error_message, created_at "
                                + "FROM printer_snapshots ORDER BY id ASC");
                ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                if (!dump.isEmpty()) {
                    dump.append(System.lineSeparator());
                }

                dump.append("id=").append(resultSet.getLong("id"))
                        .append(", printerId=").append(resultSet.getString("printer_id"))
                        .append(", state=").append(resultSet.getString("state"))
                        .append(", hotend=").append(resultSet.getString("hotend_temperature"))
                        .append(", bed=").append(resultSet.getString("bed_temperature"))
                        .append(", lastResponse=").append(resultSet.getString("last_response"))
                        .append(", errorMessage=").append(resultSet.getString("error_message"))
                        .append(", createdAt=").append(resultSet.getString("created_at"));
            }
        }

        if (dump.isEmpty()) {
            return "<no printer_snapshots rows>";
        }

        return dump.toString();
    }
}
