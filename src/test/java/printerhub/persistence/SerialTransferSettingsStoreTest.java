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
                7, // sdUploadBatchSize
                2, // sdUploadMinBatchSize
                2, // sdUploadBatchUpgradeStep
                1, // sdUploadBatchDowngradeStep
                300, // sdUploadStableLinesForUpgrade
                60, // sdUploadResendWindowLines
                2, // sdUploadResendThresholdForDowngrade
                4, // sdUploadRecoveryThresholdForMinBatch
                3, // sdUploadRecoveryWindowMultiplier
                200, // sdUploadMaxErrors
                12, // sdUploadMaxConsecutiveIdenticalResends
                8, // sdUploadMinPerformancePercent
                4, // sdUploadMaxRetriesPerLine
                6000, // fileStreamingReadTimeoutMs
                20, // fileStreamingQuietPeriodMs
                2, // fileStreamingReadActivitySleepMs
                3, // fileStreamingReadIdleSleepMs
                25 // fileStreamingRecoveryReplayDelayMs
        );

        store.save(settings);
        SerialTransferSettings loaded = store.load();

        assertEquals(7, loaded.sdUploadBatchSize());
        assertEquals(2, loaded.sdUploadMinBatchSize());
        assertEquals(2, loaded.sdUploadBatchUpgradeStep());
        assertEquals(1, loaded.sdUploadBatchDowngradeStep());
        assertEquals(300, loaded.sdUploadStableLinesForUpgrade());
        assertEquals(60, loaded.sdUploadResendWindowLines());
        assertEquals(2, loaded.sdUploadResendThresholdForDowngrade());
        assertEquals(4, loaded.sdUploadRecoveryThresholdForMinBatch());
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
