package printerhub.persistence;

import org.junit.jupiter.api.Test;
import printerhub.OperationMessages;
import printerhub.config.RuntimeDefaults;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MonitoringRulesTest {

    @Test
    void constructorStoresValues() {
        MonitoringRules rules = new MonitoringRules(
                12,
                45,
                2.5,
                90,
                MonitoringRules.ErrorPersistenceBehavior.ALWAYS,
                true);

        assertEquals(12, rules.pollIntervalSeconds());
        assertEquals(45, rules.snapshotMinimumIntervalSeconds());
        assertEquals(2.5, rules.temperatureDeltaThreshold());
        assertEquals(90, rules.eventDeduplicationWindowSeconds());
        assertEquals(MonitoringRules.ErrorPersistenceBehavior.ALWAYS, rules.errorPersistenceBehavior());
        assertEquals(true, rules.debugWireTracingEnabled());
    }

    @Test
    void defaultsUseRuntimeDefaults() {
        MonitoringRules rules = MonitoringRules.defaults();

        assertEquals(
                RuntimeDefaults.DEFAULT_MONITORING_INTERVAL_SECONDS,
                rules.pollIntervalSeconds());
        assertEquals(
                RuntimeDefaults.DEFAULT_MIN_SNAPSHOT_INTERVAL_SECONDS,
                rules.snapshotMinimumIntervalSeconds());
        assertEquals(
                RuntimeDefaults.DEFAULT_TEMPERATURE_THRESHOLD,
                rules.temperatureDeltaThreshold());
        assertEquals(
                RuntimeDefaults.DEFAULT_MONITORING_EVENT_DEDUP_WINDOW_SECONDS,
                rules.eventDeduplicationWindowSeconds());
        assertEquals(
                MonitoringRules.ErrorPersistenceBehavior.DEDUPLICATED,
                rules.errorPersistenceBehavior());
        assertEquals(
                RuntimeDefaults.DEFAULT_TRACE,
                rules.debugWireTracingEnabled());
    }

    @Test
    void constructorAllowsZeroTemperatureDeltaThreshold() {
        MonitoringRules rules = new MonitoringRules(
                5,
                30,
                0.0,
                60,
                MonitoringRules.ErrorPersistenceBehavior.DEDUPLICATED,
                false);

        assertEquals(0.0, rules.temperatureDeltaThreshold());
    }

    @Test
    void constructorAllowsZeroSnapshotMinimumIntervalSeconds() {
        MonitoringRules rules = new MonitoringRules(
                5,
                0,
                1.0,
                60,
                MonitoringRules.ErrorPersistenceBehavior.DEDUPLICATED,
                false);

        assertEquals(0, rules.snapshotMinimumIntervalSeconds());
    }

    @Test
    void constructorAllowsZeroEventDeduplicationWindowSeconds() {
        MonitoringRules rules = new MonitoringRules(
                5,
                30,
                1.0,
                0,
                MonitoringRules.ErrorPersistenceBehavior.DEDUPLICATED,
                false);

        assertEquals(0, rules.eventDeduplicationWindowSeconds());
    }

    @Test
    void constructorFailsForZeroPollIntervalSeconds() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new MonitoringRules(
                        0,
                        30,
                        1.0,
                        60,
                        MonitoringRules.ErrorPersistenceBehavior.DEDUPLICATED,
                        false));

        assertEquals(
                OperationMessages.POLL_INTERVAL_SECONDS_MUST_BE_GREATER_THAN_ZERO,
                exception.getMessage());
    }

    @Test
    void constructorFailsForNegativeSnapshotMinimumIntervalSeconds() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new MonitoringRules(
                        5,
                        -1,
                        1.0,
                        60,
                        MonitoringRules.ErrorPersistenceBehavior.DEDUPLICATED,
                        false));

        assertEquals(
                OperationMessages.SNAPSHOT_MINIMUM_INTERVAL_SECONDS_MUST_NOT_BE_NEGATIVE,
                exception.getMessage());
    }

    @Test
    void constructorFailsForNegativeTemperatureDeltaThreshold() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new MonitoringRules(
                        5,
                        30,
                        -0.1,
                        60,
                        MonitoringRules.ErrorPersistenceBehavior.DEDUPLICATED,
                        false));

        assertEquals(
                OperationMessages.TEMPERATURE_DELTA_THRESHOLD_MUST_NOT_BE_NEGATIVE,
                exception.getMessage());
    }

    @Test
    void constructorFailsForNegativeEventDeduplicationWindowSeconds() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new MonitoringRules(
                        5,
                        30,
                        1.0,
                        -1,
                        MonitoringRules.ErrorPersistenceBehavior.DEDUPLICATED,
                        false));

        assertEquals(
                OperationMessages.EVENT_DEDUPLICATION_WINDOW_SECONDS_MUST_NOT_BE_NEGATIVE,
                exception.getMessage());
    }

    @Test
    void constructorFailsForNullErrorPersistenceBehavior() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new MonitoringRules(
                        5,
                        30,
                        1.0,
                        60,
                        null,
                        false));

        assertEquals(
                OperationMessages.ERROR_PERSISTENCE_BEHAVIOR_MUST_NOT_BE_NULL,
                exception.getMessage());
    }

    @Test
    void parseErrorPersistenceBehaviorParsesDeduplicated() {
        MonitoringRules.ErrorPersistenceBehavior behavior = MonitoringRules
                .parseErrorPersistenceBehavior("DEDUPLICATED");

        assertEquals(MonitoringRules.ErrorPersistenceBehavior.DEDUPLICATED, behavior);
    }

    @Test
    void parseErrorPersistenceBehaviorParsesAlwaysCaseInsensitive() {
        MonitoringRules.ErrorPersistenceBehavior behavior = MonitoringRules
                .parseErrorPersistenceBehavior("always");

        assertEquals(MonitoringRules.ErrorPersistenceBehavior.ALWAYS, behavior);
    }

    @Test
    void parseErrorPersistenceBehaviorFailsForBlankValue() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> MonitoringRules.parseErrorPersistenceBehavior(" "));

        assertEquals(
                OperationMessages.ERROR_PERSISTENCE_BEHAVIOR_MUST_NOT_BE_NULL,
                exception.getMessage());
    }

    @Test
    void parseErrorPersistenceBehaviorFailsForInvalidValue() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> MonitoringRules.parseErrorPersistenceBehavior("INVALID"));

        assertEquals(
                OperationMessages.invalidErrorPersistenceBehavior("INVALID"),
                exception.getMessage());
    }
}