package spaghettichef.job;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spaghettichef.OperationMessages;
import spaghettichef.PrinterPort;
import spaghettichef.SerialIOMode;
import spaghettichef.config.RuntimeDefaults;
import spaghettichef.monitoring.PrinterMonitoringScheduler;
import spaghettichef.persistence.DatabaseInitializer;
import spaghettichef.persistence.PrintJobStore;
import spaghettichef.persistence.PrinterEvent;
import spaghettichef.persistence.PrinterEventStore;
import spaghettichef.runtime.PrinterRegistry;
import spaghettichef.runtime.PrinterRuntimeNode;
import spaghettichef.runtime.PrinterRuntimeStateCache;
import spaghettichef.persistence.PrintJobExecutionStepStore;
import spaghettichef.persistence.PrintFileStore;
import spaghettichef.persistence.PrinterSdFileStore;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PrintJobExecutionServiceTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        System.clearProperty(RuntimeDefaults.DATABASE_FILE_PROPERTY);
    }

    @Test
    void executeAssignedReadFirmwareJobSucceedsAndCompletesLifecycle() {
        initializeDatabase("job-execution-success.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        Clock clock = Clock.fixed(Instant.parse("2026-05-04T08:00:00Z"), ZoneOffset.UTC);

        PrintJobService jobService = new PrintJobService(store, eventStore, clock);

        PrinterRegistry registry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(registry, stateCache);

        try {
            PrinterRuntimeNode node = new PrinterRuntimeNode(
                    "printer-1",
                    "Printer 1",
                    "SIM_PORT",
                    "sim",
                    new SingleResponsePrinterPort("ok FIRMWARE_NAME:Marlin"),
                    true);
            registry.register(node);

            PrintJob job = jobService.create(
                    "Read firmware",
                    JobType.READ_FIRMWARE_INFO,
                    "printer-1",
                    null,
                    null);

            PrintJobExecutionService executionService = new PrintJobExecutionService(
                    jobService,
                    registry,
                    scheduler,
                    new PrinterActionGuard(),
                    new PrinterActionMapper(),
                    new PrintJobExecutionStepStore());

            PrinterActionExecutionResult result = executionService.execute(job.id());

            assertTrue(result.success());
            assertEquals("M115", result.wireCommand());
            assertEquals("ok FIRMWARE_NAME:Marlin", result.response());
            assertEquals("SUCCESS", result.outcome());
            assertNull(result.failureReason());
            assertNull(result.failureDetail());

            PrintJob loaded = store.findById(job.id()).orElseThrow();
            assertEquals(JobState.COMPLETED, loaded.state());
            assertFalse(node.executionInProgress());
            assertNull(node.activeJobId());

            List<PrinterEvent> events = eventStore.findRecentByJobId(job.id(), 20);
            assertTrue(events.stream()
                    .anyMatch(event -> event.message() != null && event.message().contains("read-firmware-info")));
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void executeRejectedByGuardMarksJobFailed() {
        initializeDatabase("job-execution-guard-fail.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        Clock clock = Clock.fixed(Instant.parse("2026-05-04T08:00:00Z"), ZoneOffset.UTC);

        PrintJobService jobService = new PrintJobService(store, eventStore, clock);

        PrinterRegistry registry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(registry, stateCache);

        try {
            PrinterRuntimeNode node = new PrinterRuntimeNode(
                    "printer-1",
                    "Printer 1",
                    "SIM_PORT",
                    "sim",
                    new SingleResponsePrinterPort("ok"),
                    false);
            registry.register(node);

            PrintJob job = jobService.create(
                    "Home axes",
                    JobType.HOME_AXES,
                    "printer-1",
                    null,
                    null);

            PrintJobExecutionService executionService = new PrintJobExecutionService(
                    jobService,
                    registry,
                    scheduler,
                    new PrinterActionGuard(),
                    new PrinterActionMapper(),
                    new PrintJobExecutionStepStore());
            PrinterActionExecutionResult result = executionService.execute(job.id());

            assertFalse(result.success());
            assertNull(result.wireCommand());
            assertNull(result.response());
            assertEquals("PRINTER_DISABLED", result.outcome());
            assertEquals(JobFailureReason.PRINTER_DISABLED, result.failureReason());

            PrintJob loaded = store.findById(job.id()).orElseThrow();
            assertEquals(JobState.FAILED, loaded.state());
            assertEquals(JobFailureReason.PRINTER_DISABLED.name(), loaded.failureReason());
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void executeWorkflowExceptionMarksJobFailedAndClearsNodeBusyState() {
        initializeDatabase("job-execution-io-fail.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        Clock clock = Clock.fixed(Instant.parse("2026-05-04T08:00:00Z"), ZoneOffset.UTC);

        PrintJobService jobService = new PrintJobService(store, eventStore, clock);

        PrinterRegistry registry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(registry, stateCache);

        try {
            PrinterRuntimeNode node = new PrinterRuntimeNode(
                    "printer-1",
                    "Printer 1",
                    "SIM_PORT",
                    "sim",
                    new ExceptionOnSecondCommandPrinterPort("ok X:0.00 Y:0.00 Z:0.00", "communication failure"),
                    true);
            registry.register(node);

            PrintJob job = jobService.create(
                    "Home axes",
                    JobType.HOME_AXES,
                    "printer-1",
                    null,
                    null);

            PrintJobExecutionService executionService = new PrintJobExecutionService(
                    jobService,
                    registry,
                    scheduler,
                    new PrinterActionGuard(),
                    new PrinterActionMapper(),
                    new PrintJobExecutionStepStore());

            PrinterActionExecutionResult result = executionService.execute(job.id());

            assertFalse(result.success());
            assertEquals("G28", result.wireCommand());
            assertNull(result.response());
            assertEquals("COMMUNICATION_FAILURE", result.outcome());
            assertEquals(JobFailureReason.COMMUNICATION_FAILURE, result.failureReason());
            assertTrue(result.failureDetail().contains("communication failure"));

            PrintJob loaded = store.findById(job.id()).orElseThrow();
            assertEquals(JobState.FAILED, loaded.state());
            assertEquals(JobFailureReason.COMMUNICATION_FAILURE.name(), loaded.failureReason());
            assertFalse(node.executionInProgress());
            assertNull(node.activeJobId());
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void executeHomeAxesWithPrinterReportedFailurePersistsActualResponse() {
        initializeDatabase("job-execution-home-axes-printer-failure.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        Clock clock = Clock.fixed(Instant.parse("2026-05-04T08:00:00Z"), ZoneOffset.UTC);

        PrintJobService jobService = new PrintJobService(store, eventStore, clock);

        PrinterRegistry registry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(registry, stateCache);

        try {
            PrinterRuntimeNode node = new PrinterRuntimeNode(
                    "printer-1",
                    "Printer 1",
                    "/dev/ttyUSB0",
                    "real",
                    new SequencePrinterPort(
                            "ok X:0.00 Y:0.00 Z:0.00",
                            "echo:busy: processing echo:Homing Failed Error:Printer halted. kill() called!"),
                    true);
            registry.register(node);

            PrintJob job = jobService.create(
                    "Home axes",
                    JobType.HOME_AXES,
                    "printer-1",
                    null,
                    null);
            PrintJobExecutionService executionService = new PrintJobExecutionService(
                    jobService,
                    registry,
                    scheduler,
                    new PrinterActionGuard(),
                    new PrinterActionMapper(),
                    new PrintJobExecutionStepStore());

            PrinterActionExecutionResult result = executionService.execute(job.id());

            assertFalse(result.success());
            assertEquals("G28", result.wireCommand());
            assertEquals(
                    "echo:busy: processing echo:Homing Failed Error:Printer halted. kill() called!",
                    result.response());
            assertEquals("PRINTER_REPORTED_FAILURE", result.outcome());
            assertEquals(JobFailureReason.PRINTER_REPORTED_FAILURE, result.failureReason());
            assertNotNull(result.failureDetail());
            assertTrue(result.failureDetail().contains("Homing Failed"));
            assertTrue(result.failureDetail().contains("Printer halted"));

            PrintJob loaded = store.findById(job.id()).orElseThrow();
            assertEquals(JobState.FAILED, loaded.state());
            assertEquals(JobFailureReason.PRINTER_REPORTED_FAILURE.name(), loaded.failureReason());
            assertNotNull(loaded.failureDetail());
            assertTrue(loaded.failureDetail().contains("Homing Failed"));
            assertTrue(loaded.failureDetail().contains("Printer halted"));

            List<PrinterEvent> events = eventStore.findRecentByJobId(job.id(), 20);
            assertTrue(events.stream().anyMatch(event -> event.message() != null
                    && event.message().contains("Workflow step started: validate-position-before-home -> M114")));
            assertTrue(events.stream().anyMatch(event -> event.message() != null
                    && event.message().contains("Workflow step started: home-axes -> G28")));
            assertTrue(events.stream().anyMatch(event -> event.message() != null
                    && event.message().contains("outcome=PRINTER_REPORTED_FAILURE")));
            assertTrue(events.stream().anyMatch(event -> event.message() != null
                    && event.message().contains("Homing Failed Error:Printer halted. kill() called!")));

            assertFalse(node.executionInProgress());
            assertNull(node.activeJobId());
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void executeHomeAxesTreatsBusyThenOkAsInProgressThenSuccess() {
        initializeDatabase("job-execution-home-axes-busy-ok.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        PrintJobExecutionStepStore stepStore = new PrintJobExecutionStepStore();
        Clock clock = Clock.fixed(Instant.parse("2026-05-04T08:00:00Z"), ZoneOffset.UTC);

        PrintJobService jobService = new PrintJobService(store, eventStore, clock);

        PrinterRegistry registry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(registry, stateCache);

        try {
            PrinterRuntimeNode node = new PrinterRuntimeNode(
                    "printer-1",
                    "Printer 1",
                    "/dev/ttyUSB0",
                    "real",
                    new SequencePrinterPort(
                            "ok X:0.00 Y:0.00 Z:0.00",
                            "echo:busy: processing\nok"),
                    true);
            registry.register(node);

            PrintJob job = jobService.create(
                    "Home axes",
                    JobType.HOME_AXES,
                    "printer-1",
                    null,
                    null);

            PrintJobExecutionService executionService = new PrintJobExecutionService(
                    jobService,
                    registry,
                    scheduler,
                    new PrinterActionGuard(),
                    new PrinterActionMapper(),
                    stepStore);

            PrinterActionExecutionResult result = executionService.execute(job.id());

            assertTrue(result.success());
            assertEquals("G28", result.wireCommand());
            assertEquals("echo:busy: processing\nok", result.response());

            PrintJob loaded = store.findById(job.id()).orElseThrow();
            assertEquals(JobState.COMPLETED, loaded.state());

            List<PrinterEvent> events = eventStore.findRecentByJobId(job.id(), 20);
            assertTrue(events.stream()
                    .anyMatch(event -> OperationMessages.EVENT_JOB_EXECUTION_IN_PROGRESS.equals(event.eventType())
                            && event.message() != null
                            && event.message().contains("echo:busy: processing")));
            assertTrue(events.stream()
                    .anyMatch(event -> OperationMessages.EVENT_JOB_EXECUTION_SUCCEEDED.equals(event.eventType())
                            && event.message() != null
                            && event.message().contains("Job execution completed: G28")));
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void executeAssignedReadFirmwareJobPersistsStructuredExecutionStep() {
        initializeDatabase("job-execution-step-success.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        PrintJobExecutionStepStore stepStore = new PrintJobExecutionStepStore();
        Clock clock = Clock.fixed(Instant.parse("2026-05-04T08:00:00Z"), ZoneOffset.UTC);

        PrintJobService jobService = new PrintJobService(store, eventStore, clock);

        PrinterRegistry registry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(registry, stateCache);

        try {
            PrinterRuntimeNode node = new PrinterRuntimeNode(
                    "printer-1",
                    "Printer 1",
                    "SIM_PORT",
                    "sim",
                    new SingleResponsePrinterPort("ok FIRMWARE_NAME:Marlin"),
                    true);
            registry.register(node);

            PrintJob job = jobService.create(
                    "Read firmware",
                    JobType.READ_FIRMWARE_INFO,
                    "printer-1",
                    null,
                    null);

            PrintJobExecutionService executionService = new PrintJobExecutionService(
                    jobService,
                    registry,
                    scheduler,
                    new PrinterActionGuard(),
                    new PrinterActionMapper(),
                    stepStore);

            PrinterActionExecutionResult result = executionService.execute(job.id());

            assertTrue(result.success());

            List<PrintJobExecutionStep> steps = stepStore.findByJobId(job.id());
            assertEquals(1, steps.size());

            PrintJobExecutionStep step = steps.get(0);
            assertEquals(job.id(), step.jobId());
            assertEquals(0, step.stepIndex());
            assertEquals("read-firmware-info", step.stepName());
            assertEquals("M115", step.wireCommand());
            assertEquals("ok FIRMWARE_NAME:Marlin", step.response());
            assertEquals("SUCCESS", step.outcome());
            assertTrue(step.success());
            assertNull(step.failureReason());
            assertNull(step.failureDetail());
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void executePrintFileJobStartsAutonomousPrinterWorkflowAndLeavesJobRunning() {
        initializeDatabase("job-execution-print-file-running.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        PrintJobExecutionStepStore stepStore = new PrintJobExecutionStepStore();
        PrintFileStore printFileStore = new PrintFileStore();
        Clock clock = Clock.fixed(Instant.parse("2026-05-04T08:00:00Z"), ZoneOffset.UTC);

        PrintJobService jobService = new PrintJobService(store, eventStore, clock);
        PrinterSdFileService printerSdFileService = new PrinterSdFileService(new PrinterSdFileStore(), printFileStore,
                clock);

        PrinterRegistry registry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(registry, stateCache);
        CountingPrinterPort printerPort = new CountingPrinterPort();

        try {
            PrinterRuntimeNode node = new PrinterRuntimeNode(
                    "printer-1",
                    "Printer 1",
                    "SIM_PORT",
                    "sim",
                    printerPort,
                    true);
            registry.register(node);

            PrinterSdFile printerSdFile = printerSdFileService.register(
                    "printer-1",
                    "TEST4.GCO",
                    "TEST4.GCO",
                    9L,
                    "TEST4.GCO 9",
                    null);

            PrintJob job = jobService.create(
                    "Print cube",
                    JobType.PRINT_FILE,
                    "printer-1",
                    null,
                    printerSdFile.id(),
                    null,
                    null);

            PrintJobExecutionService executionService = new PrintJobExecutionService(
                    jobService,
                    registry,
                    scheduler,
                    new PrinterActionGuard(),
                    new PrinterActionMapper(),
                    stepStore);

            PrinterActionExecutionResult result = executionService.execute(job.id());

            assertTrue(result.success());
            assertEquals("M24", result.wireCommand());
            assertEquals(2, printerPort.commandCount());
            assertEquals(List.of("M23 TEST4.GCO", "M24"), printerPort.commands());

            PrintJob loaded = store.findById(job.id()).orElseThrow();
            assertEquals(JobState.RUNNING, loaded.state());
            assertNull(loaded.finishedAt());
            assertNull(loaded.printFileId());
            assertEquals(printerSdFile.id(), loaded.printerSdFileId());
            assertFalse(node.executionInProgress());
            assertNull(node.activeJobId());

            List<PrintJobExecutionStep> steps = stepStore.findByJobId(job.id());
            assertEquals(4, steps.size());
            assertEquals("validate-printer-sd-target", steps.get(0).stepName());
            assertEquals("select-printer-sd-file", steps.get(1).stepName());
            assertEquals("M23 TEST4.GCO", steps.get(1).wireCommand());
            assertEquals("start-printer-sd-print", steps.get(2).stepName());
            assertEquals("M24", steps.get(2).wireCommand());
            assertEquals("transition-job-running", steps.get(3).stepName());
            assertNull(steps.get(0).wireCommand());
            assertTrue(steps.get(0).success());
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void executePrintFileJobCompletesImmediatelyWhenPrinterReportsDonePrinting() {
        initializeDatabase("job-execution-print-file-immediate-complete.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        PrintJobExecutionStepStore stepStore = new PrintJobExecutionStepStore();
        PrintFileStore printFileStore = new PrintFileStore();
        Clock clock = Clock.fixed(Instant.parse("2026-05-04T08:00:00Z"), ZoneOffset.UTC);

        PrintJobService jobService = new PrintJobService(store, eventStore, clock);
        PrinterSdFileService printerSdFileService = new PrinterSdFileService(new PrinterSdFileStore(), printFileStore,
                clock);

        PrinterRegistry registry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(registry, stateCache);

        try {
            PrinterRuntimeNode node = new PrinterRuntimeNode(
                    "printer-1",
                    "Printer 1",
                    "SIM_PORT",
                    "sim",
                    new SequencePrinterPort(
                            "echo:Now fresh file: TEST8.GCO File opened: TEST8.GCO Size: 24 File selected ok",
                            "ok Done printing file"),
                    true);
            registry.register(node);

            PrinterSdFile printerSdFile = printerSdFileService.register(
                    "printer-1",
                    "TEST8.GCO",
                    "TEST8.GCO",
                    24L,
                    "TEST8.GCO 24",
                    null);

            PrintJob job = jobService.create(
                    "Print tiny file",
                    JobType.PRINT_FILE,
                    "printer-1",
                    null,
                    printerSdFile.id(),
                    null,
                    null);

            PrintJobExecutionService executionService = new PrintJobExecutionService(
                    jobService,
                    registry,
                    scheduler,
                    new PrinterActionGuard(),
                    new PrinterActionMapper(),
                    stepStore);

            PrinterActionExecutionResult result = executionService.execute(job.id());

            assertTrue(result.success());
            assertEquals("M24", result.wireCommand());

            PrintJob loaded = store.findById(job.id()).orElseThrow();
            assertEquals(JobState.COMPLETED, loaded.state());
            assertNotNull(loaded.finishedAt());
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void executeHomeAxesWithPrinterReportedFailurePersistsStructuredFailureStep() {
        initializeDatabase("job-execution-step-printer-failure.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        PrintJobExecutionStepStore stepStore = new PrintJobExecutionStepStore();
        Clock clock = Clock.fixed(Instant.parse("2026-05-04T08:00:00Z"), ZoneOffset.UTC);

        PrintJobService jobService = new PrintJobService(store, eventStore, clock);

        PrinterRegistry registry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(registry, stateCache);

        try {
            PrinterRuntimeNode node = new PrinterRuntimeNode(
                    "printer-1",
                    "Printer 1",
                    "/dev/ttyUSB0",
                    "real",
                    new SequencePrinterPort(
                            "ok X:0.00 Y:0.00 Z:0.00",
                            "echo:busy: processing echo:Homing Failed Error:Printer halted. kill() called!"),
                    true);
            registry.register(node);

            PrintJob job = jobService.create(
                    "Home axes",
                    JobType.HOME_AXES,
                    "printer-1",
                    null,
                    null);

            PrintJobExecutionService executionService = new PrintJobExecutionService(
                    jobService,
                    registry,
                    scheduler,
                    new PrinterActionGuard(),
                    new PrinterActionMapper(),
                    stepStore);

            PrinterActionExecutionResult result = executionService.execute(job.id());

            assertFalse(result.success());

            List<PrintJobExecutionStep> steps = stepStore.findByJobId(job.id());
            assertEquals(2, steps.size());

            PrintJobExecutionStep validationStep = steps.get(0);
            assertEquals(0, validationStep.stepIndex());
            assertEquals("validate-position-before-home", validationStep.stepName());
            assertEquals("M114", validationStep.wireCommand());
            assertEquals("ok X:0.00 Y:0.00 Z:0.00", validationStep.response());
            assertEquals("SUCCESS", validationStep.outcome());
            assertTrue(validationStep.success());
            assertNull(validationStep.failureReason());
            assertNull(validationStep.failureDetail());

            PrintJobExecutionStep failedStep = steps.get(1);
            assertEquals(1, failedStep.stepIndex());
            assertEquals("home-axes", failedStep.stepName());
            assertEquals("G28", failedStep.wireCommand());
            assertEquals(
                    "echo:busy: processing echo:Homing Failed Error:Printer halted. kill() called!",
                    failedStep.response());
            assertEquals("PRINTER_REPORTED_FAILURE", failedStep.outcome());
            assertFalse(failedStep.success());
            assertEquals(JobFailureReason.PRINTER_REPORTED_FAILURE.name(), failedStep.failureReason());
            assertNotNull(failedStep.failureDetail());
            assertTrue(failedStep.failureDetail().contains("Homing Failed"));
            assertTrue(failedStep.failureDetail().contains("Printer halted"));
        } finally {
            scheduler.stop();
        }
    }

    private void initializeDatabase(String fileName) {
        String databaseFile = tempDir.resolve(fileName).toString();
        System.setProperty(RuntimeDefaults.DATABASE_FILE_PROPERTY, databaseFile);
        new DatabaseInitializer().initialize();
    }

    private static final class SingleResponsePrinterPort implements PrinterPort {

        private final String response;

        private SingleResponsePrinterPort(String response) {
            this.response = response;
        }

        @Override
        public void connect() {
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
        }

        @Override
        public String sendCommand(String command) {
            return response;
        }

        @Override
        public void disconnect() {
        }
    }

    private static final class SequencePrinterPort implements PrinterPort {

        private final String[] responses;
        private int index = 0;

        private SequencePrinterPort(String... responses) {
            this.responses = responses;
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

            java.util.List<String> results = new java.util.ArrayList<>(lines.size());
            for (int i = 0; i < lines.size(); i++) {
                results.add("ok");
            }
            return results;
        }

        @Override
        public void discardPendingInput(int quietPeriodMs, int maxDrainMs) {
        }

        @Override
        public void connect() {
        }

        @Override
        public String sendCommand(String command) {
            if (index >= responses.length) {
                return responses[responses.length - 1];
            }

            String response = responses[index];
            index++;
            return response;
        }

        @Override
        public void disconnect() {
        }
    }

    private static final class CountingPrinterPort implements PrinterPort {

        private int commandCount = 0;
        private final java.util.List<String> commands = new java.util.ArrayList<>();

        @Override
        public void connect() {
        }

        @Override
        public String sendCommand(String command) {
            commandCount++;
            commands.add(command);
            return "ok";
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

            java.util.List<String> results = new java.util.ArrayList<>(lines.size());
            for (int i = 0; i < lines.size(); i++) {
                results.add("ok");
            }
            return results;
        }

        @Override
        public void discardPendingInput(int quietPeriodMs, int maxDrainMs) {
        }

        @Override
        public void disconnect() {
        }

        private int commandCount() {
            return commandCount;
        }

        private List<String> commands() {
            return List.copyOf(commands);
        }
    }

    private static final class ExceptionOnSecondCommandPrinterPort implements PrinterPort {

        private final String firstResponse;
        private final String exceptionMessage;
        private int commandCount = 0;

        private ExceptionOnSecondCommandPrinterPort(
                String firstResponse,
                String exceptionMessage) {
            this.firstResponse = firstResponse;
            this.exceptionMessage = exceptionMessage;
        }

        @Override
        public void connect() {
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

            java.util.List<String> results = new java.util.ArrayList<>(lines.size());
            for (int i = 0; i < lines.size(); i++) {
                results.add("ok");
            }
            return results;
        }

        @Override
        public void discardPendingInput(int quietPeriodMs, int maxDrainMs) {
        }

        @Override
        public String sendCommand(String command) {
            commandCount++;

            if (commandCount == 1) {
                return firstResponse;
            }

            throw new IllegalStateException(exceptionMessage);
        }

        @Override
        public void disconnect() {
        }
    }
}
