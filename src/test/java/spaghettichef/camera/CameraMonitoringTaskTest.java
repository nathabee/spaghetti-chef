package spaghettichef.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import spaghettichef.persistence.CameraEventStore;
import spaghettichef.persistence.CameraJob;
import spaghettichef.persistence.CameraJobStore;
import spaghettichef.persistence.CameraSettings;
import spaghettichef.persistence.CameraSettingsStore;
import spaghettichef.persistence.CameraSnapshotMetadataStore;
import spaghettichef.persistence.DatabaseInitializer;

class CameraMonitoringTaskTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-05-18T10:15:30Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @AfterEach
    void clearDatabaseProperty() {
        System.clearProperty("spaghettichef.databaseFile");
    }

    @Test
    void runCapturesFrameForEnabledSimulatedCamera() {
        useDatabase("camera-monitoring-task-capture.db");

        CameraSettingsService settingsService = settingsService();
        settingsService.save(withTestStorage(settingsService.enableSimulated("printer-1")));
        long cameraJobId = createRunningCameraJob("printer-1");

        CameraMonitoringTask task = new CameraMonitoringTask(
                "printer-1",
                cameraJobId,
                monitoringService(settingsService),
                FIXED_CLOCK);

        task.run();

        assertEquals("printer-1", task.printerId());
        assertEquals(cameraJobId, task.cameraJobId());
        assertEquals(FIXED_INSTANT, task.lastRunAt().orElseThrow());
        assertTrue(task.lastResult().orElseThrow().success());
        assertTrue(task.lastResult().orElseThrow().frame().isPresent());
        assertTrue(task.lastFailure().isEmpty());
    }

    @Test
    void runStoresSkippedResultForDisabledCamera() {
        useDatabase("camera-monitoring-task-disabled.db");

        CameraSettingsService settingsService = settingsService();
        settingsService.disable("printer-1");
        long cameraJobId = createRunningCameraJob("printer-1");

        CameraMonitoringTask task = new CameraMonitoringTask(
                "printer-1",
                cameraJobId,
                monitoringService(settingsService),
                FIXED_CLOCK);

        task.run();

        assertEquals(FIXED_INSTANT, task.lastRunAt().orElseThrow());
        assertTrue(task.lastResult().isPresent());
        assertEquals("Camera disabled", task.lastResult().orElseThrow().message().orElseThrow());
        assertTrue(task.lastFailure().isEmpty());
    }

    @Test
    void constructorTrimsPrinterId() {
        useDatabase("camera-monitoring-task-trim.db");

        long cameraJobId = createRunningCameraJob("printer-1");

        CameraMonitoringTask task = new CameraMonitoringTask(
                "  printer-1  ",
                cameraJobId,
                monitoringService(settingsService()),
                FIXED_CLOCK);

        assertEquals("printer-1", task.printerId());
        assertEquals(cameraJobId, task.cameraJobId());
    }

    @Test
    void constructorRejectsNonPositiveCameraJobId() {
        useDatabase("camera-monitoring-task-invalid-job-id.db");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new CameraMonitoringTask(
                        "printer-1",
                        0L,
                        monitoringService(settingsService()),
                        FIXED_CLOCK));

        assertEquals("cameraJobId must be greater than zero", exception.getMessage());
    }

    @Test
    void runKeepsFailureWhenMonitoringServiceCannotUseDatabase() {
        System.setProperty("spaghettichef.databaseFile", tempDir.resolve("not-a-db-dir").toString());

        try {
            java.nio.file.Files.createDirectories(tempDir.resolve("not-a-db-dir"));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }

        CameraMonitoringTask task = new CameraMonitoringTask(
                "printer-1",
                1L,
                monitoringService(settingsService()),
                FIXED_CLOCK);

        task.run();

        assertEquals(FIXED_INSTANT, task.lastRunAt().orElseThrow());
        assertTrue(task.lastResult().isEmpty());
        assertTrue(task.lastFailure().isPresent());
    }

    private CameraSettingsService settingsService() {
        return new CameraSettingsService(new CameraSettingsStore(), FIXED_CLOCK);
    }

    private CameraMonitoringService monitoringService(CameraSettingsService settingsService) {
        CameraCaptureService captureService = new CameraCaptureService(
                settingsService,
                new CameraEventStore(),
                new CameraSnapshotMetadataStore(),
                tempDir.resolve("camera-storage"),
                FIXED_CLOCK);

        return new CameraMonitoringService(captureService);
    }

    private long createRunningCameraJob(String printerId) {
        CameraJob job = new CameraJobStore().save(CameraJob.running(
                printerId,
                null,
                null,
                FIXED_INSTANT,
                10,
                20,
                "simulated",
                "default",
                tempDir.resolve("camera-storage")
                        .resolve(printerId)
                        .resolve("snapshots")
                        .resolve("pending")
                        .toString(),
                "test camera job"));

        return job.requireId();
    }

    private CameraSettings withTestStorage(CameraSettings settings) {
        return new CameraSettings(
                settings.printerId(),
                settings.enabled(),
                settings.sourceType(),
                settings.sourceValue().orElse(null),
                settings.captureIntervalSeconds(),
                settings.retentionSnapshotCount(),
                settings.analysisEnabled(),
                settings.safetyEnabled(),
                settings.pauseOnConfirmedSpaghetti(),
                settings.confidenceThreshold(),
                settings.confirmationsRequired(),
                settings.ffmpegCommand(),
                settings.ffmpegInputFormat().orElse(null),
                settings.ffmpegVideoSize().orElse(null),
                settings.ffmpegTimeoutMs(),
                settings.ffmpegJpegQuality(),
                tempDir.resolve("camera-storage").toString(),
                settings.updatedAt());
    }

    private void useDatabase(String fileName) {
        Path dbFile = tempDir.resolve(fileName);
        System.setProperty("spaghettichef.databaseFile", dbFile.toString());
        new DatabaseInitializer().initialize();
    }
}