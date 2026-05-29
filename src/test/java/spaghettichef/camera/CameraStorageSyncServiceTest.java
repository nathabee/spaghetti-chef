package spaghettichef.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import spaghettichef.persistence.CameraDeltaFrameStore;
import spaghettichef.persistence.CameraDeltaSetStore;
import spaghettichef.persistence.CameraJob;
import spaghettichef.persistence.CameraJobStore;
import spaghettichef.persistence.CameraSettings;
import spaghettichef.persistence.CameraSettingsStore;
import spaghettichef.persistence.CameraSnapshotEntry;
import spaghettichef.persistence.CameraSnapshotEntryStore;
import spaghettichef.persistence.DatabaseInitializer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CameraStorageSyncServiceTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-28T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @AfterEach
    void clearDatabaseProperty() {
        System.clearProperty("spaghettichef.databaseFile");
    }

    @Test
    void dryRunReportsCameraStorageReconciliationWithoutWritingRows() throws Exception {
        useDatabase("camera-storage-sync-dry-run.db");
        createCameraStorage();
        saveCameraSettings(tempDir.resolve("camera-storage/pex01"));

        CameraStorageSyncReport report = service().sync("pex01", request(true, false));

        assertEquals(1, report.scannedJobs());
        assertEquals(2, report.scannedSnapshotFiles());
        assertEquals(1, report.scannedDeltaSetFolders());
        assertEquals(1, report.scannedDeltaFiles());
        assertEquals(1, report.createdCameraJobs());
        assertEquals(2, report.createdSnapshotRows());
        assertEquals(1, report.createdDeltaSets());
        assertEquals(1, report.createdDeltaFrameRows());
        assertTrue(new CameraJobStore().findByPrinterId("pex01").isEmpty());
    }

    @Test
    void syncCreatesJobsSnapshotsDeltaSetsAndFramesFromConfiguredStorage() throws Exception {
        useDatabase("camera-storage-sync.db");
        createCameraStorage();
        saveCameraSettings(tempDir.resolve("camera-storage/pex01"));

        CameraStorageSyncReport report = service().sync("pex01", request(false, false));

        assertEquals(1, report.createdCameraJobs());
        assertEquals(2, report.createdSnapshotRows());
        assertEquals(1, report.createdDeltaSets());
        assertEquals(1, report.createdDeltaFrameRows());

        CameraJob job = new CameraJobStore().findByPrinterId("pex01").get(0);
        assertEquals("camera-storage-sync", job.sourceType());
        assertEquals("runtime camera job 2", job.sourceDescription().orElseThrow());
        assertEquals(2, new CameraSnapshotEntryStore().findByPrinterIdAndJobId("pex01", Long.toString(job.requireId())).size());
        assertEquals(1, new CameraDeltaSetStore().findByCameraJobId(job.requireId()).size());
        assertEquals(1, new CameraDeltaFrameStore().findByCameraJobId(job.requireId()).size());
    }

    @Test
    void secondSyncIsIdempotent() throws Exception {
        useDatabase("camera-storage-sync-idempotent.db");
        createCameraStorage();
        saveCameraSettings(tempDir.resolve("camera-storage/pex01"));

        service().sync("pex01", request(false, false));
        CameraStorageSyncReport second = service().sync("pex01", request(false, false));

        assertEquals(0, second.createdCameraJobs());
        assertEquals(0, second.createdSnapshotRows());
        assertEquals(0, second.createdDeltaSets());
        assertEquals(0, second.createdDeltaFrameRows());
    }

    @Test
    void syncDeletesRowsForMissingFilesWhenRequested() throws Exception {
        useDatabase("camera-storage-sync-delete-missing.db");
        createCameraStorage();
        saveCameraSettings(tempDir.resolve("camera-storage/pex01"));
        CameraStorageSyncService syncService = service();
        syncService.sync("pex01", request(false, false));
        Files.delete(tempDir.resolve("camera-storage/pex01/snapshots/2/001299_snapshot.jpg"));
        Files.delete(tempDir.resolve("camera-storage/pex01/deltas/2/1/001298_001299_delta.jpg"));

        CameraStorageSyncReport report = syncService.sync("pex01", request(false, true));

        assertEquals(1, report.deletedSnapshotRows());
        assertEquals(1, report.deletedDeltaFrameRows());
    }

    @Test
    void syncReactivatesDeletedSnapshotRowsWhenFileExistsAgain() throws Exception {
        useDatabase("camera-storage-sync-reactivate.db");
        createCameraStorage();
        saveCameraSettings(tempDir.resolve("camera-storage/pex01"));
        CameraStorageSyncService syncService = service();
        syncService.sync("pex01", request(false, false));
        CameraJob job = new CameraJobStore().findByPrinterId("pex01").get(0);
        CameraSnapshotEntry entry = new CameraSnapshotEntryStore()
                .findByPrinterIdAndJobId("pex01", Long.toString(job.requireId()))
                .get(0);
        new CameraSnapshotEntryStore().markFileDeleted(entry.id(), Instant.parse("2026-05-28T12:01:00Z"), "test");

        CameraStorageSyncReport report = syncService.sync("pex01", request(false, false));

        assertEquals(1, report.reactivatedSnapshotRows());
        assertTrue(new CameraSnapshotEntryStore().findById(entry.id()).orElseThrow().deletedAtOptional().isEmpty());
    }

    @Test
    void syncAlsoSupportsRuntimeStorageRootThatContainsPrinterFolders() throws Exception {
        useDatabase("camera-storage-sync-base-root.db");
        createCameraStorage();
        saveCameraSettings(tempDir.resolve("camera-storage"));

        CameraStorageSyncReport report = service().sync("pex01", request(false, false));

        assertEquals(1, report.createdCameraJobs());
        assertEquals(2, report.createdSnapshotRows());
        assertEquals(1, report.createdDeltaFrameRows());
    }

    private CameraStorageSyncRequest request(boolean dryRun, boolean deleteRowsForMissingFiles) {
        return new CameraStorageSyncRequest(
                "runtime-camera-storage",
                dryRun,
                true,
                true,
                deleteRowsForMissingFiles,
                true,
                true,
                true,
                dryRun ? null : CameraStorageSyncService.CONFIRMATION);
    }

    private CameraStorageSyncService service() {
        return new CameraStorageSyncService(
                new CameraSettingsStore(),
                new CameraJobStore(),
                new CameraSnapshotEntryStore(),
                new CameraDeltaSetStore(),
                new CameraDeltaFrameStore(),
                FIXED_CLOCK);
    }

    private void createCameraStorage() throws Exception {
        Path contentRoot = tempDir.resolve("camera-storage/pex01");
        Files.createDirectories(contentRoot.resolve("snapshots/2"));
        Files.createDirectories(contentRoot.resolve("deltas/2/1"));
        Files.write(contentRoot.resolve("snapshots/2/001298_snapshot.jpg"), new byte[] {1});
        Files.write(contentRoot.resolve("snapshots/2/001299_snapshot.jpg"), new byte[] {2});
        Files.write(contentRoot.resolve("deltas/2/1/001298_001299_delta.jpg"), new byte[] {3});
    }

    private void saveCameraSettings(Path storageDirectory) {
        new CameraSettingsStore().save(new CameraSettings(
                "pex01",
                true,
                CameraSourceType.SNAPSHOT_FOLDER,
                storageDirectory.toString(),
                5,
                100,
                true,
                false,
                false,
                0.85,
                3,
                "ffmpeg",
                "",
                "640x480",
                5000,
                3,
                storageDirectory.toString(),
                true,
                Instant.parse("2026-05-28T12:00:00Z")));
    }

    private void useDatabase(String fileName) {
        Path dbFile = tempDir.resolve(fileName);
        System.setProperty("spaghettichef.databaseFile", dbFile.toString());
        new DatabaseInitializer().initialize();
    }
}
