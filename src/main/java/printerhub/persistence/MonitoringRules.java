package printerhub.persistence;

import printerhub.OperationMessages;
import printerhub.config.RuntimeDefaults;

public final class MonitoringRules {

    public enum ErrorPersistenceBehavior {
        DEDUPLICATED,
        ALWAYS
    }

    private final long pollIntervalSeconds;
    private final long snapshotMinimumIntervalSeconds;
    private final double temperatureDeltaThreshold;
    private final long eventDeduplicationWindowSeconds;
    private final ErrorPersistenceBehavior errorPersistenceBehavior;
    private final boolean debugWireTracingEnabled;
    private final int sdUploadBatchSize;
    private final int sdUploadRecoveryWindowMultiplier;
    private final int sdUploadMaxErrors;
    private final int sdUploadMaxConsecutiveIdenticalResends;
    private final int sdUploadMinPerformancePercent;

    public MonitoringRules(
            long pollIntervalSeconds,
            long snapshotMinimumIntervalSeconds,
            double temperatureDeltaThreshold,
            long eventDeduplicationWindowSeconds,
            ErrorPersistenceBehavior errorPersistenceBehavior,
            boolean debugWireTracingEnabled,
            int sdUploadBatchSize,
            int sdUploadRecoveryWindowMultiplier,
            int sdUploadMaxErrors,
            int sdUploadMaxConsecutiveIdenticalResends,
            int sdUploadMinPerformancePercent) {
        if (pollIntervalSeconds <= 0) {
            throw new IllegalArgumentException(
                    OperationMessages.POLL_INTERVAL_SECONDS_MUST_BE_GREATER_THAN_ZERO);
        }

        if (snapshotMinimumIntervalSeconds < 0) {
            throw new IllegalArgumentException(
                    OperationMessages.SNAPSHOT_MINIMUM_INTERVAL_SECONDS_MUST_NOT_BE_NEGATIVE);
        }

        if (temperatureDeltaThreshold < 0) {
            throw new IllegalArgumentException(
                    OperationMessages.TEMPERATURE_DELTA_THRESHOLD_MUST_NOT_BE_NEGATIVE);
        }

        if (eventDeduplicationWindowSeconds < 0) {
            throw new IllegalArgumentException(
                    OperationMessages.EVENT_DEDUPLICATION_WINDOW_SECONDS_MUST_NOT_BE_NEGATIVE);
        }

        if (errorPersistenceBehavior == null) {
            throw new IllegalArgumentException(
                    OperationMessages.ERROR_PERSISTENCE_BEHAVIOR_MUST_NOT_BE_NULL);
        }

        if (sdUploadBatchSize < 1 || sdUploadBatchSize > 100) {
            throw new IllegalArgumentException(
                    "sdUploadBatchSize must be between 1 and 100");
        }

        if (sdUploadRecoveryWindowMultiplier < 1 || sdUploadRecoveryWindowMultiplier > 100) {
            throw new IllegalArgumentException(
                    "sdUploadRecoveryWindowMultiplier must be between 1 and 100");
        }

        if (sdUploadMaxErrors < 1 || sdUploadMaxErrors > 1_000_000) {
            throw new IllegalArgumentException(
                    "sdUploadMaxErrors must be between 1 and 1000000");
        }

        if (sdUploadMaxConsecutiveIdenticalResends < 1 || sdUploadMaxConsecutiveIdenticalResends > 1000) {
            throw new IllegalArgumentException(
                    "sdUploadMaxConsecutiveIdenticalResends must be between 1 and 1000");
        }

        if (sdUploadMinPerformancePercent < 0 || sdUploadMinPerformancePercent > 100) {
            throw new IllegalArgumentException(
                    "sdUploadMinPerformancePercent must be between 0 and 100");
        }

        this.pollIntervalSeconds = pollIntervalSeconds;
        this.snapshotMinimumIntervalSeconds = snapshotMinimumIntervalSeconds;
        this.temperatureDeltaThreshold = temperatureDeltaThreshold;
        this.eventDeduplicationWindowSeconds = eventDeduplicationWindowSeconds;
        this.errorPersistenceBehavior = errorPersistenceBehavior;
        this.debugWireTracingEnabled = debugWireTracingEnabled;
        this.sdUploadBatchSize = sdUploadBatchSize;
        this.sdUploadRecoveryWindowMultiplier = sdUploadRecoveryWindowMultiplier;
        this.sdUploadMaxErrors = sdUploadMaxErrors;
        this.sdUploadMaxConsecutiveIdenticalResends = sdUploadMaxConsecutiveIdenticalResends;
        this.sdUploadMinPerformancePercent = sdUploadMinPerformancePercent;
    }

    public static MonitoringRules defaults() {
        return new MonitoringRules(
                RuntimeDefaults.DEFAULT_MONITORING_INTERVAL_SECONDS,
                RuntimeDefaults.DEFAULT_MIN_SNAPSHOT_INTERVAL_SECONDS,
                RuntimeDefaults.DEFAULT_TEMPERATURE_THRESHOLD,
                RuntimeDefaults.DEFAULT_MONITORING_EVENT_DEDUP_WINDOW_SECONDS,
                parseErrorPersistenceBehavior(RuntimeDefaults.DEFAULT_ERROR_PERSISTENCE_BEHAVIOR),
                RuntimeDefaults.DEFAULT_TRACE,
                RuntimeDefaults.DEFAULT_SD_UPLOAD_BATCH_SIZE,
                RuntimeDefaults.DEFAULT_SD_UPLOAD_RECOVERY_WINDOW_MULTIPLIER,
                RuntimeDefaults.DEFAULT_SD_UPLOAD_MAX_ERRORS,
                RuntimeDefaults.DEFAULT_SD_UPLOAD_MAX_CONSECUTIVE_IDENTICAL_RESENDS,
                RuntimeDefaults.DEFAULT_SD_UPLOAD_MIN_PERFORMANCE_PERCENT);
    }

    public long pollIntervalSeconds() {
        return pollIntervalSeconds;
    }

    public long snapshotMinimumIntervalSeconds() {
        return snapshotMinimumIntervalSeconds;
    }

    public double temperatureDeltaThreshold() {
        return temperatureDeltaThreshold;
    }

    public long eventDeduplicationWindowSeconds() {
        return eventDeduplicationWindowSeconds;
    }

    public ErrorPersistenceBehavior errorPersistenceBehavior() {
        return errorPersistenceBehavior;
    }

    public boolean debugWireTracingEnabled() {
        return debugWireTracingEnabled;
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

    public static ErrorPersistenceBehavior parseErrorPersistenceBehavior(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    OperationMessages.ERROR_PERSISTENCE_BEHAVIOR_MUST_NOT_BE_NULL);
        }

        try {
            return ErrorPersistenceBehavior.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    OperationMessages.invalidErrorPersistenceBehavior(value),
                    exception);
        }
    }
}