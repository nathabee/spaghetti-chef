package printerhub.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

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
}
