package spaghettichef.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import spaghettichef.persistence.CameraEvent;
import spaghettichef.persistence.CameraEventStore;
import spaghettichef.persistence.CameraCalculationResult;
import spaghettichef.persistence.CameraCalculationResultStore;
import spaghettichef.persistence.CameraCalculationRun;
import spaghettichef.persistence.CameraCalculationRunStore;
import spaghettichef.persistence.CameraDeltaFrame;
import spaghettichef.persistence.CameraDeltaFrameStore;
import spaghettichef.persistence.CameraDeltaSet;
import spaghettichef.persistence.CameraDeltaSetStore;
import spaghettichef.persistence.CameraSnapshotEntry;
import spaghettichef.persistence.CameraSnapshotEntryStore;
import spaghettichef.persistence.CameraSettings;
import spaghettichef.persistence.CameraSettingsStore;
import spaghettichef.persistence.CameraSnapshotMetadata;
import spaghettichef.persistence.CameraSnapshotMetadataStore;
import spaghettichef.persistence.DatabaseInitializer;
import spaghettichef.OperationMessages;


class CameraCaptureServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-05-18T10:15:30Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @AfterEach
    void clearDatabaseProperty() {
        System.clearProperty("spaghettichef.databaseFile");
    }

    @Test
    void captureSkipsWhenCameraIsDisabled() {
        useDatabase("camera-capture-disabled.db");

        CameraSettingsService settingsService = settingsService();
        settingsService.disable("printer-1");

        CameraCaptureService service = captureService(settingsService);

        CameraCaptureResult result = service.capture("printer-1");

        assertFalse(result.success());
        assertTrue(result.frame().isEmpty());
        assertEquals("Camera disabled", result.message().orElseThrow());

        List<CameraEvent> events = new CameraEventStore().findRecentByPrinterId("printer-1", 10);
        assertEquals(1, events.size());
        assertEquals(OperationMessages.EVENT_CAMERA_CAPTURE_SKIPPED, events.get(0).eventType());
    }

    @Test
    void captureStoresSimulatedFrameAndMetadata() throws Exception {
        useDatabase("camera-capture-simulated.db");

        CameraSettingsService settingsService = settingsService();
        settingsService.save(withTestStorage(settingsService.enableSimulated("printer-1")));

        CameraCaptureService service = captureService(settingsService);

        CameraCaptureResult result = service.capture("printer-1");

        assertTrue(result.success());
        assertTrue(result.frame().isPresent());
        assertEquals("printer-1", result.frame().get().printerId());
        assertEquals("image/jpeg", result.frame().get().contentType());

        Path printerStorage = tempDir.resolve("camera-storage").resolve("printer-1");
        Path latest = printerStorage.resolve("latest.jpg");

        assertTrue(Files.exists(latest));

        CameraSnapshotMetadata metadata = new CameraSnapshotMetadataStore()
                .findLatestByPrinterId("printer-1")
                .orElseThrow();

        assertEquals("printer-1", metadata.printerId());
        assertEquals(FIXED_INSTANT, metadata.capturedAt());
        assertEquals("image/jpeg", metadata.contentType());
        assertTrue(Files.exists(Path.of(metadata.filePath())));
        assertEquals(320, metadata.width().orElseThrow());
        assertEquals(240, metadata.height().orElseThrow());
        assertEquals("simulated-camera:printer-1", metadata.sourceDescription().orElseThrow());

        List<CameraEvent> events = new CameraEventStore().findRecentByPrinterId("printer-1", 10);
        assertEquals(1, events.size());
        assertEquals(OperationMessages.EVENT_CAMERA_FRAME_CAPTURED, events.get(0).eventType());
    }

    @Test
    void captureStoresSnapshotEntryUnderCameraJobId() throws Exception {
        useDatabase("camera-capture-camera-job.db");

        CameraSettingsService settingsService = settingsService();
        settingsService.save(withTestStorage(settingsService.enableSimulated("printer-1")));

        CameraCaptureService service = captureService(settingsService);

        CameraCaptureResult result = service.capture("printer-1");

        assertTrue(result.success());

        List<CameraSnapshotEntry> entries = new CameraSnapshotEntryStore().findByPrinterIdAndJobId("printer-1", "1");
        assertEquals(1, entries.size());

        CameraSnapshotEntry entry = entries.get(0);
        assertEquals(1L, entry.cameraJobId());
        assertTrue(entry.linkedPrintJobIdOptional().isEmpty());
        assertTrue(entry.snapshotPath().endsWith("/snapshots/1/000001_snapshot.jpg"));
        assertFalse(entry.snapshotPath().contains("2026-05-22T10-15-30Z"));
        assertFalse(entry.snapshotPath().contains("/unassigned/"));
        assertEquals(FIXED_INSTANT, entry.capturedAt());
        assertTrue(Files.exists(Path.of(entry.snapshotPath())));
    }

    @Test
    void captureCreatesLiveDeltaFrameAndCalculationAfterSecondSnapshot() throws Exception {
        useDatabase("camera-capture-live-delta.db");

        CameraSettingsService settingsService = settingsService();
        settingsService.save(withAnalysisEnabled(withTestStorage(settingsService.enableSimulated("printer-1"))));

        CameraCaptureService service = captureService(settingsService);

        assertTrue(service.capture("printer-1").success());
        assertTrue(new CameraDeltaSetStore().findByCameraJobId(1L).isEmpty());

        assertTrue(service.capture("printer-1").success());

        List<CameraDeltaSet> deltaSets = new CameraDeltaSetStore().findByCameraJobId(1L);
        assertEquals(1, deltaSets.size());
        assertEquals("live-image-delta", deltaSets.get(0).methodName());
        assertEquals(2, deltaSets.get(0).sourceSnapshotCount());
        assertEquals(1, deltaSets.get(0).generatedDeltaCount());

        List<CameraDeltaFrame> frames = new CameraDeltaFrameStore()
                .findByDeltaSetId(deltaSets.get(0).requireId());
        assertEquals(1, frames.size());
        assertEquals(1L, frames.get(0).fromSnapshotId());
        assertEquals(2L, frames.get(0).toSnapshotId());
        assertTrue(frames.get(0).deltaPath().contains("/deltas/1/" + deltaSets.get(0).requireId() + "/"));
        assertTrue(Files.isRegularFile(tempDir.resolve("camera-storage/printer-1/snapshots/1/000001_snapshot.jpg")));
        assertTrue(Files.isRegularFile(tempDir.resolve("camera-storage/printer-1/snapshots/1/000002_snapshot.jpg")));
        assertTrue(frames.get(0).deltaPath().endsWith("/000001_000002_delta.jpg"));
        assertFalse(frames.get(0).deltaPath().endsWith("/delta.jpg"));
        assertTrue(Files.isRegularFile(Path.of(frames.get(0).deltaPath())));

        List<CameraCalculationRun> runs = new CameraCalculationRunStore()
                .findByDeltaSetId(deltaSets.get(0).requireId());
        assertEquals(1, runs.size());
        assertEquals("live-spaghetti-delta-threshold", runs.get(0).methodName());
        assertEquals(1, runs.get(0).resultCount());

        List<CameraCalculationResult> results = new CameraCalculationResultStore()
                .findByCalculationRunId(runs.get(0).requireId());
        assertEquals(1, results.size());
        assertEquals(frames.get(0).requireId(), results.get(0).deltaFrameId());
    }

    @Test
    void captureUsesSnapshotFolderSource() throws Exception {
        useDatabase("camera-capture-folder.db");

        Path sourceFolder = Files.createDirectory(tempDir.resolve("source-camera"));
        writeImage(sourceFolder.resolve("snapshot.png"), "png", 80, 60);

        CameraSettingsService settingsService = settingsService();
        settingsService.save(withTestStorage(settingsService.enableSnapshotFolder("printer-1", sourceFolder)));

        CameraCaptureService service = captureService(settingsService);

        CameraCaptureResult result = service.capture("printer-1");

        assertTrue(result.success());
        assertEquals("image/png", result.frame().orElseThrow().contentType());
        assertEquals(80, result.frame().orElseThrow().width().orElseThrow());
        assertEquals(60, result.frame().orElseThrow().height().orElseThrow());

        Path latest = tempDir.resolve("camera-storage")
                .resolve("printer-1")
                .resolve("latest.png");

        assertTrue(Files.exists(latest));

        CameraSnapshotMetadata metadata = new CameraSnapshotMetadataStore()
                .findLatestByPrinterId("printer-1")
                .orElseThrow();

        assertEquals("image/png", metadata.contentType());
        assertTrue(metadata.filePath().endsWith(".png"));
    }

    @Test
    void captureFailsWhenSnapshotFolderIsMissing() {
        useDatabase("camera-capture-folder-missing.db");

        Path missingFolder = tempDir.resolve("missing-camera");

        CameraSettingsService settingsService = settingsService();
        settingsService.enableSnapshotFolder("printer-1", missingFolder);

        CameraCaptureService service = captureService(settingsService);

        CameraCaptureResult result = service.capture("printer-1");

        assertFalse(result.success());
        assertTrue(result.frame().isEmpty());
        assertEquals("Camera unavailable", result.message().orElseThrow());

        List<CameraEvent> events = new CameraEventStore().findRecentByPrinterId("printer-1", 10);
        assertEquals(1, events.size());
        assertEquals(OperationMessages.EVENT_CAMERA_CAPTURE_FAILED, events.get(0).eventType());
    }

    @Test
    void statusReturnsDisabledWhenCameraDisabled() {
        useDatabase("camera-status-disabled.db");

        CameraSettingsService settingsService = settingsService();
        settingsService.disable("printer-1");

        CameraCaptureService service = captureService(settingsService);

        CameraStatus status = service.status("printer-1");

        assertEquals("printer-1", status.printerId());
        assertFalse(status.enabled());
        assertFalse(status.available());
        assertEquals(CameraSourceType.DISABLED, status.sourceType());
    }

    @Test
    void statusReturnsAvailableForSimulatedCamera() {
        useDatabase("camera-status-simulated.db");

        CameraSettingsService settingsService = settingsService();
        settingsService.save(withTestStorage(settingsService.enableSimulated("printer-1")));

        CameraCaptureService service = captureService(settingsService);

        CameraStatus status = service.status("printer-1");

        assertEquals("printer-1", status.printerId());
        assertTrue(status.enabled());
        assertTrue(status.available());
        assertEquals(CameraSourceType.SIMULATED, status.sourceType());
        assertEquals("simulated-camera:printer-1", status.sourceDescription().orElseThrow());
    }

    @Test
    void statusIncludesLastCaptureAtWhenSnapshotExists() {
        useDatabase("camera-status-last-capture.db");

        CameraSettingsService settingsService = settingsService();
        settingsService.save(withTestStorage(settingsService.enableSimulated("printer-1")));

        CameraCaptureService service = captureService(settingsService);
        service.capture("printer-1");

        CameraStatus status = service.status("printer-1");

        assertTrue(status.available());
        assertEquals(FIXED_INSTANT, status.lastCaptureAt().orElseThrow());
    }

    @Test
    void captureFailsForBlankPrinterId() {
        useDatabase("camera-capture-blank-printer.db");

        CameraCaptureService service = captureService(settingsService());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.capture("   "));

        assertEquals("printerId must not be blank", exception.getMessage());
    }

    @Test
    void statusFailsForBlankPrinterId() {
        useDatabase("camera-status-blank-printer.db");

        CameraCaptureService service = captureService(settingsService());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.status("   "));

        assertEquals("printerId must not be blank", exception.getMessage());
    }

    @Test
    void constructorFailsForNullSettingsService() {
        assertThrows(
                NullPointerException.class,
                () -> new CameraCaptureService(
                        null,
                        new CameraEventStore(),
                        new CameraSnapshotMetadataStore(),
                        tempDir,
                        FIXED_CLOCK));
    }

    @Test
    void constructorFailsForNullEventStore() {
        assertThrows(
                NullPointerException.class,
                () -> new CameraCaptureService(
                        settingsService(),
                        null,
                        new CameraSnapshotMetadataStore(),
                        tempDir,
                        FIXED_CLOCK));
    }

    @Test
    void constructorFailsForNullSnapshotMetadataStore() {
        assertThrows(
                NullPointerException.class,
                () -> new CameraCaptureService(
                        settingsService(),
                        new CameraEventStore(),
                        null,
                        tempDir,
                        FIXED_CLOCK));
    }

    @Test
    void constructorFailsForNullStorageDirectory() {
        assertThrows(
                NullPointerException.class,
                () -> new CameraCaptureService(
                        settingsService(),
                        new CameraEventStore(),
                        new CameraSnapshotMetadataStore(),
                        null,
                        FIXED_CLOCK));
    }

    @Test
    void constructorFailsForNullClock() {
        assertThrows(
                NullPointerException.class,
                () -> new CameraCaptureService(
                        settingsService(),
                        new CameraEventStore(),
                        new CameraSnapshotMetadataStore(),
                        tempDir,
                        null));
    }

    private CameraSettingsService settingsService() {
        return new CameraSettingsService(new CameraSettingsStore(), FIXED_CLOCK);
    }

    private CameraCaptureService captureService(CameraSettingsService settingsService) {
        return new CameraCaptureService(
                settingsService,
                new CameraEventStore(),
                new CameraSnapshotMetadataStore(),
                tempDir.resolve("camera-storage"),
                FIXED_CLOCK);
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

    private CameraSettings withAnalysisEnabled(CameraSettings settings) {
        return new CameraSettings(
                settings.printerId(),
                settings.enabled(),
                settings.sourceType(),
                settings.sourceValue().orElse(null),
                settings.captureIntervalSeconds(),
                settings.retentionSnapshotCount(),
                true,
                settings.safetyEnabled(),
                settings.pauseOnConfirmedSpaghetti(),
                settings.confidenceThreshold(),
                settings.confirmationsRequired(),
                settings.ffmpegCommand(),
                settings.ffmpegInputFormat().orElse(null),
                settings.ffmpegVideoSize().orElse(null),
                settings.ffmpegTimeoutMs(),
                settings.ffmpegJpegQuality(),
                settings.storageDirectory(),
                settings.diagnosticLoggingEnabled(),
                settings.updatedAt());
    }

    private void useDatabase(String fileName) {
        Path dbFile = tempDir.resolve(fileName);
        System.setProperty("spaghettichef.databaseFile", dbFile.toString());
        new DatabaseInitializer().initialize();
    }

    private static void writeImage(Path path, String format, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean border = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                image.setRGB(x, y, border ? 0x000000 : 0xFFFFFF);
            }
        }

        boolean written = ImageIO.write(image, format, path.toFile());
        if (!written) {
            throw new IllegalStateException("No image writer available for format: " + format);
        }
    }
}
