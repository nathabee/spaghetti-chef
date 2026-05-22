package printerhub.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import printerhub.config.RuntimeDefaults;

class CameraStoragePathsTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearDatabaseProperty() {
        System.clearProperty(RuntimeDefaults.DATABASE_FILE_PROPERTY);
    }

    @Test
    void defaultBaseDirectoryResolvesFromDatabaseDirectory() {
        Path databaseFile = tempDir.resolve("data").resolve("printerhub.db");
        System.setProperty(RuntimeDefaults.DATABASE_FILE_PROPERTY, databaseFile.toString());

        assertEquals(
                databaseFile.getParent().resolve("camera").toAbsolutePath().normalize(),
                CameraStoragePaths.defaultBaseDirectory().toAbsolutePath().normalize());
    }

    @Test
    void relativeStorageDirectoryResolvesFromDatabaseDirectory() {
        Path databaseFile = tempDir.resolve("data").resolve("printerhub.db");
        System.setProperty(RuntimeDefaults.DATABASE_FILE_PROPERTY, databaseFile.toString());

        assertEquals(
                databaseFile.getParent().resolve("camera-custom").toAbsolutePath().normalize(),
                CameraStoragePaths.resolveBaseDirectory("camera-custom").toAbsolutePath().normalize());
    }

    @Test
    void absoluteStorageDirectoryIsUsedAsConfigured() {
        Path storageDirectory = tempDir.resolve("absolute-camera-storage");

        assertEquals(
                storageDirectory.toAbsolutePath().normalize(),
                CameraStoragePaths.resolveBaseDirectory(storageDirectory.toString()).toAbsolutePath().normalize());
    }

    @Test
    void legacyDataCameraDefaultResolvesFromDatabaseDirectory() {
        Path databaseFile = tempDir.resolve("data").resolve("printerhub.db");
        System.setProperty(RuntimeDefaults.DATABASE_FILE_PROPERTY, databaseFile.toString());

        assertEquals(
                databaseFile.getParent().resolve("camera").toAbsolutePath().normalize(),
                CameraStoragePaths.resolveBaseDirectory("data/camera").toAbsolutePath().normalize());
    }

    @Test
    void snapshotPathUsesCameraJobIdAsFolderOwner() {
        Path storageDirectory = tempDir.resolve("absolute-camera-storage");

        Path path = CameraStoragePaths.snapshotPath(
                storageDirectory.toString(),
                "printer 1",
                42L,
                Instant.parse("2026-05-22T11:23:40.512051086Z"),
                ".jpg");

        assertEquals(
                storageDirectory
                        .resolve("printer_1")
                        .resolve("snapshots")
                        .resolve("42")
                        .resolve("2026-05-22T11-23-40.512051086Z_42.jpg")
                        .toAbsolutePath()
                        .normalize(),
                path.toAbsolutePath().normalize());
    }

    @Test
    void snapshotPathRejectsInvalidCameraJobId() {
        Path storageDirectory = tempDir.resolve("absolute-camera-storage");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> CameraStoragePaths.snapshotPath(
                        storageDirectory.toString(),
                        "printer-1",
                        0L,
                        Instant.parse("2026-05-22T11:23:40Z"),
                        ".jpg"));

        assertEquals("cameraJobId must be greater than zero", exception.getMessage());
    }

    @Test
    void deltaFramePathUsesCameraJobAndDeltaSetFolders() {
        Path storageDirectory = tempDir.resolve("absolute-camera-storage");

        Path path = CameraStoragePaths.deltaFramePath(
                storageDirectory.toString(),
                "printer 1",
                42L,
                9L,
                1,
                11);

        assertEquals(
                storageDirectory
                        .resolve("printer_1")
                        .resolve("deltas")
                        .resolve("42")
                        .resolve("9")
                        .resolve("000001_000011_delta.jpg")
                        .toAbsolutePath()
                        .normalize(),
                path.toAbsolutePath().normalize());
    }

    @Test
    void deltaFramePathRejectsInvalidDeltaSetId() {
        Path storageDirectory = tempDir.resolve("absolute-camera-storage");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> CameraStoragePaths.deltaFramePath(
                        storageDirectory.toString(),
                        "printer-1",
                        42L,
                        0L,
                        1,
                        2));

        assertEquals("deltaSetId must be greater than zero", exception.getMessage());
    }
}
