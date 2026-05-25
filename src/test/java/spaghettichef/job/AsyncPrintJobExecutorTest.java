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
import spaghettichef.persistence.PrintJobExecutionStepStore;
import spaghettichef.persistence.PrintJobStore;
import spaghettichef.persistence.PrinterEvent;
import spaghettichef.persistence.PrinterEventStore;
import spaghettichef.runtime.PrinterRegistry;
import spaghettichef.runtime.PrinterRuntimeNode;
import spaghettichef.runtime.PrinterRuntimeStateCache;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AsyncPrintJobExecutorTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        System.clearProperty(RuntimeDefaults.DATABASE_FILE_PROPERTY);
    }

    @Test
    void startReturnsRunningJobAndCompletesInBackground() throws Exception {
        initializeDatabase("async-job-success.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        PrintJobService jobService = new PrintJobService(
                store,
                eventStore,
                Clock.fixed(Instant.parse("2026-05-06T08:00:00Z"), ZoneOffset.UTC));

        PrinterRegistry registry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(registry, stateCache);
        BlockingPrinterPort printerPort = new BlockingPrinterPort("ok FIRMWARE_NAME:Marlin");

        try (AsyncPrintJobExecutor executor = createExecutor(jobService, registry, scheduler)) {
            registry.register(new PrinterRuntimeNode(
                    "printer-1",
                    "Printer 1",
                    "SIM_PORT",
                    "sim",
                    printerPort,
                    true));

            PrintJob job = jobService.create(
                    "Read firmware",
                    JobType.READ_FIRMWARE_INFO,
                    "printer-1",
                    null,
                    null);

            AsyncPrintJobExecutor.StartResult result = executor.start(job.id());

            assertTrue(result.accepted());
            assertEquals(JobState.RUNNING, result.job().state());
            assertTrue(printerPort.awaitCommandStarted());
            assertEquals(JobState.RUNNING, store.findById(job.id()).orElseThrow().state());

            printerPort.release();

            PrintJob completed = waitForState(store, job.id(), JobState.COMPLETED);
            assertEquals(JobState.COMPLETED, completed.state());

            List<PrinterEvent> events = eventStore.findRecentByJobId(job.id(), 20);
            assertTrue(events.stream()
                    .anyMatch(event -> OperationMessages.EVENT_JOB_EXECUTION_QUEUED.equals(event.eventType())));
            assertTrue(events.stream()
                    .anyMatch(event -> OperationMessages.EVENT_JOB_EXECUTION_SUCCEEDED.equals(event.eventType())));
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void startRejectsSecondJobForSamePrinterWhileFirstJobIsRunning() throws Exception {
        initializeDatabase("async-job-busy.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        PrintJobService jobService = new PrintJobService(
                store,
                eventStore,
                Clock.fixed(Instant.parse("2026-05-06T08:00:00Z"), ZoneOffset.UTC));

        PrinterRegistry registry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(registry, stateCache);
        BlockingPrinterPort printerPort = new BlockingPrinterPort("ok FIRMWARE_NAME:Marlin");

        try (AsyncPrintJobExecutor executor = createExecutor(jobService, registry, scheduler)) {
            registry.register(new PrinterRuntimeNode(
                    "printer-1",
                    "Printer 1",
                    "SIM_PORT",
                    "sim",
                    printerPort,
                    true));

            PrintJob firstJob = jobService.create(
                    "Read firmware 1",
                    JobType.READ_FIRMWARE_INFO,
                    "printer-1",
                    null,
                    null);
            PrintJob secondJob = jobService.create(
                    "Read firmware 2",
                    JobType.READ_FIRMWARE_INFO,
                    "printer-1",
                    null,
                    null);

            AsyncPrintJobExecutor.StartResult firstResult = executor.start(firstJob.id());
            assertTrue(firstResult.accepted());
            assertTrue(printerPort.awaitCommandStarted());

            AsyncPrintJobExecutor.StartResult secondResult = executor.start(secondJob.id());

            assertFalse(secondResult.accepted());
            assertEquals(JobFailureReason.PRINTER_BUSY, secondResult.failureReason());
            assertEquals(JobState.FAILED, store.findById(secondJob.id()).orElseThrow().state());

            printerPort.release();
            waitForState(store, firstJob.id(), JobState.COMPLETED);
        } finally {
            scheduler.stop();
        }
    }

    private AsyncPrintJobExecutor createExecutor(
            PrintJobService jobService,
            PrinterRegistry registry,
            PrinterMonitoringScheduler scheduler) {
        PrinterActionGuard printerActionGuard = new PrinterActionGuard();
        PrintJobExecutionService executionService = new PrintJobExecutionService(
                jobService,
                registry,
                scheduler,
                printerActionGuard,
                new PrinterActionMapper(),
                new PrintJobExecutionStepStore());

        return new AsyncPrintJobExecutor(
                jobService,
                registry,
                printerActionGuard,
                executionService,
                2);
    }

    private PrintJob waitForState(PrintJobStore store, String jobId, JobState expectedState) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000L;

        while (System.currentTimeMillis() < deadline) {
            PrintJob job = store.findById(jobId).orElseThrow();

            if (job.state() == expectedState) {
                return job;
            }

            Thread.sleep(50L);
        }

        fail("Timed out waiting for job " + jobId + " to reach state " + expectedState);
        return store.findById(jobId).orElseThrow();
    }

    private void initializeDatabase(String fileName) {
        String databaseFile = tempDir.resolve(fileName).toString();
        System.setProperty(RuntimeDefaults.DATABASE_FILE_PROPERTY, databaseFile);
        new DatabaseInitializer().initialize();
    }

    private static final class BlockingPrinterPort implements PrinterPort {

        private final String response;
        private final CountDownLatch commandStarted = new CountDownLatch(1);
        private final CountDownLatch releaseCommand = new CountDownLatch(1);
        private final List<String> pendingRawResponses = new ArrayList<>();
        private boolean connected;

        private BlockingPrinterPort(String response) {
            this.response = response;
        }

        @Override
        public void connect() {
            connected = true;
        }

        @Override
        public String sendCommand(String command) {
            ensureConnected();
            commandStarted.countDown();

            try {
                releaseCommand.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }

            return response;
        }

        @Override
        public String sendRawLine(String line) {
            return sendRawLine(line, SerialIOMode.COMMAND_RESPONSE);
        }

        @Override
        public String sendRawLine(String line, SerialIOMode mode) {
            ensureConnected();

            if (!pendingRawResponses.isEmpty()) {
                return pendingRawResponses.remove(0);
            }

            return "ok";
        }

        @Override
        public void writeRawLine(String line, SerialIOMode mode) {
            ensureConnected();
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
        public List<String> sendRawLinesPipelined(List<String> lines, SerialIOMode mode) {
            ensureConnected();

            if (lines == null || lines.isEmpty()) {
                return List.of();
            }

            List<String> responses = new ArrayList<>(lines.size());
            for (int i = 0; i < lines.size(); i++) {
                if (!pendingRawResponses.isEmpty()) {
                    responses.add(pendingRawResponses.remove(0));
                } else {
                    responses.add("ok");
                }
            }

            return responses;
        }

        @Override
        public void discardPendingInput(int quietPeriodMs, int maxDrainMs) {
            ensureConnected();
            pendingRawResponses.clear();
        }

        @Override
        public void disconnect() {
            connected = false;
            pendingRawResponses.clear();
        }

        private boolean awaitCommandStarted() throws InterruptedException {
            return commandStarted.await(5, TimeUnit.SECONDS);
        }

        private void release() {
            releaseCommand.countDown();
        }

        private void ensureConnected() {
            if (!connected) {
                throw new IllegalStateException("not connected");
            }
        }
    }
}