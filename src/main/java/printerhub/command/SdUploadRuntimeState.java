package printerhub.command;

import printerhub.persistence.SerialTransferSettings;

public final class SdUploadRuntimeState {

    private final boolean debugWireTracingEnabled;
    private final int configuredMaxBatchSize;
    private final int recoveryWindowMultiplier;
    private final int maxErrors;
    private final int maxConsecutiveIdenticalResends;
    private final int minPerformancePercent;
    private final int maxRetriesPerLine;
    private final int fileStreamingQuietPeriodMs;
    private final int fileStreamingRecoveryReplayDelayMs;

    private int activeBatchSize;
    private boolean singleSendMode;

    public SdUploadRuntimeState(
            boolean debugWireTracingEnabled,
            int configuredMaxBatchSize,
            int recoveryWindowMultiplier,
            int maxErrors,
            int maxConsecutiveIdenticalResends,
            int minPerformancePercent,
            int maxRetriesPerLine,
            int fileStreamingQuietPeriodMs,
            int fileStreamingRecoveryReplayDelayMs) {
        this.debugWireTracingEnabled = debugWireTracingEnabled;
        this.configuredMaxBatchSize = requirePositive(configuredMaxBatchSize, "configuredMaxBatchSize");
        this.recoveryWindowMultiplier = requirePositive(recoveryWindowMultiplier, "recoveryWindowMultiplier");
        this.maxErrors = requirePositive(maxErrors, "maxErrors");
        this.maxConsecutiveIdenticalResends = requirePositive(
                maxConsecutiveIdenticalResends,
                "maxConsecutiveIdenticalResends");
        this.minPerformancePercent = requireRange(minPerformancePercent, 0, 100, "minPerformancePercent");
        this.maxRetriesPerLine = requirePositive(maxRetriesPerLine, "maxRetriesPerLine");
        this.fileStreamingQuietPeriodMs = requireNonNegative(fileStreamingQuietPeriodMs, "fileStreamingQuietPeriodMs");
        this.fileStreamingRecoveryReplayDelayMs = requireNonNegative(
                fileStreamingRecoveryReplayDelayMs,
                "fileStreamingRecoveryReplayDelayMs");

        this.activeBatchSize = this.configuredMaxBatchSize;
        this.singleSendMode = false;
    }

    public static SdUploadRuntimeState from(
            boolean debugWireTracingEnabled,
            SerialTransferSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("serialTransferSettings must not be null");
        }

        return new SdUploadRuntimeState(
                debugWireTracingEnabled,
                settings.sdUploadBatchSize(),
                settings.sdUploadRecoveryWindowMultiplier(),
                settings.sdUploadMaxErrors(),
                settings.sdUploadMaxConsecutiveIdenticalResends(),
                settings.sdUploadMinPerformancePercent(),
                settings.sdUploadMaxRetriesPerLine(),
                settings.fileStreamingQuietPeriodMs(),
                settings.fileStreamingRecoveryReplayDelayMs());
    }

    public boolean debugWireTracingEnabled() {
        return debugWireTracingEnabled;
    }

    public int configuredMaxBatchSize() {
        return configuredMaxBatchSize;
    }

    public int recoveryWindowMultiplier() {
        return recoveryWindowMultiplier;
    }

    public int maxErrors() {
        return maxErrors;
    }

    public int maxConsecutiveIdenticalResends() {
        return maxConsecutiveIdenticalResends;
    }

    public int minPerformancePercent() {
        return minPerformancePercent;
    }

    public int maxRetriesPerLine() {
        return maxRetriesPerLine;
    }

    public int fileStreamingQuietPeriodMs() {
        return fileStreamingQuietPeriodMs;
    }

    public int fileStreamingRecoveryReplayDelayMs() {
        return fileStreamingRecoveryReplayDelayMs;
    }

    public int activeBatchSize() {
        return activeBatchSize;
    }

    public boolean singleSendMode() {
        return singleSendMode;
    }

    public void enableSingleSendMode() {
        this.singleSendMode = true;
        this.activeBatchSize = 1;
    }

    public void setActiveBatchSize(int activeBatchSize) {
        this.activeBatchSize = requirePositive(activeBatchSize, "activeBatchSize");
        if (this.activeBatchSize > configuredMaxBatchSize) {
            this.activeBatchSize = configuredMaxBatchSize;
        }
        this.singleSendMode = this.activeBatchSize == 1;
    }

    private static int requirePositive(int value, String fieldName) {
        if (value < 1) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero");
        }
        return value;
    }

    private static int requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        return value;
    }

    private static int requireRange(int value, int min, int max, String fieldName) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(fieldName + " must be between " + min + " and " + max);
        }
        return value;
    }
}