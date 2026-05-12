package printerhub.persistence;

import printerhub.OperationMessages;
import printerhub.config.PrinterProtocolDefaults;
import printerhub.config.RuntimeDefaults;
import printerhub.config.SerialDefaults; 

public final class SerialTransferSettings {

    private final int sdUploadBatchSize;
    private final int sdUploadRecoveryWindowMultiplier;
    private final int sdUploadMaxErrors;
    private final int sdUploadMaxConsecutiveIdenticalResends;
    private final int sdUploadMinPerformancePercent;
    private final int sdUploadMaxRetriesPerLine;
    private final int fileStreamingReadTimeoutMs;
    private final int fileStreamingQuietPeriodMs;
    private final int fileStreamingReadActivitySleepMs;
    private final int fileStreamingReadIdleSleepMs;
    private final int fileStreamingRecoveryReplayDelayMs;

    public SerialTransferSettings(
            int sdUploadBatchSize,
            int sdUploadRecoveryWindowMultiplier,
            int sdUploadMaxErrors,
            int sdUploadMaxConsecutiveIdenticalResends,
            int sdUploadMinPerformancePercent,
            int sdUploadMaxRetriesPerLine,
            int fileStreamingReadTimeoutMs,
            int fileStreamingQuietPeriodMs,
            int fileStreamingReadActivitySleepMs,
            int fileStreamingReadIdleSleepMs,
            int fileStreamingRecoveryReplayDelayMs) {
        requireBetween(
                sdUploadBatchSize,
                1,
                100,
                OperationMessages.SD_UPLOAD_BATCH_SIZE_MUST_BE_IN_RANGE);
        requireBetween(
                sdUploadRecoveryWindowMultiplier,
                1,
                100,
                OperationMessages.SD_UPLOAD_RECOVERY_WINDOW_MULTIPLIER_MUST_BE_IN_RANGE);
        requireBetween(
                sdUploadMaxErrors,
                1,
                1_000_000,
                OperationMessages.SD_UPLOAD_MAX_ERRORS_MUST_BE_IN_RANGE);
        requireBetween(
                sdUploadMaxConsecutiveIdenticalResends,
                1,
                1000,
                OperationMessages.SD_UPLOAD_MAX_CONSECUTIVE_IDENTICAL_RESENDS_MUST_BE_IN_RANGE);
        requireBetween(
                sdUploadMinPerformancePercent,
                0,
                100,
                OperationMessages.SD_UPLOAD_MIN_PERFORMANCE_PERCENT_MUST_BE_IN_RANGE);
        requireBetween(
                sdUploadMaxRetriesPerLine,
                1,
                100,
                OperationMessages.SD_UPLOAD_MAX_RETRIES_PER_LINE_MUST_BE_IN_RANGE);
        requireBetween(
                fileStreamingReadTimeoutMs,
                1,
                600_000,
                OperationMessages.FILE_STREAMING_READ_TIMEOUT_MS_MUST_BE_IN_RANGE);
        requireBetween(
                fileStreamingQuietPeriodMs,
                0,
                60_000,
                OperationMessages.FILE_STREAMING_QUIET_PERIOD_MS_MUST_BE_IN_RANGE);
        requireBetween(
                fileStreamingReadActivitySleepMs,
                0,
                60_000,
                OperationMessages.FILE_STREAMING_READ_ACTIVITY_SLEEP_MS_MUST_BE_IN_RANGE);
        requireBetween(
                fileStreamingReadIdleSleepMs,
                0,
                60_000,
                OperationMessages.FILE_STREAMING_READ_IDLE_SLEEP_MS_MUST_BE_IN_RANGE);
        requireBetween(
                fileStreamingRecoveryReplayDelayMs,
                0,
                60_000,
                OperationMessages.FILE_STREAMING_RECOVERY_REPLAY_DELAY_MS_MUST_BE_IN_RANGE);

        this.sdUploadBatchSize = sdUploadBatchSize;
        this.sdUploadRecoveryWindowMultiplier = sdUploadRecoveryWindowMultiplier;
        this.sdUploadMaxErrors = sdUploadMaxErrors;
        this.sdUploadMaxConsecutiveIdenticalResends = sdUploadMaxConsecutiveIdenticalResends;
        this.sdUploadMinPerformancePercent = sdUploadMinPerformancePercent;
        this.sdUploadMaxRetriesPerLine = sdUploadMaxRetriesPerLine;
        this.fileStreamingReadTimeoutMs = fileStreamingReadTimeoutMs;
        this.fileStreamingQuietPeriodMs = fileStreamingQuietPeriodMs;
        this.fileStreamingReadActivitySleepMs = fileStreamingReadActivitySleepMs;
        this.fileStreamingReadIdleSleepMs = fileStreamingReadIdleSleepMs;
        this.fileStreamingRecoveryReplayDelayMs = fileStreamingRecoveryReplayDelayMs;
    }

