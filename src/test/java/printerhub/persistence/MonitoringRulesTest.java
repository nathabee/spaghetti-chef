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
                                true,
                                5,
                                2,
                                100,
                                10,
                                5);

                assertEquals(12, rules.pollIntervalSeconds());
                assertEquals(45, rules.snapshotMinimumIntervalSeconds());
                assertEquals(2.5, rules.temperatureDeltaThreshold());
                assertEquals(90, rules.eventDeduplicationWindowSeconds());
                assertEquals(MonitoringRules.ErrorPersistenceBehavior.ALWAYS, rules.errorPersistenceBehavior());
                assertEquals(true, rules.debugWireTracingEnabled());
                assertEquals(5, rules.sdUploadBatchSize());
                assertEquals(2, rules.sdUploadRecoveryWindowMultiplier());
                assertEquals(100, rules.sdUploadMaxErrors());
                assertEquals(10, rules.sdUploadMaxConsecutiveIdenticalResends());
                assertEquals(5, rules.sdUploadMinPerformancePercent());
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
                                RuntimeDefaults.DEFAULT_SD_UPLOAD_BATCH_SIZE,
                                rules.sdUploadBatchSize());
                assertEquals(
                                RuntimeDefaults.DEFAULT_SD_UPLOAD_RECOVERY_WINDOW_MULTIPLIER,
                                rules.sdUploadRecoveryWindowMultiplier());
                assertEquals(
                                RuntimeDefaults.DEFAULT_SD_UPLOAD_MAX_ERRORS,
                                rules.sdUploadMaxErrors());
                assertEquals(
                                RuntimeDefaults.DEFAULT_SD_UPLOAD_MAX_CONSECUTIVE_IDENTICAL_RESENDS,
                                rules.sdUploadMaxConsecutiveIdenticalResends());
                assertEquals(
                                RuntimeDefaults.DEFAULT_SD_UPLOAD_MIN_PERFORMANCE_PERCENT,
                                rules.sdUploadMinPerformancePercent());
        }

        @Test
        void constructorAllowsZeroTemperatureDeltaThreshold() {
                MonitoringRules rules = new MonitoringRules(
                                5,
                                30,
                                0.0,
                                60,
                                MonitoringRules.ErrorPersistenceBehavior.DEDUPLICATED,
                                false,
                                1,
                                2,
                                100,
                                10,
                                5);

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
                                false,
                                1,
                                2,
                                100,
                                10,
                                5);

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
                                false,
                                1,
                                2,
                                100,
                                10,
                                5);

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
                                                false,
                                                1,
                                                2,
                                                100,
                                                10,
                                                5));

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
                                                false,
                                                1,
                                                2,
                                                100,
                                                10,
                                                5));

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
                                                false,
                                                1,
                                                2,
                                                100,
                                                10,
                                                5));

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
                                                false,
                                                1,
                                                2,
                                                100,
                                                10,
                                                5));

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
                                                false,
                                                1,
                                                2,
                                                100,
                                                10,
                                                5));

                assertEquals(
                                OperationMessages.ERROR_PERSISTENCE_BEHAVIOR_MUST_NOT_BE_NULL,
                                exception.getMessage());
        }

        @Test
        void constructorFailsForInvalidSdUploadBatchSize() {
                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> new MonitoringRules(
                                                5,
                                                30,
                                                1.0,
                                                60,
                                                MonitoringRules.ErrorPersistenceBehavior.DEDUPLICATED,
                                                false,
                                                0,
                                                2,
                                                100,
                                                10,
                                                5));

                assertEquals(
                                "sdUploadBatchSize must be between 1 and 100",
                                exception.getMessage());
        }

        @Test
        void constructorFailsForInvalidSdUploadRecoveryWindowMultiplier() {
                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> new MonitoringRules(
                                                5,
                                                30,
                                                1.0,
                                                60,
                                                MonitoringRules.ErrorPersistenceBehavior.DEDUPLICATED,
                                                false,
                                                5,
                                                0,
                                                100,
                                                10,
                                                5));

                assertEquals(
                                "sdUploadRecoveryWindowMultiplier must be between 1 and 100",
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

        @Test
        void constructorFailsForInvalidSdUploadMaxErrors() {
                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> new MonitoringRules(
                                                5,
                                                30,
                                                1.0,
                                                60,
                                                MonitoringRules.ErrorPersistenceBehavior.DEDUPLICATED,
                                                false,
                                                5,
                                                2,
                                                0,
                                                10,
                                                5));

                assertEquals(
                                "sdUploadMaxErrors must be between 1 and 1000000",
                                exception.getMessage());
        }

        @Test
        void constructorFailsForInvalidSdUploadMaxConsecutiveIdenticalResends() {
                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> new MonitoringRules(
                                                5,
                                                30,
                                                1.0,
                                                60,
                                                MonitoringRules.ErrorPersistenceBehavior.DEDUPLICATED,
                                                false,
                                                5,
                                                2,
                                                100,
                                                0,
                                                5));

                assertEquals(
                                "sdUploadMaxConsecutiveIdenticalResends must be between 1 and 1000",
                                exception.getMessage());
        }

        @Test
        void constructorFailsForInvalidSdUploadMinPerformancePercent() {
                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> new MonitoringRules(
                                                5,
                                                30,
                                                1.0,
                                                60,
                                                MonitoringRules.ErrorPersistenceBehavior.DEDUPLICATED,
                                                false,
                                                5,
                                                2,
                                                100,
                                                10,
                                                101));

                assertEquals(
                                "sdUploadMinPerformancePercent must be between 0 and 100",
                                exception.getMessage());
        }

}