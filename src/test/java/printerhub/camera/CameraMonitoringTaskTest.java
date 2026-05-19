package printerhub.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import printerhub.persistence.CameraEventStore;
import printerhub.persistence.CameraSettings;
import printerhub.persistence.CameraSettingsStore;
import printerhub.persistence.CameraSnapshotMetadataStore;
import printerhub.persistence.DatabaseInitializer;

class CameraMonitoringTaskTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-05-18T10:15:30Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @AfterEach
    void clearDatabaseProperty() {
        System.clearProperty("printerhub.databaseFile");
    }

    @Test
    void runCapturesFrameForEnabledSimulatedCamera() {
        useDatabase("camera-monitoring-task-capture.db");

        CameraSettingsService settingsService = settingsService();
        settingsService.save(withTestStorage(settingsService.enableSimulated("printer-1")));

        CameraMonitoringTask task = new CameraMonitoringTask(
                "printer-1",
                monitoringService(settingsService),
                FIXED_CLOCK);

        task.run();

        assertEquals("printer-1", task.printerId());
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

        CameraMonitoringTask task = new CameraMonitoringTask(
                "printer-1",
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

        CameraMonitoringTask task = new CameraMonitoringTask(
                "  printer-1  ",
                monitoringService(settingsService()),
                FIXED_CLOCK);

        assertEquals("printer-1", task.printerId());
    }

    @Test
    void runKeepsFailureWhenMonitoringServiceCannotUseDatabase() {
        System.setProperty("printerhub.databaseFile", tempDir.resolve("not-a-db-dir").toString());

        try {
            java.nio.file.Files.createDirectories(tempDir.resolve("not-a-db-dir"));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }

        CameraMonitoringTask task = new CameraMonitoringTask(
                "printer-1",
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
        System.setProperty("printerhub.databaseFile", dbFile.toString());
        new DatabaseInitializer().initialize();
    }
}
