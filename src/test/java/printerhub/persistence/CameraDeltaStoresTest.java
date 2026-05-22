package printerhub.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CameraDeltaStoresTest {

    private static final Instant CREATED_AT = Instant.parse("2026-05-22T12:00:00Z");

    @TempDir
    Path tempDir;

    @AfterEach
    void clearDatabaseProperty() {
        System.clearProperty("printerhub.databaseFile");
    }

    @Test
    void deltaSetStoreSavesAndListsByCameraJob() {
        useDatabase("camera-delta-set.db");

        CameraDeltaSetStore store = new CameraDeltaSetStore();

        CameraDeltaSet saved = store.save(new CameraDeltaSet(
                null,
                "printer-1",
                7L,
                "image-delta",
                10,
                31,
                3,
                CREATED_AT,
                "step 10"));

        assertEquals(1L, saved.requireId());
        assertEquals("printer-1", saved.printerId());
        assertEquals(7L, saved.cameraJobId());

        CameraDeltaSet loaded = store.findById(saved.requireId()).orElseThrow();
        assertEquals("image-delta", loaded.methodName());
        assertEquals(10, loaded.deltaSnapshotStep());
        assertEquals(31, loaded.sourceSnapshotCount());
        assertEquals(3, loaded.generatedDeltaCount());
        assertEquals("step 10", loaded.messageOptional().orElseThrow());

        List<CameraDeltaSet> cameraJobDeltaSets = store.findByCameraJobId(7L);
        assertEquals(1, cameraJobDeltaSets.size());
        assertEquals(saved.requireId(), cameraJobDeltaSets.get(0).requireId());
    }

    @Test
    void deltaFrameStoreSavesAndListsByDeltaSet() {
        useDatabase("camera-delta-frame.db");

        CameraDeltaFrameStore store = new CameraDeltaFrameStore();

        CameraDeltaFrame saved = store.save(new CameraDeltaFrame(
                null,
                1L,
                "printer-1",
                7L,
                11L,
                21L,
                Instant.parse("2026-05-22T12:00:01Z"),
                Instant.parse("2026-05-22T12:00:11Z"),
                tempDir.resolve("delta.jpg").toString(),
                0.42,
                0.25,
                0.13,
                CREATED_AT));

        assertEquals(1L, saved.requireId());

        CameraDeltaFrame loaded = store.findById(saved.requireId()).orElseThrow();
        assertEquals(11L, loaded.fromSnapshotId());
        assertEquals(21L, loaded.toSnapshotId());
        assertEquals(0.42, loaded.deltaScore());

        List<CameraDeltaFrame> frames = store.findByDeltaSetId(1L);
        assertEquals(1, frames.size());
        assertEquals(saved.requireId(), frames.get(0).requireId());
    }

    @Test
    void calculationRunAndResultStoresDoNotOverwritePreviousRuns() {
        useDatabase("camera-calculation-run.db");

        CameraCalculationRunStore runStore = new CameraCalculationRunStore();
        CameraCalculationResultStore resultStore = new CameraCalculationResultStore();

        CameraCalculationRun firstRun = runStore.save(new CameraCalculationRun(
                null,
                "printer-1",
                7L,
                3L,
                "spaghetti-v1",
                "{\"threshold\":0.8}",
                CREATED_AT,
                1,
                "first"));
        CameraCalculationRun secondRun = runStore.save(new CameraCalculationRun(
                null,
                "printer-1",
                7L,
                3L,
                "spaghetti-v1",
                "{\"threshold\":0.9}",
                CREATED_AT.plusSeconds(60),
                1,
                "second"));

        assertTrue(firstRun.requireId() != secondRun.requireId());

        CameraCalculationResult savedResult = resultStore.save(new CameraCalculationResult(
                null,
                firstRun.requireId(),
                44L,
                0.86,
                true,
                "HIGH_VISUAL_DELTA",
                "possible spaghetti",
                CREATED_AT.plusSeconds(1)));

        List<CameraCalculationRun> runs = runStore.findByDeltaSetId(3L);
        assertEquals(2, runs.size());
        assertEquals(secondRun.requireId(), runs.get(0).requireId());
        assertEquals(firstRun.requireId(), runs.get(1).requireId());

        List<CameraCalculationResult> results = resultStore.findByCalculationRunId(firstRun.requireId());
        assertEquals(1, results.size());
        assertEquals(savedResult.requireId(), results.get(0).requireId());
        assertTrue(results.get(0).suspected());
        assertEquals("HIGH_VISUAL_DELTA", results.get(0).reasonCodesOptional().orElseThrow());
    }

    @Test
    void modelValidationRejectsInvalidDeltaStep() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new CameraDeltaSet(
                        null,
                        "printer-1",
                        1L,
                        "image-delta",
                        0,
                        10,
                        0,
                        CREATED_AT,
                        null));

        assertEquals("deltaSnapshotStep must be greater than zero", exception.getMessage());
    }

    private void useDatabase(String fileName) {
        Path dbFile = tempDir.resolve(fileName);
        System.setProperty("printerhub.databaseFile", dbFile.toString());
        new DatabaseInitializer().initialize();
    }
}
