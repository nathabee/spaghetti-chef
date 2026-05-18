package printerhub.job;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import printerhub.PrinterPort;
import printerhub.SerialIOMode;
import printerhub.config.RuntimeDefaults;
import printerhub.monitoring.PrinterMonitoringScheduler;
import printerhub.persistence.DatabaseInitializer;
import printerhub.persistence.PrintFileStore;
import printerhub.persistence.PrintJobExecutionStepStore;
import printerhub.persistence.PrintJobStore;
import printerhub.persistence.PrinterEventStore;
import printerhub.persistence.PrinterSdFileStore;
import printerhub.runtime.PrinterRegistry;
import printerhub.runtime.PrinterRuntimeNode;
import printerhub.runtime.PrinterRuntimeStateCache;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AutonomousPrintControlServiceTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        System.clearProperty(RuntimeDefaults.DATABASE_FILE_PROPERTY);
    }

    @Test
    void pauseRunningPrintFileJobSendsM25AndMarksJobPaused() {
        initializeDatabase("autonomous-pause.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        PrintJobExecutionStepStore stepStore = new PrintJobExecutionStepStore();
        PrintFileStore printFileStore = new PrintFileStore();
        Clock clock = Clock.fixed(Instant.parse("2026-05-07T20:00:00Z"), ZoneOffset.UTC);

        PrintJobService jobService = new PrintJobService(store, eventStore, clock);
        PrinterSdFileService printerSdFileService = new PrinterSdFileService(new PrinterSdFileStore(), printFileStore,
                clock);

        PrinterRegistry registry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(registry, stateCache);
        RecordingPrinterPort port = new RecordingPrinterPort("ok");

        try {
            registry.register(new PrinterRuntimeNode("printer-1", "Printer 1", "SIM_PORT", "sim", port, true));
            PrinterSdFile printerSdFile = printerSdFileService.register(
                    "printer-1",
                    "TEST4.GCO",
                    "TEST4.GCO",
                    9L,
                    "TEST4.GCO 9",
                    null);

            PrintJob job = jobService.create("Pause me", JobType.PRINT_FILE, "printer-1", null, printerSdFile.id(),
                    null, null);
            jobService.markRunning(job.id());

            AutonomousPrintControlService service = new AutonomousPrintControlService(jobService, registry, scheduler,
                    stepStore);
            AutonomousPrintControlService.ControlResult result = service.pause(job.id());

            assertTrue(result.success());
            assertEquals("M25", result.wireCommand());
            assertEquals("M25", port.commands().get(0));
            assertEquals(JobState.PAUSED, store.findById(job.id()).orElseThrow().state());
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void pauseBusyResponseMarksJobPausedSoResumeRemainsAvailable() {
        initializeDatabase("autonomous-pause-busy.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        PrintJobExecutionStepStore stepStore = new PrintJobExecutionStepStore();
        PrintFileStore printFileStore = new PrintFileStore();
        Clock clock = Clock.fixed(Instant.parse("2026-05-07T20:00:00Z"), ZoneOffset.UTC);

        PrintJobService jobService = new PrintJobService(store, eventStore, clock);
        PrinterSdFileService printerSdFileService = new PrinterSdFileService(new PrinterSdFileStore(), printFileStore,
                clock);

        PrinterRegistry registry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(registry, stateCache);
        RecordingPrinterPort port = new RecordingPrinterPort("echo:busy: processing echo:busy: processing");

        try {
            registry.register(new PrinterRuntimeNode("printer-1", "Printer 1", "SIM_PORT", "sim", port, true));
            PrinterSdFile printerSdFile = printerSdFileService.register(
                    "printer-1",
                    "TEST4.GCO",
                    "TEST4.GCO",
                    9L,
                    "TEST4.GCO 9",
                    null);

            PrintJob job = jobService.create("Pause me", JobType.PRINT_FILE, "printer-1", null, printerSdFile.id(),
                    null, null);
            jobService.markRunning(job.id());

            AutonomousPrintControlService service = new AutonomousPrintControlService(jobService, registry, scheduler,
                    stepStore);
            AutonomousPrintControlService.ControlResult result = service.pause(job.id());

            assertTrue(result.success());
            assertEquals("M25", result.wireCommand());
            assertEquals("echo:busy: processing echo:busy: processing", result.response());
            assertEquals("M25", port.commands().get(0));
            assertEquals(JobState.PAUSED, store.findById(job.id()).orElseThrow().state());
            assertEquals("PAUSE_REQUESTED", stepStore.findByJobId(job.id()).get(0).outcome());
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void resumePausedPrintFileJobSendsM24AndMarksJobRunning() {
        initializeDatabase("autonomous-resume.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        PrintJobExecutionStepStore stepStore = new PrintJobExecutionStepStore();
        PrintFileStore printFileStore = new PrintFileStore();
        Clock clock = Clock.fixed(Instant.parse("2026-05-07T20:00:00Z"), ZoneOffset.UTC);

        PrintJobService jobService = new PrintJobService(store, eventStore, clock);
        PrinterSdFileService printerSdFileService = new PrinterSdFileService(new PrinterSdFileStore(), printFileStore,
                clock);

        PrinterRegistry registry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(registry, stateCache);
        RecordingPrinterPort port = new RecordingPrinterPort("ok");

        try {
            registry.register(new PrinterRuntimeNode("printer-1", "Printer 1", "SIM_PORT", "sim", port, true));
            PrinterSdFile printerSdFile = printerSdFileService.register(
                    "printer-1",
                    "TEST4.GCO",
                    "TEST4.GCO",
                    9L,
                    "TEST4.GCO 9",
                    null);

            PrintJob job = jobService.create("Resume me", JobType.PRINT_FILE, "printer-1", null, printerSdFile.id(),
                    null, null);
            jobService.markRunning(job.id());
            jobService.markPaused(job.id());

            AutonomousPrintControlService service = new AutonomousPrintControlService(jobService, registry, scheduler,
                    stepStore);
            AutonomousPrintControlService.ControlResult result = service.resume(job.id());

            assertTrue(result.success());
            assertEquals("M24", result.wireCommand());
            assertEquals("M24", port.commands().get(0));
            assertEquals(JobState.RUNNING, store.findById(job.id()).orElseThrow().state());
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void cancelRunningPrintFileJobSendsM524AndMarksJobCancelled() {
        initializeDatabase("autonomous-cancel.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        PrintJobExecutionStepStore stepStore = new PrintJobExecutionStepStore();
        PrintFileStore printFileStore = new PrintFileStore();
        Clock clock = Clock.fixed(Instant.parse("2026-05-07T20:00:00Z"), ZoneOffset.UTC);

        PrintJobService jobService = new PrintJobService(store, eventStore, clock);
        PrinterSdFileService printerSdFileService = new PrinterSdFileService(new PrinterSdFileStore(), printFileStore,
                clock);

        PrinterRegistry registry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(registry, stateCache);
        SequencePrinterPort port = new SequencePrinterPort("ok", "Not SD printing");

        try {
            registry.register(new PrinterRuntimeNode("printer-1", "Printer 1", "SIM_PORT", "sim", port, true));
            PrinterSdFile printerSdFile = printerSdFileService.register(
                    "printer-1",
                    "TEST4.GCO",
                    "TEST4.GCO",
                    9L,
                    "TEST4.GCO 9",
                    null);

            PrintJob job = jobService.create("Cancel me", JobType.PRINT_FILE, "printer-1", null, printerSdFile.id(),
                    null, null);
            jobService.markRunning(job.id());

            AutonomousPrintControlService service = new AutonomousPrintControlService(jobService, registry, scheduler,
                    stepStore);
            AutonomousPrintControlService.ControlResult result = service.cancel(job.id());

            assertTrue(result.success());
            assertEquals("M524", result.wireCommand());
            assertTrue(
                    port.commands().size() >= 2,
                    "Expected at least M524 and M27, got: " + port.commands());

            assertEquals(
                    List.of("M524", "M27"),
                    port.commands().subList(0, 2));
            PrintJob loaded = store.findById(job.id()).orElseThrow();
            assertEquals(JobState.CANCELLED, loaded.state());
            assertNotNull(loaded.finishedAt());
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void cancelRunningPrintFileJobRetriesWhenPrinterReportsBusy() {
        initializeDatabase("autonomous-cancel-busy-retry.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        PrintJobExecutionStepStore stepStore = new PrintJobExecutionStepStore();
        PrintFileStore printFileStore = new PrintFileStore();
        Clock clock = Clock.fixed(Instant.parse("2026-05-07T20:00:00Z"), ZoneOffset.UTC);

        PrintJobService jobService = new PrintJobService(store, eventStore, clock);
        PrinterSdFileService printerSdFileService = new PrinterSdFileService(new PrinterSdFileStore(), printFileStore,
                clock);

        PrinterRegistry registry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(registry, stateCache);
        SequencePrinterPort port = new SequencePrinterPort(
                "echo:busy: processing",
                "echo:busy: processing",
                "ok",
                "Not SD printing");

        try {
            registry.register(new PrinterRuntimeNode("printer-1", "Printer 1", "SIM_PORT", "sim", port, true));
            PrinterSdFile printerSdFile = printerSdFileService.register(
                    "printer-1",
                    "TEST4.GCO",
                    "TEST4.GCO",
                    9L,
                    "TEST4.GCO 9",
                    null);

            PrintJob job = jobService.create("Cancel me", JobType.PRINT_FILE, "printer-1", null, printerSdFile.id(),
                    null, null);
            jobService.markRunning(job.id());

            AutonomousPrintControlService service = new AutonomousPrintControlService(jobService, registry, scheduler,
                    stepStore);
            AutonomousPrintControlService.ControlResult result = service.cancel(job.id());

            assertTrue(result.success());
            assertEquals(List.of("M524", "M524", "M524", "M27"), port.commands());
            assertEquals(JobState.CANCELLED, store.findById(job.id()).orElseThrow().state());
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void cancelRunningPrintFileJobAcceptsOkAfterStalePrinterErrors() {
        initializeDatabase("autonomous-cancel-stale-errors.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        PrintJobExecutionStepStore stepStore = new PrintJobExecutionStepStore();
        PrintFileStore printFileStore = new PrintFileStore();
        Clock clock = Clock.fixed(Instant.parse("2026-05-07T20:00:00Z"), ZoneOffset.UTC);

        PrintJobService jobService = new PrintJobService(store, eventStore, clock);
        PrinterSdFileService printerSdFileService = new PrinterSdFileService(new PrinterSdFileStore(), printFileStore,
                clock);

        PrinterRegistry registry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(registry, stateCache);
        SequencePrinterPort port = new SequencePrinterPort(
                """
                        echo:busy: processing
                        echo:Invalid mesh.
                        Error:Failed to enable Bed Leveling
                        ok
                        """,
                "Not SD printing");

        try {
            registry.register(new PrinterRuntimeNode("printer-1", "Printer 1", "SIM_PORT", "sim", port, true));
            PrinterSdFile printerSdFile = printerSdFileService.register(
                    "printer-1",
                    "TEST4.GCO",
                    "TEST4.GCO",
                    9L,
                    "TEST4.GCO 9",
                    null);

            PrintJob job = jobService.create("Cancel me", JobType.PRINT_FILE, "printer-1", null, printerSdFile.id(),
                    null, null);
            jobService.markRunning(job.id());

            AutonomousPrintControlService service = new AutonomousPrintControlService(jobService, registry, scheduler,
                    stepStore);
            AutonomousPrintControlService.ControlResult result = service.cancel(job.id());

            assertTrue(result.success());
            assertEquals("M524", result.wireCommand());
            assertTrue(
                    port.commands().size() >= 2,
                    "Expected at least M524 and M27, got: " + port.commands());

            assertEquals(
                    List.of("M524", "M27"),
                    port.commands().subList(0, 2));
            assertEquals(JobState.CANCELLED, store.findById(job.id()).orElseThrow().state());
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void cancelDoesNotMarkCancelledWhenPrinterStillReportsSdPrintingAfterAbortOk() {
        initializeDatabase("autonomous-cancel-status-still-printing.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        PrintJobExecutionStepStore stepStore = new PrintJobExecutionStepStore();
        PrintFileStore printFileStore = new PrintFileStore();
        Clock clock = Clock.fixed(Instant.parse("2026-05-07T20:00:00Z"), ZoneOffset.UTC);

        PrintJobService jobService = new PrintJobService(store, eventStore, clock);
        PrinterSdFileService printerSdFileService = new PrinterSdFileService(new PrinterSdFileStore(), printFileStore,
                clock);

        PrinterRegistry registry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(registry, stateCache);
        SequencePrinterPort port = new SequencePrinterPort(
                "ok",
                "SD printing byte 10/200",
                "SD printing byte 20/200",
                "SD printing byte 30/200");

        try {
            registry.register(new PrinterRuntimeNode("printer-1", "Printer 1", "SIM_PORT", "sim", port, true));
            PrinterSdFile printerSdFile = printerSdFileService.register(
                    "printer-1",
                    "TEST4.GCO",
                    "TEST4.GCO",
                    9L,
                    "TEST4.GCO 9",
                    null);

            PrintJob job = jobService.create("Cancel me", JobType.PRINT_FILE, "printer-1", null, printerSdFile.id(),
                    null, null);
            jobService.markRunning(job.id());

            AutonomousPrintControlService service = new AutonomousPrintControlService(jobService, registry, scheduler,
                    stepStore);
            AutonomousPrintControlService.ControlResult result = service.cancel(job.id());

            assertFalse(result.success());
            assertEquals("M27", result.wireCommand());
            assertEquals(JobFailureReason.PRINTER_BUSY, result.failureReason());
            assertEquals(List.of("M524", "M27", "M27", "M27"), List.copyOf(port.commands()).subList(0, 4));
            assertEquals(JobState.RUNNING, store.findById(job.id()).orElseThrow().state());
        } finally {
            scheduler.stop();
        }
    }

    private void initializeDatabase(String fileName) {
        String databaseFile = tempDir.resolve(fileName).toString();
        System.setProperty(RuntimeDefaults.DATABASE_FILE_PROPERTY, databaseFile);
        new DatabaseInitializer().initialize();
    }

    private static final class RecordingPrinterPort implements PrinterPort {
        private final java.util.List<String> commands = new java.util.ArrayList<>();
        private final java.util.List<String> pendingRawResponses = new java.util.ArrayList<>();
        private final String response;
        private boolean connected;

        private RecordingPrinterPort(String response) {
            this.response = response;
        }

        @Override
        public void connect() {
            connected = true;
        }

        @Override
        public String sendRawLine(String line) {
            return sendRawLine(line, SerialIOMode.COMMAND_RESPONSE);
        }

        @Override
        public String sendRawLine(String line, SerialIOMode mode) {
            ensureConnected();
            commands.add(line);

            if (!pendingRawResponses.isEmpty()) {
                return pendingRawResponses.remove(0);
            }

            return "ok";
        }

        @Override
        public void writeRawLine(String line, SerialIOMode mode) {
            ensureConnected();
            commands.add(line);
        }

        @Override
        public String readRawResponse(SerialIOMode mode) {
            ensureConnected();

            if (!pendingRawResponses.isEmpty()) {
                return pendingRawResponses.remove(0);
            }

            return "ok";
        }

        @Override
        public java.util.List<String> sendRawLinesPipelined(java.util.List<String> lines, SerialIOMode mode) {
            ensureConnected();

            if (lines == null || lines.isEmpty()) {
                return java.util.List.of();
            }

            java.util.List<String> results = new java.util.ArrayList<>(lines.size());
            for (String line : lines) {
                commands.add(line);
                if (!pendingRawResponses.isEmpty()) {
                    results.add(pendingRawResponses.remove(0));
                } else {
                    results.add("ok");
                }
            }
            return results;
        }

        @Override
        public void discardPendingInput(int quietPeriodMs, int maxDrainMs) {
            ensureConnected();
            pendingRawResponses.clear();
        }

        @Override
        public String sendCommand(String command) {
            ensureConnected();
            commands.add(command);
            return response;
        }

        @Override
        public void disconnect() {
            connected = false;
            pendingRawResponses.clear();
        }

        private java.util.List<String> commands() {
            return commands;
        }

        private void ensureConnected() {
            if (!connected) {
                throw new IllegalStateException("not connected");
            }
        }
    }

    private static final class SequencePrinterPort implements PrinterPort {
        private final java.util.List<String> commands = new java.util.ArrayList<>();
        private final java.util.List<String> responses;
        private final java.util.List<String> pendingRawResponses = new java.util.ArrayList<>();
        private int index;
        private boolean connected;

        private SequencePrinterPort(String... responses) {
            this.responses = java.util.List.of(responses);
        }

        @Override
        public void connect() {
            connected = true;
        }

        @Override
        public String sendRawLine(String line) {
            return sendRawLine(line, SerialIOMode.COMMAND_RESPONSE);
        }

        @Override
        public String sendRawLine(String line, SerialIOMode mode) {
            ensureConnected();
            commands.add(line);
            return "ok";
        }

        @Override
        public void writeRawLine(String line, SerialIOMode mode) {
            ensureConnected();
            commands.add(line);
            pendingRawResponses.add("ok");
        }

        @Override
        public String readRawResponse(SerialIOMode mode) {
            ensureConnected();

            if (!pendingRawResponses.isEmpty()) {
                return pendingRawResponses.remove(0);
            }

            return "ok";
        }

        @Override
        public java.util.List<String> sendRawLinesPipelined(java.util.List<String> lines, SerialIOMode mode) {
            ensureConnected();

            if (lines == null || lines.isEmpty()) {
                return java.util.List.of();
            }

            java.util.List<String> results = new java.util.ArrayList<>(lines.size());
            for (String line : lines) {
                commands.add(line);
                results.add("ok");
            }
            return results;
        }

        @Override
        public void discardPendingInput(int quietPeriodMs, int maxDrainMs) {
            ensureConnected();
            pendingRawResponses.clear();
        }

        @Override
        public String sendCommand(String command) {
            ensureConnected();
            commands.add(command);
            String response = responses.get(Math.min(index, responses.size() - 1));
            index++;
            return response;
        }

        @Override
        public void disconnect() {
            connected = false;
            pendingRawResponses.clear();
        }

        private java.util.List<String> commands() {
            return commands;
        }

        private void ensureConnected() {
            if (!connected) {
                throw new IllegalStateException("not connected");
            }
        }
    }

}
