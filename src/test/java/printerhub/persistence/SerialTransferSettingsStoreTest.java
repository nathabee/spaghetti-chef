package printerhub.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SerialTransferSettingsStoreTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearDatabaseProperty() {
        System.clearProperty("printerhub.databaseFile");
    }

    @Test
    void loadReturnsDefaultsWhenSettingsHaveNotBeenSaved() {
        useDatabase("transfer-settings-defaults.db");

        SerialTransferSettings settings = new SerialTransferSettingsStore().load();

        assertEquals(SerialTransferSettings.defaults().sdUploadBatchSize(), settings.sdUploadBatchSize());
        assertEquals(
                SerialTransferSettings.defaults().fileStreamingRecoveryReplayDelayMs(),
                settings.fileStreamingRecoveryReplayDelayMs());
    }

    @Test
    void savePersistsSettings() {
        useDatabase("transfer-settings-save.db");
        SerialTransferSettingsStore store = new SerialTransferSettingsStore();
        SerialTransferSettings settings = new SerialTransferSettings(
                7,
                3,
                200,
                12,
                8,
                4,
                6000,
                20,
                2,
                3,
                25);

        store.save(settings);
        SerialTransferSettings loaded = store.load();

        assertEquals(7, loaded.sdUploadBatchSize());
        assertEquals(3, loaded.sdUploadRecoveryWindowMultiplier());
        assertEquals(200, loaded.sdUploadMaxErrors());
        assertEquals(12, loaded.sdUploadMaxConsecutiveIdenticalResends());
        assertEquals(8, loaded.sdUploadMinPerformancePercent());
        assertEquals(4, loaded.sdUploadMaxRetriesPerLine());
        assertEquals(6000, loaded.fileStreamingReadTimeoutMs());
        assertEquals(20, loaded.fileStreamingQuietPeriodMs());
        assertEquals(2, loaded.fileStreamingReadActivitySleepMs());
        assertEquals(3, loaded.fileStreamingReadIdleSleepMs());
        assertEquals(25, loaded.fileStreamingRecoveryReplayDelayMs());
    }

    private void useDatabase(String filename) {
        System.setProperty("printerhub.databaseFile", tempDir.resolve(filename).toString());
        new DatabaseInitializer().initialize();
    }
}