    private static void requireBetween(int value, int min, int max, String message) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(message);
        }
    }

    public static SerialTransferSettings defaults() {
        return new SerialTransferSettings(
                RuntimeDefaults.DEFAULT_SD_UPLOAD_BATCH_SIZE,
                RuntimeDefaults.DEFAULT_SD_UPLOAD_RECOVERY_WINDOW_MULTIPLIER,
                RuntimeDefaults.DEFAULT_SD_UPLOAD_MAX_ERRORS,
                RuntimeDefaults.DEFAULT_SD_UPLOAD_MAX_CONSECUTIVE_IDENTICAL_RESENDS,
                RuntimeDefaults.DEFAULT_SD_UPLOAD_MIN_PERFORMANCE_PERCENT,
                PrinterProtocolDefaults.SD_UPLOAD_MAX_RETRIES_PER_LINE,
                SerialDefaults.FILE_STREAMING_READ_TIMEOUT_MS,
                SerialDefaults.FILE_STREAMING_QUIET_PERIOD_MS,
                SerialDefaults.FILE_STREAMING_READ_ACTIVITY_SLEEP_MS,
                SerialDefaults.FILE_STREAMING_READ_IDLE_SLEEP_MS,
                SerialDefaults.FILE_STREAMING_RECOVERY_REPLAY_DELAY_MS);
    }

    public int sdUploadBatchSize() {
        return sdUploadBatchSize;
    }

    public int sdUploadRecoveryWindowMultiplier() {
        return sdUploadRecoveryWindowMultiplier;
    }

    public int sdUploadMaxErrors() {
        return sdUploadMaxErrors;
    }

    public int sdUploadMaxConsecutiveIdenticalResends() {
        return sdUploadMaxConsecutiveIdenticalResends;
    }

    public int sdUploadMinPerformancePercent() {
        return sdUploadMinPerformancePercent;
    }

    public int sdUploadMaxRetriesPerLine() {
        return sdUploadMaxRetriesPerLine;
    }

    public int fileStreamingReadTimeoutMs() {
        return fileStreamingReadTimeoutMs;
    }

    public int fileStreamingQuietPeriodMs() {
        return fileStreamingQuietPeriodMs;
    }

    public int fileStreamingReadActivitySleepMs() {
        return fileStreamingReadActivitySleepMs;
    }

    public int fileStreamingReadIdleSleepMs() {
        return fileStreamingReadIdleSleepMs;
    }

    public int fileStreamingRecoveryReplayDelayMs() {
        return fileStreamingRecoveryReplayDelayMs;
    }

        public SerialTransferSettings withSdUploadBatchSize(int value) {
        return new SerialTransferSettings(
                value,
                sdUploadRecoveryWindowMultiplier,
                sdUploadMaxErrors,
                sdUploadMaxConsecutiveIdenticalResends,
                sdUploadMinPerformancePercent,
                sdUploadMaxRetriesPerLine,
                fileStreamingReadTimeoutMs,
                fileStreamingQuietPeriodMs,
                fileStreamingReadActivitySleepMs,
                fileStreamingReadIdleSleepMs,
                fileStreamingRecoveryReplayDelayMs);
    }

    public SerialTransferSettings withSdUploadRecoveryWindowMultiplier(int value) {
        return new SerialTransferSettings(
                sdUploadBatchSize,
                value,
                sdUploadMaxErrors,
                sdUploadMaxConsecutiveIdenticalResends,
                sdUploadMinPerformancePercent,
                sdUploadMaxRetriesPerLine,
                fileStreamingReadTimeoutMs,
                fileStreamingQuietPeriodMs,
                fileStreamingReadActivitySleepMs,
                fileStreamingReadIdleSleepMs,
                fileStreamingRecoveryReplayDelayMs);
    }

    public SerialTransferSettings withSdUploadMaxErrors(int value) {
        return new SerialTransferSettings(
                sdUploadBatchSize,
                sdUploadRecoveryWindowMultiplier,
                value,
                sdUploadMaxConsecutiveIdenticalResends,
                sdUploadMinPerformancePercent,
                sdUploadMaxRetriesPerLine,
                fileStreamingReadTimeoutMs,
                fileStreamingQuietPeriodMs,
                fileStreamingReadActivitySleepMs,
                fileStreamingReadIdleSleepMs,
                fileStreamingRecoveryReplayDelayMs);
    }

    public SerialTransferSettings withSdUploadMaxConsecutiveIdenticalResends(int value) {
        return new SerialTransferSettings(
                sdUploadBatchSize,
                sdUploadRecoveryWindowMultiplier,
                sdUploadMaxErrors,
                value,
                sdUploadMinPerformancePercent,
                sdUploadMaxRetriesPerLine,
                fileStreamingReadTimeoutMs,
                fileStreamingQuietPeriodMs,
                fileStreamingReadActivitySleepMs,
                fileStreamingReadIdleSleepMs,
                fileStreamingRecoveryReplayDelayMs);
    }

    public SerialTransferSettings withSdUploadMinPerformancePercent(int value) {
        return new SerialTransferSettings(
                sdUploadBatchSize,
                sdUploadRecoveryWindowMultiplier,
                sdUploadMaxErrors,
                sdUploadMaxConsecutiveIdenticalResends,
                value,
                sdUploadMaxRetriesPerLine,
                fileStreamingReadTimeoutMs,
                fileStreamingQuietPeriodMs,
                fileStreamingReadActivitySleepMs,
                fileStreamingReadIdleSleepMs,
                fileStreamingRecoveryReplayDelayMs);
    }

    public SerialTransferSettings withSdUploadMaxRetriesPerLine(int value) {
        return new SerialTransferSettings(
                sdUploadBatchSize,
                sdUploadRecoveryWindowMultiplier,
                sdUploadMaxErrors,
                sdUploadMaxConsecutiveIdenticalResends,
                sdUploadMinPerformancePercent,
                value,
                fileStreamingReadTimeoutMs,
                fileStreamingQuietPeriodMs,
                fileStreamingReadActivitySleepMs,
                fileStreamingReadIdleSleepMs,
                fileStreamingRecoveryReplayDelayMs);
    }

    public SerialTransferSettings withFileStreamingReadTimeoutMs(int value) {
        return new SerialTransferSettings(
                sdUploadBatchSize,
                sdUploadRecoveryWindowMultiplier,
                sdUploadMaxErrors,
                sdUploadMaxConsecutiveIdenticalResends,
                sdUploadMinPerformancePercent,
                sdUploadMaxRetriesPerLine,
                value,
                fileStreamingQuietPeriodMs,
                fileStreamingReadActivitySleepMs,
                fileStreamingReadIdleSleepMs,
                fileStreamingRecoveryReplayDelayMs);
    }

    public SerialTransferSettings withFileStreamingQuietPeriodMs(int value) {
        return new SerialTransferSettings(
                sdUploadBatchSize,
                sdUploadRecoveryWindowMultiplier,
                sdUploadMaxErrors,
                sdUploadMaxConsecutiveIdenticalResends,
                sdUploadMinPerformancePercent,
                sdUploadMaxRetriesPerLine,
                fileStreamingReadTimeoutMs,
                value,
                fileStreamingReadActivitySleepMs,
                fileStreamingReadIdleSleepMs,
                fileStreamingRecoveryReplayDelayMs);
    }

    public SerialTransferSettings withFileStreamingReadActivitySleepMs(int value) {
        return new SerialTransferSettings(
                sdUploadBatchSize,
                sdUploadRecoveryWindowMultiplier,
                sdUploadMaxErrors,
                sdUploadMaxConsecutiveIdenticalResends,
                sdUploadMinPerformancePercent,
                sdUploadMaxRetriesPerLine,
                fileStreamingReadTimeoutMs,
                fileStreamingQuietPeriodMs,
                value,
                fileStreamingReadIdleSleepMs,
                fileStreamingRecoveryReplayDelayMs);
    }

    public SerialTransferSettings withFileStreamingReadIdleSleepMs(int value) {
        return new SerialTransferSettings(
                sdUploadBatchSize,
                sdUploadRecoveryWindowMultiplier,
                sdUploadMaxErrors,
                sdUploadMaxConsecutiveIdenticalResends,
                sdUploadMinPerformancePercent,
                sdUploadMaxRetriesPerLine,
                fileStreamingReadTimeoutMs,
                fileStreamingQuietPeriodMs,
                fileStreamingReadActivitySleepMs,
                value,
                fileStreamingRecoveryReplayDelayMs);
    }

    public SerialTransferSettings withFileStreamingRecoveryReplayDelayMs(int value) {
        return new SerialTransferSettings(
                sdUploadBatchSize,
                sdUploadRecoveryWindowMultiplier,
                sdUploadMaxErrors,
                sdUploadMaxConsecutiveIdenticalResends,
                sdUploadMinPerformancePercent,
                sdUploadMaxRetriesPerLine,
                fileStreamingReadTimeoutMs,
                fileStreamingQuietPeriodMs,
                fileStreamingReadActivitySleepMs,
                fileStreamingReadIdleSleepMs,
                value);
    }


}
