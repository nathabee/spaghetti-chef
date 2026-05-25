package spaghettichef.job;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spaghettichef.OperationMessages;
import spaghettichef.config.RuntimeDefaults;
import spaghettichef.persistence.DatabaseInitializer;
import spaghettichef.persistence.PrintJobStore;
import spaghettichef.persistence.PrinterEvent;
import spaghettichef.persistence.PrinterEventStore;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PrintJobServiceTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        System.clearProperty(RuntimeDefaults.DATABASE_FILE_PROPERTY);
    }

    @Test
    void createWithPrinterStartsAsAssigned() {
        initializeDatabase("job-service-create-assigned.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        Clock clock = Clock.fixed(Instant.parse("2026-05-04T08:00:00Z"), ZoneOffset.UTC);

        PrintJobService service = new PrintJobService(store, eventStore, clock);

        PrintJob job = service.create(
                "Preheat nozzle",
                JobType.SET_NOZZLE_TEMPERATURE,
                "printer-1",
                200.0,
                null
        );

        assertEquals(JobState.ASSIGNED, job.state());
        assertEquals("printer-1", job.printerId());
        assertEquals(200.0, job.targetTemperature());

        PrintJob loaded = store.findById(job.id()).orElseThrow();
        assertEquals(JobState.ASSIGNED, loaded.state());

        List<PrinterEvent> events = eventStore.findRecentByPrinterId("printer-1", 10);
        assertEquals(2, events.size());
    }

    @Test
    void createWithoutPrinterStartsAsCreated() {
        initializeDatabase("job-service-create-created.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        Clock clock = Clock.fixed(Instant.parse("2026-05-04T08:00:00Z"), ZoneOffset.UTC);

        PrintJobService service = new PrintJobService(store, eventStore, clock);

        PrintJob job = service.create(
                "Read firmware",
                JobType.READ_FIRMWARE_INFO,
                null,
                null,
                null
        );

        assertEquals(JobState.CREATED, job.state());
        assertNull(job.printerId());

        PrintJob loaded = store.findById(job.id()).orElseThrow();
        assertEquals(JobState.CREATED, loaded.state());
    }

    @Test
    void createPrintFileJobRequiresPrinterSdFileReference() {
        initializeDatabase("job-service-print-file-requires-sd-file.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        Clock clock = Clock.fixed(Instant.parse("2026-05-04T08:00:00Z"), ZoneOffset.UTC);

        PrintJobService service = new PrintJobService(store, eventStore, clock);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(
                        "Print cube",
                        JobType.PRINT_FILE,
                        "printer-1",
                        "print-file-1",
                        null,
                        null,
                        null));

        assertEquals("printerSdFileId must not be blank", exception.getMessage());
    }

    @Test
    void markRunningSetsRunningStateAndStartedAt() {
        initializeDatabase("job-service-running.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        Clock clock = Clock.fixed(Instant.parse("2026-05-04T08:10:00Z"), ZoneOffset.UTC);

        PrintJobService service = new PrintJobService(store, eventStore, clock);

        PrintJob created = service.create(
                "Home axes",
                JobType.HOME_AXES,
                "printer-1",
                null,
                null
        );

        PrintJob running = service.markRunning(created.id());

        assertEquals(JobState.RUNNING, running.state());
        assertEquals(Instant.parse("2026-05-04T08:10:00Z"), running.startedAt());
    }

    @Test
    void markCompletedSetsCompletedStateAndFinishedAt() {
        initializeDatabase("job-service-completed.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();

        PrintJobService createService = new PrintJobService(
                store,
                eventStore,
                Clock.fixed(Instant.parse("2026-05-04T08:00:00Z"), ZoneOffset.UTC)
        );

        PrintJob job = createService.create(
                "Read temperature",
                JobType.READ_TEMPERATURE,
                "printer-1",
                null,
                null
        );

        PrintJobService runningService = new PrintJobService(
                store,
                eventStore,
                Clock.fixed(Instant.parse("2026-05-04T08:01:00Z"), ZoneOffset.UTC)
        );
        runningService.markRunning(job.id());

        PrintJobService completedService = new PrintJobService(
                store,
                eventStore,
                Clock.fixed(Instant.parse("2026-05-04T08:02:00Z"), ZoneOffset.UTC)
        );

        PrintJob completed = completedService.markCompleted(job.id());

        assertEquals(JobState.COMPLETED, completed.state());
        assertEquals(Instant.parse("2026-05-04T08:02:00Z"), completed.finishedAt());
    }

    @Test
    void markFailedSetsFailureFields() {
        initializeDatabase("job-service-failed.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();

        PrintJobService createService = new PrintJobService(
                store,
                eventStore,
                Clock.fixed(Instant.parse("2026-05-04T08:00:00Z"), ZoneOffset.UTC)
        );

        PrintJob job = createService.create(
                "Home axes",
                JobType.HOME_AXES,
                "printer-1",
                null,
                null
        );

        PrintJobService failedService = new PrintJobService(
                store,
                eventStore,
                Clock.fixed(Instant.parse("2026-05-04T08:03:00Z"), ZoneOffset.UTC)
        );

        PrintJob failed = failedService.markFailed(
                job.id(),
                JobFailureReason.TIMEOUT,
                "No response for command G28"
        );

        assertEquals(JobState.FAILED, failed.state());
        assertEquals("TIMEOUT", failed.failureReason());
        assertEquals("No response for command G28", failed.failureDetail());
        assertEquals(Instant.parse("2026-05-04T08:03:00Z"), failed.finishedAt());
    }

    @Test
    void cancelSetsCancelledState() {
        initializeDatabase("job-service-cancel.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        Clock clock = Clock.fixed(Instant.parse("2026-05-04T08:04:00Z"), ZoneOffset.UTC);

        PrintJobService service = new PrintJobService(store, eventStore, clock);

        PrintJob job = service.create(
                "Fan off",
                JobType.TURN_FAN_OFF,
                "printer-1",
                null,
                null
        );

        PrintJob cancelled = service.cancel(job.id());

        assertEquals(JobState.CANCELLED, cancelled.state());
        assertEquals(Instant.parse("2026-05-04T08:04:00Z"), cancelled.finishedAt());
    }

    @Test
    void cancelRejectsCompletedJob() {
        initializeDatabase("job-service-cancel-completed.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        Clock clock = Clock.fixed(Instant.parse("2026-05-04T08:04:00Z"), ZoneOffset.UTC);
        PrintJobService service = new PrintJobService(store, eventStore, clock);

        PrintJob job = service.create(
                "Read temperature",
                JobType.READ_TEMPERATURE,
                "printer-1",
                null,
                null
        );
        service.markCompleted(job.id());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.cancel(job.id())
        );

        assertEquals(OperationMessages.INVALID_JOB_STATE, exception.getMessage());
        assertEquals(JobState.COMPLETED, store.findById(job.id()).orElseThrow().state());
    }

    @Test
    void deleteRemovesExistingJob() {
        initializeDatabase("job-service-delete.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        Clock clock = Clock.fixed(Instant.parse("2026-05-04T08:05:00Z"), ZoneOffset.UTC);
        PrintJobService service = new PrintJobService(store, eventStore, clock);

        PrintJob job = service.create(
                "Read position",
                JobType.READ_POSITION,
                "printer-1",
                null,
                null
        );

        service.delete(job.id());

        assertTrue(store.findById(job.id()).isEmpty());
    }

    private void initializeDatabase(String fileName) {
        String databaseFile = tempDir.resolve(fileName).toString();
        System.setProperty(RuntimeDefaults.DATABASE_FILE_PROPERTY, databaseFile);
        new DatabaseInitializer().initialize();
    }
}
