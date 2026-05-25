package spaghettichef.persistence;

import spaghettichef.OperationMessages;
import spaghettichef.config.RuntimeDefaults;

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

    public MonitoringRules(
            long pollIntervalSeconds,
            long snapshotMinimumIntervalSeconds,
            double temperatureDeltaThreshold,
            long eventDeduplicationWindowSeconds,
            ErrorPersistenceBehavior errorPersistenceBehavior,
            boolean debugWireTracingEnabled) {
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

        this.pollIntervalSeconds = pollIntervalSeconds;
        this.snapshotMinimumIntervalSeconds = snapshotMinimumIntervalSeconds;
        this.temperatureDeltaThreshold = temperatureDeltaThreshold;
        this.eventDeduplicationWindowSeconds = eventDeduplicationWindowSeconds;
        this.errorPersistenceBehavior = errorPersistenceBehavior;
        this.debugWireTracingEnabled = debugWireTracingEnabled;
    }

    public static MonitoringRules defaults() {
        return new MonitoringRules(
                RuntimeDefaults.DEFAULT_MONITORING_INTERVAL_SECONDS,
                RuntimeDefaults.DEFAULT_MIN_SNAPSHOT_INTERVAL_SECONDS,
                RuntimeDefaults.DEFAULT_TEMPERATURE_THRESHOLD,
                RuntimeDefaults.DEFAULT_MONITORING_EVENT_DEDUP_WINDOW_SECONDS,
                parseErrorPersistenceBehavior(RuntimeDefaults.DEFAULT_ERROR_PERSISTENCE_BEHAVIOR),
                RuntimeDefaults.DEFAULT_TRACE);
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

    public MonitoringRules withDebugWireTracingEnabled(boolean value) {
        return new MonitoringRules(
                pollIntervalSeconds,
                snapshotMinimumIntervalSeconds,
                temperatureDeltaThreshold,
                eventDeduplicationWindowSeconds,
                errorPersistenceBehavior,
                value);
    }
}