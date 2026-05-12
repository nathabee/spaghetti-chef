package printerhub.command;

import printerhub.persistence.SerialTransferSettings;

public final class SdUploadRuntimeState {

    private final boolean debugWireTracingEnabled;

    private final int configuredMaxBatchSize;
    private final int configuredMinBatchSize;
    private final int batchUpgradeStep;
    private final int batchDowngradeStep;
    private final int stableLinesForUpgrade;
    private final int resendWindowLines;
    private final int resendThresholdForDowngrade;
    private final int recoveryThresholdForMinBatch;

    private final int recoveryWindowMultiplier;
    private final int maxErrors;
    private final int maxConsecutiveIdenticalResends;
    private final int minPerformancePercent;
    private final int maxRetriesPerLine;
    private final int fileStreamingQuietPeriodMs;
    private final int fileStreamingRecoveryReplayDelayMs;

    private int activeBatchSize;
    private boolean singleSendMode;

    private long acceptedLinesSinceLastResend;
    private long totalAcceptedLines;
    private long totalRejectedLines;
    private int recentResendCount;
    private int recentRecoveryCount;
    private int consecutiveIdenticalResends;
    private int modeTransitionCount;

    public SdUploadRuntimeState(
            boolean debugWireTracingEnabled,
            int configuredMaxBatchSize,
            int configuredMinBatchSize,
            int batchUpgradeStep,
            int batchDowngradeStep,
            int stableLinesForUpgrade,
            int resendWindowLines,
            int resendThresholdForDowngrade,
            int recoveryThresholdForMinBatch,
            int recoveryWindowMultiplier,
            int maxErrors,
            int maxConsecutiveIdenticalResends,
            int minPerformancePercent,
            int maxRetriesPerLine,
            int fileStreamingQuietPeriodMs,
            int fileStreamingRecoveryReplayDelayMs) {
        this.debugWireTracingEnabled = debugWireTracingEnabled;
        this.configuredMaxBatchSize = requirePositive(configuredMaxBatchSize, "configuredMaxBatchSize");
        this.configuredMinBatchSize = requirePositive(configuredMinBatchSize, "configuredMinBatchSize");
        this.batchUpgradeStep = requirePositive(batchUpgradeStep, "batchUpgradeStep");
        this.batchDowngradeStep = requirePositive(batchDowngradeStep, "batchDowngradeStep");
        this.stableLinesForUpgrade = requirePositive(stableLinesForUpgrade, "stableLinesForUpgrade");
        this.resendWindowLines = requirePositive(resendWindowLines, "resendWindowLines");
        this.resendThresholdForDowngrade = requirePositive(
                resendThresholdForDowngrade,
                "resendThresholdForDowngrade");
        this.recoveryThresholdForMinBatch = requirePositive(
                recoveryThresholdForMinBatch,
                "recoveryThresholdForMinBatch");
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

        if (this.configuredMinBatchSize > this.configuredMaxBatchSize) {
            throw new IllegalArgumentException("configuredMinBatchSize must not exceed configuredMaxBatchSize");
        }

        this.activeBatchSize = this.configuredMaxBatchSize;
        this.singleSendMode = this.activeBatchSize <= 1;

        this.acceptedLinesSinceLastResend = 0L;
        this.totalAcceptedLines = 0L;
        this.totalRejectedLines = 0L;
        this.recentResendCount = 0;
        this.recentRecoveryCount = 0;
        this.consecutiveIdenticalResends = 0;
        this.modeTransitionCount = 0;
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
                settings.sdUploadMinBatchSize(),
                settings.sdUploadBatchUpgradeStep(),
                settings.sdUploadBatchDowngradeStep(),
                settings.sdUploadStableLinesForUpgrade(),
                settings.sdUploadResendWindowLines(),
                settings.sdUploadResendThresholdForDowngrade(),
                settings.sdUploadRecoveryThresholdForMinBatch(),
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

    public int configuredMinBatchSize() {
        return configuredMinBatchSize;
    }

    public int batchUpgradeStep() {
        return batchUpgradeStep;
    }

    public int batchDowngradeStep() {
        return batchDowngradeStep;
    }

    public int stableLinesForUpgrade() {
        return stableLinesForUpgrade;
    }

    public int resendWindowLines() {
        return resendWindowLines;
    }

    public int resendThresholdForDowngrade() {
        return resendThresholdForDowngrade;
    }

    public int recoveryThresholdForMinBatch() {
        return recoveryThresholdForMinBatch;
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

    public long acceptedLinesSinceLastResend() {
        return acceptedLinesSinceLastResend;
    }

    public long totalAcceptedLines() {
        return totalAcceptedLines;
    }

    public long totalRejectedLines() {
        return totalRejectedLines;
    }

    public int recentResendCount() {
        return recentResendCount;
    }

    public int recentRecoveryCount() {
        return recentRecoveryCount;
    }

    public int consecutiveIdenticalResends() {
        return consecutiveIdenticalResends;
    }

    public int modeTransitionCount() {
        return modeTransitionCount;
    }

    public void recordAcceptedLine() {
        acceptedLinesSinceLastResend++;
        totalAcceptedLines++;

        if (recentResendCount > 0 && acceptedLinesSinceLastResend >= resendWindowLines) {
            recentResendCount = 0;
        }
    }

    public void recordRejectedLine() {
        totalRejectedLines++;
    }

    public void recordResend() {
        recentResendCount++;
        acceptedLinesSinceLastResend = 0L;
    }

    public void recordRecovery() {
        recentRecoveryCount++;
    }

    public void resetRecentRecoveryCount() {
        recentRecoveryCount = 0;
    }

    public void recordIdenticalResend() {
        consecutiveIdenticalResends++;
    }

    public void resetConsecutiveIdenticalResends() {
        consecutiveIdenticalResends = 0;
    }

    public boolean shouldDowngradeBatch() {
        return recentResendCount >= resendThresholdForDowngrade
                && activeBatchSize > configuredMinBatchSize;
    }

    public boolean shouldForceMinBatch() {
        return recentRecoveryCount >= recoveryThresholdForMinBatch
                && activeBatchSize > configuredMinBatchSize;
    }

    public boolean shouldUpgradeBatch() {
        return acceptedLinesSinceLastResend >= stableLinesForUpgrade
                && activeBatchSize < configuredMaxBatchSize;
    }

    public void downgradeBatch() {
        setActiveBatchSize(Math.max(configuredMinBatchSize, activeBatchSize - batchDowngradeStep));
    }

    public void upgradeBatch() {
        setActiveBatchSize(Math.min(configuredMaxBatchSize, activeBatchSize + batchUpgradeStep));
        acceptedLinesSinceLastResend = 0L;
    }

    public void forceMinBatch() {
        setActiveBatchSize(configuredMinBatchSize);
    }

    public void enableSingleSendMode() {
        setActiveBatchSize(1);
    }

    public void disableSingleSendModeIfPossible() {
        if (activeBatchSize > 1) {
            singleSendMode = false;
        }
    }

    public void setActiveBatchSize(int activeBatchSize) {
        int normalized = requirePositive(activeBatchSize, "activeBatchSize");

        if (normalized < configuredMinBatchSize) {
            normalized = configuredMinBatchSize;
        }
        if (normalized > configuredMaxBatchSize) {
            normalized = configuredMaxBatchSize;
        }

        if (this.activeBatchSize != normalized) {
            modeTransitionCount++;
        }

        this.activeBatchSize = normalized;
        this.singleSendMode = this.activeBatchSize <= 1;
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