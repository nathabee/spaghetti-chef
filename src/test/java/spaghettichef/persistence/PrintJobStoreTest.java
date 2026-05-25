package spaghettichef.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spaghettichef.config.RuntimeDefaults;
import spaghettichef.job.JobState;
import spaghettichef.job.JobType;
import spaghettichef.job.PrintJob;
import spaghettichef.job.PrintJobExecutionStep;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PrintJobStoreTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        System.clearProperty(RuntimeDefaults.DATABASE_FILE_PROPERTY);
    }

    @Test
    void saveAndFindByIdPersistsAllMainFields() {
        initializeDatabase("print-job-save.db");

        PrintJobStore store = new PrintJobStore();
        Instant now = Instant.parse("2026-05-04T08:00:00Z");

        PrintJob job = new PrintJob(
                "job-1",
                "Preheat nozzle",
                JobType.SET_NOZZLE_TEMPERATURE,
                JobState.ASSIGNED,
                "printer-1",
                200.0,
                null,
                null,
                null,
                now,
                now,
                null,
                null
        );

        store.save(job);

        Optional<PrintJob> loaded = store.findById("job-1");

        assertTrue(loaded.isPresent());
        assertEquals("job-1", loaded.get().id());
        assertEquals("Preheat nozzle", loaded.get().name());
        assertEquals(JobType.SET_NOZZLE_TEMPERATURE, loaded.get().type());
        assertEquals(JobState.ASSIGNED, loaded.get().state());
        assertEquals("printer-1", loaded.get().printerId());
        assertEquals(200.0, loaded.get().targetTemperature());
        assertNull(loaded.get().fanSpeed());
        assertEquals(now, loaded.get().createdAt());
        assertEquals(now, loaded.get().updatedAt());
        assertNull(loaded.get().startedAt());
        assertNull(loaded.get().finishedAt());
    }

    @Test
    void saveAndUpdatePersistsLifecycleAndFailureFields() {
        initializeDatabase("print-job-update.db");

        PrintJobStore store = new PrintJobStore();
        Instant createdAt = Instant.parse("2026-05-04T08:00:00Z");
        Instant startedAt = Instant.parse("2026-05-04T08:01:00Z");
        Instant finishedAt = Instant.parse("2026-05-04T08:02:00Z");

        PrintJob job = new PrintJob(
                "job-2",
                "Home axes",
                JobType.HOME_AXES,
                JobState.ASSIGNED,
                "printer-1",
                null,
                null,
                null,
                null,
                createdAt,
                createdAt,
                null,
                null
        );

        store.save(job);

        PrintJob failedJob = new PrintJob(
                "job-2",
                "Home axes",
                JobType.HOME_AXES,
                JobState.FAILED,
                "printer-1",
                null,
                null,
                "TIMEOUT",
                "No response for command G28",
                createdAt,
                finishedAt,
                startedAt,
                finishedAt
        );

        store.update(failedJob);

        PrintJob loaded = store.findById("job-2").orElseThrow();

        assertEquals(JobState.FAILED, loaded.state());
        assertEquals("TIMEOUT", loaded.failureReason());
        assertEquals("No response for command G28", loaded.failureDetail());
        assertEquals(startedAt, loaded.startedAt());
        assertEquals(finishedAt, loaded.finishedAt());
    }

    @Test
    void findRecentReturnsNewestJobsFirst() {
        initializeDatabase("print-job-recent.db");

        PrintJobStore store = new PrintJobStore();

        Instant t1 = Instant.parse("2026-05-04T08:00:00Z");
        Instant t2 = Instant.parse("2026-05-04T08:05:00Z");
        Instant t3 = Instant.parse("2026-05-04T08:10:00Z");

        store.save(new PrintJob(
                "job-a",
                "Job A",
                JobType.READ_TEMPERATURE,
                JobState.COMPLETED,
                "printer-1",
                null,
                null,
                null,
                null,
                t1,
                t1,
                null,
                null
        ));

        store.save(new PrintJob(
                "job-b",
                "Job B",
                JobType.READ_POSITION,
                JobState.COMPLETED,
                "printer-1",
                null,
                null,
                null,
                null,
                t2,
                t2,
                null,
                null
        ));

        store.save(new PrintJob(
                "job-c",
                "Job C",
                JobType.READ_FIRMWARE_INFO,
                JobState.COMPLETED,
                "printer-1",
                null,
                null,
                null,
                null,
                t3,
                t3,
                null,
                null
        ));

        List<PrintJob> recent = store.findRecent(2);

        assertEquals(2, recent.size());
        assertEquals("job-c", recent.get(0).id());
        assertEquals("job-b", recent.get(1).id());
    }

    @Test
    void deleteRemovesJobAndJobScopedDiagnostics() {
        initializeDatabase("print-job-delete.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        PrintJobExecutionStepStore stepStore = new PrintJobExecutionStepStore();
        Instant now = Instant.parse("2026-05-04T08:00:00Z");

        store.save(new PrintJob(
                "job-delete",
                "Delete me",
                JobType.READ_TEMPERATURE,
                JobState.ASSIGNED,
                "printer-1",
                null,
                null,
                null,
                null,
                now,
                now,
                null,
                null
        ));
        eventStore.record("printer-1", "job-delete", "JOB_CREATED", "Job created.");
        stepStore.save(PrintJobExecutionStep.success(
                "job-delete",
                0,
                "read-temperature",
                "M105",
                "ok",
                "SUCCESS"));

        assertTrue(store.delete("job-delete"));

        assertTrue(store.findById("job-delete").isEmpty());
        assertTrue(eventStore.findRecentByJobId("job-delete", 10).isEmpty());
        assertTrue(stepStore.findByJobId("job-delete").isEmpty());
    }

    @Test
    void deleteReturnsFalseWhenJobIsMissing() {
        initializeDatabase("print-job-delete-missing.db");

        PrintJobStore store = new PrintJobStore();

        assertFalse(store.delete("missing-job"));
    }

    private void initializeDatabase(String fileName) {
        String databaseFile = tempDir.resolve(fileName).toString();
        System.setProperty(RuntimeDefaults.DATABASE_FILE_PROPERTY, databaseFile);
        new DatabaseInitializer().initialize();
    }
}
