package spaghettichef.persistence;

import spaghettichef.OperationMessages;
import spaghettichef.config.PrinterProtocolDefaults;
import spaghettichef.config.RuntimeDefaults;
import spaghettichef.config.SerialDefaults;

public final class SerialTransferSettings {

    private static final int DEFAULT_SD_UPLOAD_MIN_BATCH_SIZE = 1;
    private static final int DEFAULT_SD_UPLOAD_BATCH_UPGRADE_STEP = 1;
    private static final int DEFAULT_SD_UPLOAD_BATCH_DOWNGRADE_STEP = 1;
    private static final int DEFAULT_SD_UPLOAD_STABLE_LINES_FOR_UPGRADE = 200;
    private static final int DEFAULT_SD_UPLOAD_RESEND_WINDOW_LINES = 50;
    private static final int DEFAULT_SD_UPLOAD_RESEND_THRESHOLD_FOR_DOWNGRADE = 1;
    private static final int DEFAULT_SD_UPLOAD_RECOVERY_THRESHOLD_FOR_MIN_BATCH = 3;

    private final int sdUploadBatchSize;
    private final int sdUploadMinBatchSize;
    private final int sdUploadBatchUpgradeStep;
    private final int sdUploadBatchDowngradeStep;
    private final int sdUploadStableLinesForUpgrade;
    private final int sdUploadResendWindowLines;
    private final int sdUploadResendThresholdForDowngrade;
    private final int sdUploadRecoveryThresholdForMinBatch;
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
            int sdUploadMinBatchSize,
            int sdUploadBatchUpgradeStep,
            int sdUploadBatchDowngradeStep,
            int sdUploadStableLinesForUpgrade,
            int sdUploadResendWindowLines,
            int sdUploadResendThresholdForDowngrade,
            int sdUploadRecoveryThresholdForMinBatch,
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
                sdUploadMinBatchSize,
                1,
                100,
                "sdUploadMinBatchSize must be between 1 and 100");
        requireBetween(
                sdUploadBatchUpgradeStep,
                1,
                100,
                "sdUploadBatchUpgradeStep must be between 1 and 100");
        requireBetween(
                sdUploadBatchDowngradeStep,
                1,
                100,
                "sdUploadBatchDowngradeStep must be between 1 and 100");
        requireBetween(
                sdUploadStableLinesForUpgrade,
                1,
                1_000_000,
                "sdUploadStableLinesForUpgrade must be between 1 and 1000000");
        requireBetween(
                sdUploadResendWindowLines,
                1,
                1_000_000,
                "sdUploadResendWindowLines must be between 1 and 1000000");
        requireBetween(
                sdUploadResendThresholdForDowngrade,
                1,
                1_000_000,
                "sdUploadResendThresholdForDowngrade must be between 1 and 1000000");
        requireBetween(
                sdUploadRecoveryThresholdForMinBatch,
                1,
                1_000_000,
                "sdUploadRecoveryThresholdForMinBatch must be between 1 and 1000000");
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

        if (sdUploadMinBatchSize > sdUploadBatchSize) {
            throw new IllegalArgumentException("sdUploadMinBatchSize must not exceed sdUploadBatchSize");
        }

        this.sdUploadBatchSize = sdUploadBatchSize;
        this.sdUploadMinBatchSize = sdUploadMinBatchSize;
        this.sdUploadBatchUpgradeStep = sdUploadBatchUpgradeStep;
        this.sdUploadBatchDowngradeStep = sdUploadBatchDowngradeStep;
        this.sdUploadStableLinesForUpgrade = sdUploadStableLinesForUpgrade;
        this.sdUploadResendWindowLines = sdUploadResendWindowLines;
        this.sdUploadResendThresholdForDowngrade = sdUploadResendThresholdForDowngrade;
        this.sdUploadRecoveryThresholdForMinBatch = sdUploadRecoveryThresholdForMinBatch;
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
                DEFAULT_SD_UPLOAD_MIN_BATCH_SIZE,
                DEFAULT_SD_UPLOAD_BATCH_UPGRADE_STEP,
                DEFAULT_SD_UPLOAD_BATCH_DOWNGRADE_STEP,
                DEFAULT_SD_UPLOAD_STABLE_LINES_FOR_UPGRADE,
                DEFAULT_SD_UPLOAD_RESEND_WINDOW_LINES,
                DEFAULT_SD_UPLOAD_RESEND_THRESHOLD_FOR_DOWNGRADE,
                DEFAULT_SD_UPLOAD_RECOVERY_THRESHOLD_FOR_MIN_BATCH,
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

    public int sdUploadMinBatchSize() {
        return sdUploadMinBatchSize;
    }

    public int sdUploadBatchUpgradeStep() {
        return sdUploadBatchUpgradeStep;
    }

    public int sdUploadBatchDowngradeStep() {
        return sdUploadBatchDowngradeStep;
    }

    public int sdUploadStableLinesForUpgrade() {
        return sdUploadStableLinesForUpgrade;
    }

    public int sdUploadResendWindowLines() {
        return sdUploadResendWindowLines;
    }

    public int sdUploadResendThresholdForDowngrade() {
        return sdUploadResendThresholdForDowngrade;
    }

    public int sdUploadRecoveryThresholdForMinBatch() {
        return sdUploadRecoveryThresholdForMinBatch;
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
                sdUploadMinBatchSize,
                sdUploadBatchUpgradeStep,
                sdUploadBatchDowngradeStep,
                sdUploadStableLinesForUpgrade,
                sdUploadResendWindowLines,
                sdUploadResendThresholdForDowngrade,
                sdUploadRecoveryThresholdForMinBatch,
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

    public SerialTransferSettings withSdUploadMinBatchSize(int value) {
        return new SerialTransferSettings(
                sdUploadBatchSize,
                value,
                sdUploadBatchUpgradeStep,
                sdUploadBatchDowngradeStep,
                sdUploadStableLinesForUpgrade,
                sdUploadResendWindowLines,
                sdUploadResendThresholdForDowngrade,
                sdUploadRecoveryThresholdForMinBatch,
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

    public SerialTransferSettings withSdUploadBatchUpgradeStep(int value) {
        return new SerialTransferSettings(
                sdUploadBatchSize,
                sdUploadMinBatchSize,
                value,
                sdUploadBatchDowngradeStep,
                sdUploadStableLinesForUpgrade,
                sdUploadResendWindowLines,
                sdUploadResendThresholdForDowngrade,
                sdUploadRecoveryThresholdForMinBatch,
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

    public SerialTransferSettings withSdUploadBatchDowngradeStep(int value) {
        return new SerialTransferSettings(
                sdUploadBatchSize,
                sdUploadMinBatchSize,
                sdUploadBatchUpgradeStep,
                value,
                sdUploadStableLinesForUpgrade,
                sdUploadResendWindowLines,
                sdUploadResendThresholdForDowngrade,
                sdUploadRecoveryThresholdForMinBatch,
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

    public SerialTransferSettings withSdUploadStableLinesForUpgrade(int value) {
        return new SerialTransferSettings(
                sdUploadBatchSize,
                sdUploadMinBatchSize,
                sdUploadBatchUpgradeStep,
                sdUploadBatchDowngradeStep,
                value,
                sdUploadResendWindowLines,
                sdUploadResendThresholdForDowngrade,
                sdUploadRecoveryThresholdForMinBatch,
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

    public SerialTransferSettings withSdUploadResendWindowLines(int value) {
        return new SerialTransferSettings(
                sdUploadBatchSize,
                sdUploadMinBatchSize,
                sdUploadBatchUpgradeStep,
                sdUploadBatchDowngradeStep,
                sdUploadStableLinesForUpgrade,
                value,
                sdUploadResendThresholdForDowngrade,
                sdUploadRecoveryThresholdForMinBatch,
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

    public SerialTransferSettings withSdUploadResendThresholdForDowngrade(int value) {
        return new SerialTransferSettings(
                sdUploadBatchSize,
                sdUploadMinBatchSize,
                sdUploadBatchUpgradeStep,
                sdUploadBatchDowngradeStep,
                sdUploadStableLinesForUpgrade,
                sdUploadResendWindowLines,
                value,
                sdUploadRecoveryThresholdForMinBatch,
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

    public SerialTransferSettings withSdUploadRecoveryThresholdForMinBatch(int value) {
        return new SerialTransferSettings(
                sdUploadBatchSize,
                sdUploadMinBatchSize,
                sdUploadBatchUpgradeStep,
                sdUploadBatchDowngradeStep,
                sdUploadStableLinesForUpgrade,
                sdUploadResendWindowLines,
                sdUploadResendThresholdForDowngrade,
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
                sdUploadMinBatchSize,
                sdUploadBatchUpgradeStep,
                sdUploadBatchDowngradeStep,
                sdUploadStableLinesForUpgrade,
                sdUploadResendWindowLines,
                sdUploadResendThresholdForDowngrade,
                sdUploadRecoveryThresholdForMinBatch,
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
                sdUploadMinBatchSize,
                sdUploadBatchUpgradeStep,
                sdUploadBatchDowngradeStep,
                sdUploadStableLinesForUpgrade,
                sdUploadResendWindowLines,
                sdUploadResendThresholdForDowngrade,
                sdUploadRecoveryThresholdForMinBatch,
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
                sdUploadMinBatchSize,
                sdUploadBatchUpgradeStep,
                sdUploadBatchDowngradeStep,
                sdUploadStableLinesForUpgrade,
                sdUploadResendWindowLines,
                sdUploadResendThresholdForDowngrade,
                sdUploadRecoveryThresholdForMinBatch,
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
                sdUploadMinBatchSize,
                sdUploadBatchUpgradeStep,
                sdUploadBatchDowngradeStep,
                sdUploadStableLinesForUpgrade,
                sdUploadResendWindowLines,
                sdUploadResendThresholdForDowngrade,
                sdUploadRecoveryThresholdForMinBatch,
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
                sdUploadMinBatchSize,
                sdUploadBatchUpgradeStep,
                sdUploadBatchDowngradeStep,
                sdUploadStableLinesForUpgrade,
                sdUploadResendWindowLines,
                sdUploadResendThresholdForDowngrade,
                sdUploadRecoveryThresholdForMinBatch,
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
                sdUploadMinBatchSize,
                sdUploadBatchUpgradeStep,
                sdUploadBatchDowngradeStep,
                sdUploadStableLinesForUpgrade,
                sdUploadResendWindowLines,
                sdUploadResendThresholdForDowngrade,
                sdUploadRecoveryThresholdForMinBatch,
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
                sdUploadMinBatchSize,
                sdUploadBatchUpgradeStep,
                sdUploadBatchDowngradeStep,
                sdUploadStableLinesForUpgrade,
                sdUploadResendWindowLines,
                sdUploadResendThresholdForDowngrade,
                sdUploadRecoveryThresholdForMinBatch,
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
                sdUploadMinBatchSize,
                sdUploadBatchUpgradeStep,
                sdUploadBatchDowngradeStep,
                sdUploadStableLinesForUpgrade,
                sdUploadResendWindowLines,
                sdUploadResendThresholdForDowngrade,
                sdUploadRecoveryThresholdForMinBatch,
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
                sdUploadMinBatchSize,
                sdUploadBatchUpgradeStep,
                sdUploadBatchDowngradeStep,
                sdUploadStableLinesForUpgrade,
                sdUploadResendWindowLines,
                sdUploadResendThresholdForDowngrade,
                sdUploadRecoveryThresholdForMinBatch,
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
                sdUploadMinBatchSize,
                sdUploadBatchUpgradeStep,
                sdUploadBatchDowngradeStep,
                sdUploadStableLinesForUpgrade,
                sdUploadResendWindowLines,
                sdUploadResendThresholdForDowngrade,
                sdUploadRecoveryThresholdForMinBatch,
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