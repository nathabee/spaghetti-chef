package spaghettichef.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import spaghettichef.persistence.CameraCalculationResult;
import spaghettichef.persistence.CameraCalculationResultStore;
import spaghettichef.persistence.CameraCalculationRun;
import spaghettichef.persistence.CameraCalculationRunStore;
import spaghettichef.persistence.CameraDeltaFrame;
import spaghettichef.persistence.CameraDeltaFrameStore;
import spaghettichef.persistence.CameraDeltaSet;
import spaghettichef.persistence.CameraDeltaSetStore;
import spaghettichef.persistence.CameraSettings;
import spaghettichef.persistence.CameraSettingsStore;
import spaghettichef.persistence.CameraSnapshotEntry;
import spaghettichef.persistence.CameraSnapshotEntryStore;
import spaghettichef.persistence.DatabaseInitializer;

class CameraDeltaSetDeletionServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-05-27T14:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @AfterEach
    void clearDatabaseProperty() {
        System.clearProperty("spaghettichef.databaseFile");
    }

    @Test
    void deleteRemovesSelectedDeltaSetOnly() throws Exception {
        useDatabase("camera-delta-set-delete.db");
        Stores stores = stores();
        saveCameraSettings(stores.settingsStore(), "printer-1");
        Path snapshotPath = saveSnapshot(stores.snapshotEntryStore(), "printer-1", 1L);
        CameraDeltaSet target = saveDeltaSet(stores.deltaSetStore(), "printer-1", 1L, "target");
        CameraDeltaSet other = saveDeltaSet(stores.deltaSetStore(), "printer-1", 1L, "other");
        Path targetDeltaPath = saveDeltaFrame(stores.deltaFrameStore(), "printer-1", 1L, target.requireId(), 1L);
        Path otherDeltaPath = saveDeltaFrame(stores.deltaFrameStore(), "printer-1", 1L, other.requireId(), 2L);
        CameraCalculationRun targetRun = saveCalculationRun(stores.calculationRunStore(), "printer-1", 1L, target.requireId());
        CameraCalculationRun otherRun = saveCalculationRun(stores.calculationRunStore(), "printer-1", 1L, other.requireId());
        stores.calculationResultStore().save(result(targetRun.requireId(), 1L));
        stores.calculationResultStore().save(result(otherRun.requireId(), 2L));
        Path preview = tempDir.resolve("camera").resolve("printer-1").resolve("latest.jpg");
        Files.write(preview, new byte[] {9});

        CameraDeltaSetDeletionReport report = service(stores).delete(
                "printer-1",
                target.requireId(),
                CameraDeltaSetDeletionRequest.safeDefault(CameraDeltaSetDeletionRequest.CONFIRMATION));

        assertEquals(1, report.deletedDeltaFiles());
        assertEquals(1, report.deletedDeltaRows());
        assertEquals(1, report.deletedDeltaSetRows());
        assertEquals(1, report.deletedCalculationRunRows());
        assertEquals(1, report.deletedCalculationResultRows());
        assertTrue(report.failedFiles().isEmpty());
        assertFalse(Files.exists(targetDeltaPath));
        assertTrue(Files.exists(otherDeltaPath));
        assertTrue(Files.exists(snapshotPath));
        assertTrue(Files.exists(preview));
        assertTrue(stores.deltaSetStore().findById(target.requireId()).isEmpty());
        assertTrue(stores.deltaFrameStore().findByDeltaSetId(target.requireId()).isEmpty());
        assertTrue(stores.calculationRunStore().findByDeltaSetId(target.requireId()).isEmpty());
        assertTrue(stores.deltaSetStore().findById(other.requireId()).isPresent());
        assertEquals(1, stores.deltaFrameStore().findByDeltaSetId(other.requireId()).size());
        assertEquals(1, stores.calculationRunStore().findByDeltaSetId(other.requireId()).size());
    }

    @Test
    void deleteRejectsWrongPrinter() {
        useDatabase("camera-delta-set-delete-wrong-printer.db");
        Stores stores = stores();
        saveCameraSettings(stores.settingsStore(), "printer-1");
        saveCameraSettings(stores.settingsStore(), "printer-2");
        CameraDeltaSet target = saveDeltaSet(stores.deltaSetStore(), "printer-1", 1L, "target");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service(stores).delete(
                        "printer-2",
                        target.requireId(),
                        CameraDeltaSetDeletionRequest.safeDefault(CameraDeltaSetDeletionRequest.CONFIRMATION)));

        assertEquals("camera delta set does not belong to printer: printer-2", exception.getMessage());
    }

    @Test
    void deleteRequiresConfirmation() {
        useDatabase("camera-delta-set-delete-confirmation.db");
        Stores stores = stores();
        saveCameraSettings(stores.settingsStore(), "printer-1");
        CameraDeltaSet target = saveDeltaSet(stores.deltaSetStore(), "printer-1", 1L, "target");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service(stores).delete(
                        "printer-1",
                        target.requireId(),
                        CameraDeltaSetDeletionRequest.safeDefault("")));

        assertEquals("requiredConfirmation must be DELETE_DELTA_SET", exception.getMessage());
    }

    private CameraDeltaSetDeletionService service(Stores stores) {
        return new CameraDeltaSetDeletionService(
                new CameraSettingsService(stores.settingsStore(), FIXED_CLOCK),
                stores.deltaSetStore(),
                stores.deltaFrameStore(),
                stores.calculationRunStore(),
                stores.calculationResultStore());
    }

    private void saveCameraSettings(CameraSettingsStore store, String printerId) {
        store.save(new CameraSettings(
                printerId,
                true,
                CameraSourceType.SIMULATED,
                "default",
                10,
                20,
                false,
                false,
                false,
                0.85,
                3,
                "ffmpeg",
                "",
                "640x480",
                5000,
                3,
                tempDir.resolve("camera").toString(),
                FIXED_INSTANT));
    }

    private Path saveSnapshot(CameraSnapshotEntryStore store, String printerId, long cameraJobId) throws Exception {
        Path directory = tempDir.resolve("camera").resolve(printerId).resolve("snapshots").resolve(Long.toString(cameraJobId));
        Files.createDirectories(directory);
        Path path = directory.resolve("000001_snapshot.jpg");
        Files.write(path, new byte[] {1});
        store.save(CameraSnapshotEntry.captured(
                printerId,
                cameraJobId,
                null,
                path.toString(),
                "image/jpeg",
                Files.size(path),
                FIXED_INSTANT,
                FIXED_INSTANT,
                CameraSourceType.SIMULATED.wireValue(),
                "snapshot"));
        return path;
    }

    private CameraDeltaSet saveDeltaSet(CameraDeltaSetStore store, String printerId, long cameraJobId, String message) {
        return store.save(new CameraDeltaSet(
                null,
                printerId,
                cameraJobId,
                "image-delta",
                1,
                2,
                1,
                FIXED_INSTANT,
                message));
    }

    private Path saveDeltaFrame(
            CameraDeltaFrameStore store,
            String printerId,
            long cameraJobId,
            long deltaSetId,
            long toSnapshotId) throws Exception {
        Path directory = tempDir.resolve("camera").resolve(printerId).resolve("deltas").resolve(Long.toString(cameraJobId)).resolve(Long.toString(deltaSetId));
        Files.createDirectories(directory);
        Path path = directory.resolve("000001_%06d_delta.jpg".formatted(toSnapshotId));
        Files.write(path, new byte[] {(byte) toSnapshotId});
        store.save(new CameraDeltaFrame(
                null,
                deltaSetId,
                printerId,
                cameraJobId,
                1L,
                toSnapshotId,
                FIXED_INSTANT,
                FIXED_INSTANT.plusSeconds(toSnapshotId),
                path.toString(),
                0.2,
                0.1,
                0.1,
                FIXED_INSTANT));
        return path;
    }

    private CameraCalculationRun saveCalculationRun(
            CameraCalculationRunStore store,
            String printerId,
            long cameraJobId,
            long deltaSetId) {
        return store.save(new CameraCalculationRun(
                null,
                printerId,
                cameraJobId,
                deltaSetId,
                "spaghetti-heuristic",
                "{}",
                FIXED_INSTANT,
                1,
                "calculation"));
    }

    private CameraCalculationResult result(long calculationRunId, long deltaFrameId) {
        return new CameraCalculationResult(
                null,
                calculationRunId,
                deltaFrameId,
                0.7,
                false,
                "OK",
                "test result",
                FIXED_INSTANT);
    }

    private Stores stores() {
        return new Stores(
                new CameraSettingsStore(),
                new CameraSnapshotEntryStore(),
                new CameraDeltaSetStore(),
                new CameraDeltaFrameStore(),
                new CameraCalculationRunStore(),
                new CameraCalculationResultStore());
    }

    private void useDatabase(String fileName) {
        Path dbFile = tempDir.resolve(fileName);
        System.setProperty("spaghettichef.databaseFile", dbFile.toString());
        new DatabaseInitializer().initialize();
    }

    private record Stores(
            CameraSettingsStore settingsStore,
            CameraSnapshotEntryStore snapshotEntryStore,
            CameraDeltaSetStore deltaSetStore,
            CameraDeltaFrameStore deltaFrameStore,
            CameraCalculationRunStore calculationRunStore,
            CameraCalculationResultStore calculationResultStore
    ) {
    }
}
