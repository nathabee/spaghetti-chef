package printerhub.persistence;

import org.junit.jupiter.api.Test;
import printerhub.OperationMessages;
import printerhub.config.PrinterProtocolDefaults;
import printerhub.config.RuntimeDefaults;
import printerhub.config.SerialDefaults;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SerialTransferSettingsTest {

    @Test
    void constructorStoresValues() {
        SerialTransferSettings settings = new SerialTransferSettings(
                5,      // sdUploadBatchSize
                1,      // sdUploadMinBatchSize
                1,      // sdUploadBatchUpgradeStep
                1,      // sdUploadBatchDowngradeStep
                200,    // sdUploadStableLinesForUpgrade
                50,     // sdUploadResendWindowLines
                1,      // sdUploadResendThresholdForDowngrade
                3,      // sdUploadRecoveryThresholdForMinBatch
                2,      // sdUploadRecoveryWindowMultiplier
                100,    // sdUploadMaxErrors
                10,     // sdUploadMaxConsecutiveIdenticalResends
                5,      // sdUploadMinPerformancePercent
                3,      // sdUploadMaxRetriesPerLine
                5000,   // fileStreamingReadTimeoutMs
                10,     // fileStreamingQuietPeriodMs
                1,      // fileStreamingReadActivitySleepMs
                1,      // fileStreamingReadIdleSleepMs
                15      // fileStreamingRecoveryReplayDelayMs
        );

        assertEquals(5, settings.sdUploadBatchSize());
        assertEquals(1, settings.sdUploadMinBatchSize());
        assertEquals(1, settings.sdUploadBatchUpgradeStep());
        assertEquals(1, settings.sdUploadBatchDowngradeStep());
        assertEquals(200, settings.sdUploadStableLinesForUpgrade());
        assertEquals(50, settings.sdUploadResendWindowLines());
        assertEquals(1, settings.sdUploadResendThresholdForDowngrade());
        assertEquals(3, settings.sdUploadRecoveryThresholdForMinBatch());
        assertEquals(2, settings.sdUploadRecoveryWindowMultiplier());
        assertEquals(100, settings.sdUploadMaxErrors());
        assertEquals(10, settings.sdUploadMaxConsecutiveIdenticalResends());
        assertEquals(5, settings.sdUploadMinPerformancePercent());
        assertEquals(3, settings.sdUploadMaxRetriesPerLine());
        assertEquals(5000, settings.fileStreamingReadTimeoutMs());
        assertEquals(10, settings.fileStreamingQuietPeriodMs());
        assertEquals(1, settings.fileStreamingReadActivitySleepMs());
        assertEquals(1, settings.fileStreamingReadIdleSleepMs());
        assertEquals(15, settings.fileStreamingRecoveryReplayDelayMs());
    }

    @Test
    void defaultsUseRuntimeAndSerialDefaults() {
        SerialTransferSettings settings = SerialTransferSettings.defaults();

        assertEquals(RuntimeDefaults.DEFAULT_SD_UPLOAD_BATCH_SIZE, settings.sdUploadBatchSize());
        assertEquals(1, settings.sdUploadMinBatchSize());
        assertEquals(1, settings.sdUploadBatchUpgradeStep());
        assertEquals(1, settings.sdUploadBatchDowngradeStep());
        assertEquals(200, settings.sdUploadStableLinesForUpgrade());
        assertEquals(50, settings.sdUploadResendWindowLines());
        assertEquals(1, settings.sdUploadResendThresholdForDowngrade());
        assertEquals(3, settings.sdUploadRecoveryThresholdForMinBatch());
        assertEquals(
                RuntimeDefaults.DEFAULT_SD_UPLOAD_RECOVERY_WINDOW_MULTIPLIER,
                settings.sdUploadRecoveryWindowMultiplier());
        assertEquals(RuntimeDefaults.DEFAULT_SD_UPLOAD_MAX_ERRORS, settings.sdUploadMaxErrors());
        assertEquals(
                RuntimeDefaults.DEFAULT_SD_UPLOAD_MAX_CONSECUTIVE_IDENTICAL_RESENDS,
                settings.sdUploadMaxConsecutiveIdenticalResends());
        assertEquals(
                RuntimeDefaults.DEFAULT_SD_UPLOAD_MIN_PERFORMANCE_PERCENT,
                settings.sdUploadMinPerformancePercent());
        assertEquals(
                PrinterProtocolDefaults.SD_UPLOAD_MAX_RETRIES_PER_LINE,
                settings.sdUploadMaxRetriesPerLine());
        assertEquals(SerialDefaults.FILE_STREAMING_READ_TIMEOUT_MS, settings.fileStreamingReadTimeoutMs());
        assertEquals(SerialDefaults.FILE_STREAMING_QUIET_PERIOD_MS, settings.fileStreamingQuietPeriodMs());
        assertEquals(
                SerialDefaults.FILE_STREAMING_READ_ACTIVITY_SLEEP_MS,
                settings.fileStreamingReadActivitySleepMs());
        assertEquals(
                SerialDefaults.FILE_STREAMING_READ_IDLE_SLEEP_MS,
                settings.fileStreamingReadIdleSleepMs());
        assertEquals(
                SerialDefaults.FILE_STREAMING_RECOVERY_REPLAY_DELAY_MS,
                settings.fileStreamingRecoveryReplayDelayMs());
    }

    @Test
    void constructorAllowsZeroMinPerformancePercent() {
        SerialTransferSettings settings = new SerialTransferSettings(
                5,
                1,
                1,
                1,
                200,
                50,
                1,
                3,
                2,
                100,
                10,
                0,
                3,
                5000,
                10,
                1,
                1,
                15);

        assertEquals(0, settings.sdUploadMinPerformancePercent());
    }

    @Test
    void constructorAllowsZeroQuietPeriodMs() {
        SerialTransferSettings settings = new SerialTransferSettings(
                5,
                1,
                1,
                1,
                200,
                50,
                1,
                3,
                2,
                100,
                10,
                5,
                3,
                5000,
                0,
                1,
                1,
                15);

        assertEquals(0, settings.fileStreamingQuietPeriodMs());
    }

    @Test
    void constructorAllowsZeroReadActivitySleepMs() {
        SerialTransferSettings settings = new SerialTransferSettings(
                5,
                1,
                1,
                1,
                200,
                50,
                1,
                3,
                2,
                100,
                10,
                5,
                3,
                5000,
                10,
                0,
                1,
                15);

        assertEquals(0, settings.fileStreamingReadActivitySleepMs());
    }

    @Test
    void constructorAllowsZeroReadIdleSleepMs() {
        SerialTransferSettings settings = new SerialTransferSettings(
                5,
                1,
                1,
                1,
                200,
                50,
                1,
                3,
                2,
                100,
                10,
                5,
                3,
                5000,
                10,
                1,
                0,
                15);

        assertEquals(0, settings.fileStreamingReadIdleSleepMs());
    }

    @Test
    void constructorAllowsZeroRecoveryReplayDelayMs() {
        SerialTransferSettings settings = new SerialTransferSettings(
                5,
                1,
                1,
                1,
                200,
                50,
                1,
                3,
                2,
                100,
                10,
                5,
                3,
                5000,
                10,
                1,
                1,
                0);

        assertEquals(0, settings.fileStreamingRecoveryReplayDelayMs());
    }

    @Test
    void constructorFailsForInvalidSdUploadBatchSize() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SerialTransferSettings(
                        0,
                        1,
                        1,
                        1,
                        200,
                        50,
                        1,
                        3,
                        2,
                        100,
                        10,
                        5,
                        3,
                        5000,
                        10,
                        1,
                        1,
                        15));

        assertEquals(
                OperationMessages.SD_UPLOAD_BATCH_SIZE_MUST_BE_IN_RANGE,
                exception.getMessage());
    }

    @Test
    void constructorFailsForInvalidSdUploadMinBatchSize() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SerialTransferSettings(
                        5,
                        0,
                        1,
                        1,
                        200,
                        50,
                        1,
                        3,
                        2,
                        100,
                        10,
                        5,
                        3,
                        5000,
                        10,
                        1,
                        1,
                        15));

        assertEquals("sdUploadMinBatchSize must be between 1 and 100", exception.getMessage());
    }

    @Test
    void constructorFailsWhenMinBatchExceedsMaxBatch() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SerialTransferSettings(
                        5,
                        6,
                        1,
                        1,
                        200,
                        50,
                        1,
                        3,
                        2,
                        100,
                        10,
                        5,
                        3,
                        5000,
                        10,
                        1,
                        1,
                        15));

        assertEquals("sdUploadMinBatchSize must not exceed sdUploadBatchSize", exception.getMessage());
    }

    @Test
    void constructorFailsForInvalidSdUploadBatchUpgradeStep() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SerialTransferSettings(
                        5,
                        1,
                        0,
                        1,
                        200,
                        50,
                        1,
                        3,
                        2,
                        100,
                        10,
                        5,
                        3,
                        5000,
                        10,
                        1,
                        1,
                        15));

        assertEquals("sdUploadBatchUpgradeStep must be between 1 and 100", exception.getMessage());
    }

    @Test
    void constructorFailsForInvalidSdUploadBatchDowngradeStep() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SerialTransferSettings(
                        5,
                        1,
                        1,
                        0,
                        200,
                        50,
                        1,
                        3,
                        2,
                        100,
                        10,
                        5,
                        3,
                        5000,
                        10,
                        1,
                        1,
                        15));

        assertEquals("sdUploadBatchDowngradeStep must be between 1 and 100", exception.getMessage());
    }

    @Test
    void constructorFailsForInvalidSdUploadStableLinesForUpgrade() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SerialTransferSettings(
                        5,
                        1,
                        1,
                        1,
                        0,
                        50,
                        1,
                        3,
                        2,
                        100,
                        10,
                        5,
                        3,
                        5000,
                        10,
                        1,
                        1,
                        15));

        assertEquals("sdUploadStableLinesForUpgrade must be between 1 and 1000000", exception.getMessage());
    }

    @Test
    void constructorFailsForInvalidSdUploadResendWindowLines() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SerialTransferSettings(
                        5,
                        1,
                        1,
                        1,
                        200,
                        0,
                        1,
                        3,
                        2,
                        100,
                        10,
                        5,
                        3,
                        5000,
                        10,
                        1,
                        1,
                        15));

        assertEquals("sdUploadResendWindowLines must be between 1 and 1000000", exception.getMessage());
    }

    @Test
    void constructorFailsForInvalidSdUploadResendThresholdForDowngrade() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SerialTransferSettings(
                        5,
                        1,
                        1,
                        1,
                        200,
                        50,
                        0,
                        3,
                        2,
                        100,
                        10,
                        5,
                        3,
                        5000,
                        10,
                        1,
                        1,
                        15));

        assertEquals("sdUploadResendThresholdForDowngrade must be between 1 and 1000000", exception.getMessage());
    }

    @Test
    void constructorFailsForInvalidSdUploadRecoveryThresholdForMinBatch() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SerialTransferSettings(
                        5,
                        1,
                        1,
                        1,
                        200,
                        50,
                        1,
                        0,
                        2,
                        100,
                        10,
                        5,
                        3,
                        5000,
                        10,
                        1,
                        1,
                        15));

        assertEquals("sdUploadRecoveryThresholdForMinBatch must be between 1 and 1000000", exception.getMessage());
    }

    @Test
    void constructorFailsForInvalidSdUploadRecoveryWindowMultiplier() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SerialTransferSettings(
                        5,
                        1,
                        1,
                        1,
                        200,
                        50,
                        1,
                        3,
                        0,
                        100,
                        10,
                        5,
                        3,
                        5000,
                        10,
                        1,
                        1,
                        15));

        assertEquals(
                OperationMessages.SD_UPLOAD_RECOVERY_WINDOW_MULTIPLIER_MUST_BE_IN_RANGE,
                exception.getMessage());
    }

    @Test
    void constructorFailsForInvalidSdUploadMaxErrors() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SerialTransferSettings(
                        5,
                        1,
                        1,
                        1,
                        200,
                        50,
                        1,
                        3,
                        2,
                        0,
                        10,
                        5,
                        3,
                        5000,
                        10,
                        1,
                        1,
                        15));

        assertEquals(
                OperationMessages.SD_UPLOAD_MAX_ERRORS_MUST_BE_IN_RANGE,
                exception.getMessage());
    }

    @Test
    void constructorFailsForInvalidSdUploadMaxConsecutiveIdenticalResends() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SerialTransferSettings(
                        5,
                        1,
                        1,
                        1,
                        200,
                        50,
                        1,
                        3,
                        2,
                        100,
                        0,
                        5,
                        3,
                        5000,
                        10,
                        1,
                        1,
                        15));

        assertEquals(
                OperationMessages.SD_UPLOAD_MAX_CONSECUTIVE_IDENTICAL_RESENDS_MUST_BE_IN_RANGE,
                exception.getMessage());
    }

    @Test
    void constructorFailsForInvalidSdUploadMinPerformancePercent() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SerialTransferSettings(
                        5,
                        1,
                        1,
                        1,
                        200,
                        50,
                        1,
                        3,
                        2,
                        100,
                        10,
                        101,
                        3,
                        5000,
                        10,
                        1,
                        1,
                        15));

        assertEquals(
                OperationMessages.SD_UPLOAD_MIN_PERFORMANCE_PERCENT_MUST_BE_IN_RANGE,
                exception.getMessage());
    }

    @Test
    void constructorFailsForInvalidSdUploadMaxRetriesPerLine() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SerialTransferSettings(
                        5,
                        1,
                        1,
                        1,
                        200,
                        50,
                        1,
                        3,
                        2,
                        100,
                        10,
                        5,
                        0,
                        5000,
                        10,
                        1,
                        1,
                        15));

        assertEquals(
                OperationMessages.SD_UPLOAD_MAX_RETRIES_PER_LINE_MUST_BE_IN_RANGE,
                exception.getMessage());
    }

    @Test
    void constructorFailsForInvalidFileStreamingReadTimeoutMs() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SerialTransferSettings(
                        5,
                        1,
                        1,
                        1,
                        200,
                        50,
                        1,
                        3,
                        2,
                        100,
                        10,
                        5,
                        3,
                        0,
                        10,
                        1,
                        1,
                        15));

        assertEquals(
                OperationMessages.FILE_STREAMING_READ_TIMEOUT_MS_MUST_BE_IN_RANGE,
                exception.getMessage());
    }

    @Test
    void constructorFailsForInvalidFileStreamingQuietPeriodMs() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SerialTransferSettings(
                        5,
                        1,
                        1,
                        1,
                        200,
                        50,
                        1,
                        3,
                        2,
                        100,
                        10,
                        5,
                        3,
                        5000,
                        -1,
                        1,
                        1,
                        15));

        assertEquals(
                OperationMessages.FILE_STREAMING_QUIET_PERIOD_MS_MUST_BE_IN_RANGE,
                exception.getMessage());
    }

    @Test
    void constructorFailsForInvalidFileStreamingReadActivitySleepMs() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SerialTransferSettings(
                        5,
                        1,
                        1,
                        1,
                        200,
                        50,
                        1,
                        3,
                        2,
                        100,
                        10,
                        5,
                        3,
                        5000,
                        10,
                        -1,
                        1,
                        15));

        assertEquals(
                OperationMessages.FILE_STREAMING_READ_ACTIVITY_SLEEP_MS_MUST_BE_IN_RANGE,
                exception.getMessage());
    }

    @Test
    void constructorFailsForInvalidFileStreamingReadIdleSleepMs() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SerialTransferSettings(
                        5,
                        1,
                        1,
                        1,
                        200,
                        50,
                        1,
                        3,
                        2,
                        100,
                        10,
                        5,
                        3,
                        5000,
                        10,
                        1,
                        -1,
                        15));

        assertEquals(
                OperationMessages.FILE_STREAMING_READ_IDLE_SLEEP_MS_MUST_BE_IN_RANGE,
                exception.getMessage());
    }

    @Test
    void constructorFailsForInvalidFileStreamingRecoveryReplayDelayMs() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SerialTransferSettings(
                        5,
                        1,
                        1,
                        1,
                        200,
                        50,
                        1,
                        3,
                        2,
                        100,
                        10,
                        5,
                        3,
                        5000,
                        10,
                        1,
                        1,
                        -1));

        assertEquals(
                OperationMessages.FILE_STREAMING_RECOVERY_REPLAY_DELAY_MS_MUST_BE_IN_RANGE,
                exception.getMessage());
    }
}