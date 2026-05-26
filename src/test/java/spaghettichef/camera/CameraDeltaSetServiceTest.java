package spaghettichef.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.net.URISyntaxException;
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

import spaghettichef.persistence.CameraDeltaFrame;
import spaghettichef.persistence.CameraDeltaFrameStore;
import spaghettichef.persistence.CameraDeltaSetStore;
import spaghettichef.persistence.CameraCalculationResult;
import spaghettichef.persistence.CameraCalculationResultStore;
import spaghettichef.persistence.CameraCalculationRun;
import spaghettichef.persistence.CameraCalculationRunStore;
import spaghettichef.persistence.CameraJob;
import spaghettichef.persistence.CameraJobStore;
import spaghettichef.persistence.CameraSettings;
import spaghettichef.persistence.CameraSettingsStore;
import spaghettichef.persistence.CameraSnapshotEntry;
import spaghettichef.persistence.CameraSnapshotEntryStore;
import spaghettichef.persistence.DatabaseInitializer;

class CameraDeltaSetServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-05-22T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @AfterEach
    void clearDatabaseProperty() {
        System.clearProperty("spaghettichef.databaseFile");
    }

    @Test
    void generateCreatesConsecutiveDeltaFramesForStepOne() throws Exception {
        useDatabase("camera-delta-step-one.db");
        CameraJob job = saveCameraJob("printer-1");
        saveCameraSettings("printer-1");
        saveSnapshots("printer-1", job.requireId(), 3);

        CameraDeltaSetGenerationResult result = service().generate(
                "printer-1",
                job.requireId(),
                1,
                null,
                "step one");

        assertEquals(3, result.sourceSnapshotCount());
        assertEquals(2, result.generatedDeltaCount());
        assertEquals(1, result.deltaSet().deltaSnapshotStep());
        assertEquals("image-delta", result.deltaSet().methodName());

        List<CameraDeltaFrame> frames = new CameraDeltaFrameStore()
                .findByDeltaSetId(result.deltaSet().requireId());
        assertEquals(2, frames.size());
        assertEquals(1L, frames.get(0).fromSnapshotId());
        assertEquals(2L, frames.get(0).toSnapshotId());
        assertTrue(frames.get(0).deltaPath().endsWith("/deltas/1/1/000001_000002_delta.jpg"));
        assertTrue(frames.get(1).deltaPath().endsWith("/deltas/1/1/000002_000003_delta.jpg"));
        assertTrue(Files.exists(Path.of(frames.get(0).deltaPath())));
        assertTrue(Files.exists(Path.of(frames.get(1).deltaPath())));
    }

    @Test
    void generateUsesConfiguredStepInsteadOfEveryIntermediateSnapshot() throws Exception {
        useDatabase("camera-delta-step-ten.db");
        CameraJob job = saveCameraJob("printer-1");
        saveCameraSettings("printer-1");
        saveSnapshots("printer-1", job.requireId(), 31);

        CameraDeltaSetGenerationResult result = service().generate(
                "printer-1",
                job.requireId(),
                10,
                "sampled-delta",
                "step ten");

        assertEquals(31, result.sourceSnapshotCount());
        assertEquals(3, result.generatedDeltaCount());
        assertEquals(10, result.deltaSet().deltaSnapshotStep());
        assertEquals("sampled-delta", result.deltaSet().methodName());

        List<CameraDeltaFrame> frames = new CameraDeltaFrameStore()
                .findByDeltaSetId(result.deltaSet().requireId());
        assertEquals(3, frames.size());
        assertEquals(1L, frames.get(0).fromSnapshotId());
        assertEquals(11L, frames.get(0).toSnapshotId());
        assertEquals(11L, frames.get(1).fromSnapshotId());
        assertEquals(21L, frames.get(1).toSnapshotId());
        assertEquals(21L, frames.get(2).fromSnapshotId());
        assertEquals(31L, frames.get(2).toSnapshotId());
        assertTrue(frames.get(0).deltaPath().endsWith("/deltas/1/1/000001_000011_delta.jpg"));
        assertTrue(frames.get(1).deltaPath().endsWith("/deltas/1/1/000011_000021_delta.jpg"));
        assertTrue(frames.get(2).deltaPath().endsWith("/deltas/1/1/000021_000031_delta.jpg"));
    }

    @Test
    void generateRejectsCameraJobFromAnotherPrinter() throws Exception {
        useDatabase("camera-delta-wrong-printer.db");
        CameraJob job = saveCameraJob("printer-1");
        saveCameraSettings("printer-2");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service().generate("printer-2", job.requireId(), 1, null, null));

        assertEquals("camera job does not belong to printer: printer-2", exception.getMessage());
    }

    @Test
    void calculationRunsCanBeCreatedSeveralTimesForSameDeltaSet() throws Exception {
        useDatabase("camera-calculation-runs.db");
        CameraJob job = saveCameraJob("printer-1");
        saveCameraSettings("printer-1");
        saveSnapshots("printer-1", job.requireId(), 3);
        CameraDeltaSetGenerationResult deltaResult = service().generate(
                "printer-1",
                job.requireId(),
                1,
                null,
                null);

        CameraCalculationRunService calculationService = calculationService();
        CameraCalculationRun firstRun = calculationService.run(
                deltaResult.deltaSet().requireId(),
                "threshold-v1",
                0.25,
                null,
                "first");
        CameraCalculationRun secondRun = calculationService.run(
                deltaResult.deltaSet().requireId(),
                "threshold-v1",
                0.75,
                null,
                "second");

        assertTrue(firstRun.requireId() != secondRun.requireId());
        assertEquals(2, firstRun.resultCount());
        assertEquals(2, secondRun.resultCount());

        List<CameraCalculationRun> runs = new CameraCalculationRunStore()
                .findByDeltaSetId(deltaResult.deltaSet().requireId());
        assertEquals(2, runs.size());
        assertEquals(secondRun.requireId(), runs.get(0).requireId());
        assertEquals(firstRun.requireId(), runs.get(1).requireId());

        List<CameraCalculationResult> firstResults = new CameraCalculationResultStore()
                .findByCalculationRunId(firstRun.requireId());
        List<CameraCalculationResult> secondResults = new CameraCalculationResultStore()
                .findByCalculationRunId(secondRun.requireId());
        assertEquals(2, firstResults.size());
        assertEquals(2, secondResults.size());
        assertEquals(firstRun.requireId(), firstResults.get(0).calculationRunId());
        assertEquals(secondRun.requireId(), secondResults.get(0).calculationRunId());
        assertEquals("JAVA_BASIC_DELTA", firstRun.engineName());
        assertEquals("DELTA_SCORE_THRESHOLD", firstRun.algorithmVariant());
        assertEquals("SUCCESS", firstRun.engineStatus());
        assertTrue(firstRun.executionDurationMs() != null);
    }

    @Test
    void rustCalculationEngineCanBeSelectedButRemainsUnavailable() throws Exception {
        useDatabase("camera-rust-calculation-engine.db");
        CameraJob job = saveCameraJob("printer-1");
        saveCameraSettings("printer-1");
        saveSnapshots("printer-1", job.requireId(), 3);
        CameraDeltaSetGenerationResult deltaResult = service().generate(
                "printer-1",
                job.requireId(),
                1,
                null,
                null);

        CameraCalculationRun run = calculationService().run(
                deltaResult.deltaSet().requireId(),
                "rust-cli-delta",
                0.65,
                null,
                null,
                "RUST_CLI_DELTA");

        assertEquals("RUST_CLI_DELTA", run.engineName());
        assertEquals("FRAME_DELTA", run.algorithmVariant());
        assertEquals("UNAVAILABLE", run.engineStatus());
        assertEquals(0, run.resultCount());
        assertTrue(run.executionDurationMs() != null);

        List<CameraCalculationResult> results = new CameraCalculationResultStore()
                .findByCalculationRunId(run.requireId());
        assertEquals(0, results.size());
    }

    @Test
    void rustCalculationEnginePersistsResultsWhenExecutableIsConfigured() throws Exception {
        useDatabase("camera-rust-calculation-engine-success.db");
        CameraJob job = saveCameraJob("printer-1");
        saveCameraSettings("printer-1");
        saveSnapshots("printer-1", job.requireId(), 3);
        CameraDeltaSetGenerationResult deltaResult = service().generate(
                "printer-1",
                job.requireId(),
                1,
                null,
                null);

        CameraCalculationRun javaRun = calculationService().run(
                deltaResult.deltaSet().requireId(),
                "threshold-v1",
                0.85,
                null,
                "java run",
                "JAVA_BASIC_DELTA");
        CameraCalculationRun rustRun = calculationService().run(
                deltaResult.deltaSet().requireId(),
                "rust-cli-delta",
                0.65,
                null,
                "rust run",
                "RUST_CLI_DELTA",
                scriptPath("fake-rust-analyzer-success.sh").toString());

        assertTrue(javaRun.requireId() != rustRun.requireId());
        assertEquals("JAVA_BASIC_DELTA", javaRun.engineName());
        assertEquals("RUST_CLI_DELTA", rustRun.engineName());
        assertEquals("SUCCESS", rustRun.engineStatus());
        assertEquals("0.5.6", rustRun.engineVersion());
        assertEquals(2, rustRun.resultCount());

        List<CameraCalculationResult> rustResults = new CameraCalculationResultStore()
                .findByCalculationRunId(rustRun.requireId());
        assertEquals(2, rustResults.size());
        assertEquals("large_delta_area,high_average_pixel_delta", rustResults.get(0).reasonCodes());
        assertEquals("Large visual difference detected between snapshots.", rustResults.get(0).message());
    }

    @Test
    void rustCalculationEngineInvalidResponseIsVisibleOnRun() throws Exception {
        useDatabase("camera-rust-calculation-engine-invalid.db");
        CameraJob job = saveCameraJob("printer-1");
        saveCameraSettings("printer-1");
        saveSnapshots("printer-1", job.requireId(), 3);
        CameraDeltaSetGenerationResult deltaResult = service().generate(
                "printer-1",
                job.requireId(),
                1,
                null,
                null);

        CameraCalculationRun rustRun = calculationService().run(
                deltaResult.deltaSet().requireId(),
                "rust-cli-delta",
                0.65,
                null,
                "rust invalid",
                "RUST_CLI_DELTA",
                scriptPath("fake-rust-analyzer-invalid-json.sh").toString());

        assertEquals("RUST_CLI_DELTA", rustRun.engineName());
        assertEquals("INVALID_RESPONSE", rustRun.engineStatus());
        assertEquals(0, rustRun.resultCount());
        assertTrue(rustRun.message().contains("invalid JSON"));
    }

    @Test
    void comparisonServiceComparesRunsForSameDeltaSet() throws Exception {
        useDatabase("camera-calculation-comparison.db");
        CameraJob job = saveCameraJob("printer-1");
        saveCameraSettings("printer-1");
        saveSnapshots("printer-1", job.requireId(), 3);
        CameraDeltaSetGenerationResult deltaResult = service().generate(
                "printer-1",
                job.requireId(),
                1,
                null,
                null);

        CameraCalculationRun javaRun = calculationService().run(
                deltaResult.deltaSet().requireId(),
                "threshold-v1",
                0.85,
                null,
                "java run",
                "JAVA_BASIC_DELTA");
        CameraCalculationRun rustRun = calculationService().run(
                deltaResult.deltaSet().requireId(),
                "rust-cli-delta",
                0.65,
                null,
                "rust run",
                "RUST_CLI_DELTA",
                scriptPath("fake-rust-analyzer-success.sh").toString());

        CameraCalculationRunComparison comparison = new CameraCalculationComparisonService()
                .compare(javaRun.requireId(), rustRun.requireId(), "printer-1");

        assertEquals(javaRun.requireId(), comparison.left().run().requireId());
        assertEquals(rustRun.requireId(), comparison.right().run().requireId());
        assertEquals(2, comparison.left().resultCount());
        assertEquals(2, comparison.right().resultCount());
        assertEquals(2, comparison.comparedFrameCount());
        assertEquals(2, comparison.frames().size());
        assertTrue(comparison.averageAbsoluteConfidenceDifference() >= 0.0);
        assertFalse(comparison.frames().get(0).deltaFrameId() <= 0L);
    }

    private CameraDeltaSetService service() {
        return new CameraDeltaSetService(
                new CameraJobStore(),
                new CameraSettingsStore(),
                new CameraSnapshotEntryStore(),
                new CameraDeltaSetStore(),
                new CameraDeltaFrameStore(),
                new ImageDeltaFrameAnalyzer(1, 0.95, true, FIXED_CLOCK),
                FIXED_CLOCK);
    }

    private CameraCalculationRunService calculationService() {
        return new CameraCalculationRunService(
                new CameraDeltaSetStore(),
                new CameraDeltaFrameStore(),
                new CameraCalculationRunStore(),
                new CameraCalculationResultStore(),
                FIXED_CLOCK);
    }

    private CameraJob saveCameraJob(String printerId) {
        return new CameraJobStore().save(CameraJob.running(
                printerId,
                null,
                null,
                FIXED_INSTANT,
                CameraSettings.DEFAULT_CAPTURE_INTERVAL_SECONDS,
                CameraSettings.DEFAULT_RETENTION_SNAPSHOT_COUNT,
                CameraSourceType.SIMULATED.wireValue(),
                "simulated-camera:" + printerId,
                tempDir.resolve("camera-storage").resolve(printerId).resolve("snapshots").resolve("1").toString(),
                "test camera job"));
    }

    private void saveCameraSettings(String printerId) {
        CameraSettings settings = CameraSettings.simulated(printerId, FIXED_INSTANT);
        new CameraSettingsStore().save(new CameraSettings(
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
                settings.updatedAt()));
    }

    private void saveSnapshots(String printerId, long cameraJobId, int count) throws Exception {
        CameraSnapshotEntryStore store = new CameraSnapshotEntryStore();
        Path directory = CameraStoragePaths.snapshotsDirectory(
                tempDir.resolve("camera-storage").toString(),
                printerId,
                cameraJobId);
        Files.createDirectories(directory);

        for (int index = 1; index <= count; index++) {
            Path path = directory.resolve("%06d.jpg".formatted(index));
            writeImage(path, index);
            store.save(CameraSnapshotEntry.captured(
                    printerId,
                    cameraJobId,
                    null,
                    path.toString(),
                    "image/jpeg",
                    Files.size(path),
                    FIXED_INSTANT.plusSeconds(index),
                    FIXED_INSTANT.plusSeconds(index),
                    CameraSourceType.SIMULATED.wireValue(),
                    "snapshot " + index));
        }
    }

    private void useDatabase(String fileName) {
        Path dbFile = tempDir.resolve(fileName);
        System.setProperty("spaghettichef.databaseFile", dbFile.toString());
        new DatabaseInitializer().initialize();
    }

    private static Path scriptPath(String resourceName) {
        try {
            Path path = Path.of(
                    CameraDeltaSetServiceTest.class
                            .getClassLoader()
                            .getResource(resourceName)
                            .toURI());
            path.toFile().setExecutable(true);
            return path;
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Invalid test resource URI", exception);
        }
    }

    private static void writeImage(Path path, int shadeSeed) throws Exception {
        BufferedImage image = new BufferedImage(16, 12, BufferedImage.TYPE_INT_RGB);
        int shade = Math.min(255, 32 + (shadeSeed * 7));

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = (shade << 16) | (shade << 8) | shade;
                image.setRGB(x, y, rgb);
            }
        }

        boolean written = ImageIO.write(image, "jpg", path.toFile());
        if (!written) {
            throw new IllegalStateException("No image writer available for jpg");
        }
    }
}
