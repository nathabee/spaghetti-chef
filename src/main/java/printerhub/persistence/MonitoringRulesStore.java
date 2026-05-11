package printerhub.persistence;

import printerhub.OperationMessages;
import printerhub.config.RuntimeDefaults;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class MonitoringRulesStore {

    private static final String DEFAULT_RULES_ID = "default";

    public MonitoringRules load() {
        String sql = """
                SELECT
                    poll_interval_seconds,
                    min_interval_seconds,
                    temperature_threshold,
                    event_dedup_window_seconds,
                    error_persistence_behavior,
                    debug_wire_tracing_enabled,
                    sd_upload_batch_size,
                    sd_upload_recovery_window_multiplier,
                    sd_upload_max_errors,
                    sd_upload_max_consecutive_identical_resends,
                    sd_upload_min_performance_percent
                FROM monitoring_rules
                WHERE id = ?;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, DEFAULT_RULES_ID);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return MonitoringRules.defaults();
                }

                int batchSize;
                try {
                    batchSize = resultSet.getInt("sd_upload_batch_size");
                    if (resultSet.wasNull() || batchSize < 1) {
                        batchSize = RuntimeDefaults.DEFAULT_SD_UPLOAD_BATCH_SIZE;
                    }
                } catch (SQLException ignored) {
                    batchSize = RuntimeDefaults.DEFAULT_SD_UPLOAD_BATCH_SIZE;
                }

                int recoveryWindowMultiplier;
                try {
                    recoveryWindowMultiplier = resultSet.getInt("sd_upload_recovery_window_multiplier");
                    if (resultSet.wasNull() || recoveryWindowMultiplier < 1) {
                        recoveryWindowMultiplier = RuntimeDefaults.DEFAULT_SD_UPLOAD_RECOVERY_WINDOW_MULTIPLIER;
                    }
                } catch (SQLException ignored) {
                    recoveryWindowMultiplier = RuntimeDefaults.DEFAULT_SD_UPLOAD_RECOVERY_WINDOW_MULTIPLIER;
                }

                int maxErrors;
                try {
                    maxErrors = resultSet.getInt("sd_upload_max_errors");
                    if (resultSet.wasNull() || maxErrors < 1) {
                        maxErrors = RuntimeDefaults.DEFAULT_SD_UPLOAD_MAX_ERRORS;
                    }
                } catch (SQLException ignored) {
                    maxErrors = RuntimeDefaults.DEFAULT_SD_UPLOAD_MAX_ERRORS;
                }

                int maxConsecutiveIdenticalResends;
                try {
                    maxConsecutiveIdenticalResends = resultSet.getInt("sd_upload_max_consecutive_identical_resends");
                    if (resultSet.wasNull() || maxConsecutiveIdenticalResends < 1) {
                        maxConsecutiveIdenticalResends = RuntimeDefaults.DEFAULT_SD_UPLOAD_MAX_CONSECUTIVE_IDENTICAL_RESENDS;
                    }
                } catch (SQLException ignored) {
                    maxConsecutiveIdenticalResends = RuntimeDefaults.DEFAULT_SD_UPLOAD_MAX_CONSECUTIVE_IDENTICAL_RESENDS;
                }

                int minPerformancePercent;
                try {
                    minPerformancePercent = resultSet.getInt("sd_upload_min_performance_percent");
                    if (resultSet.wasNull() || minPerformancePercent < 0 || minPerformancePercent > 100) {
                        minPerformancePercent = RuntimeDefaults.DEFAULT_SD_UPLOAD_MIN_PERFORMANCE_PERCENT;
                    }
                } catch (SQLException ignored) {
                    minPerformancePercent = RuntimeDefaults.DEFAULT_SD_UPLOAD_MIN_PERFORMANCE_PERCENT;
                }

                return new MonitoringRules(
                        resultSet.getLong("poll_interval_seconds"),
                        resultSet.getLong("min_interval_seconds"),
                        resultSet.getDouble("temperature_threshold"),
                        resultSet.getLong("event_dedup_window_seconds"),
                        MonitoringRules.parseErrorPersistenceBehavior(
                                resultSet.getString("error_persistence_behavior")),
                        resultSet.getInt("debug_wire_tracing_enabled") == 1,
                        batchSize,
                        recoveryWindowMultiplier,
                        maxErrors,
                        maxConsecutiveIdenticalResends,
                        minPerformancePercent);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    OperationMessages.FAILED_TO_LOAD_MONITORING_RULES,
                    exception);
        }
    }

    public MonitoringRules save(MonitoringRules monitoringRules) {
        if (monitoringRules == null) {
            throw new IllegalArgumentException(
                    OperationMessages.MONITORING_RULES_MUST_NOT_BE_NULL);
        }

        String sql = """
                INSERT INTO monitoring_rules (
                    id,
                    poll_interval_seconds,
                    min_interval_seconds,
                    temperature_threshold,
                    event_dedup_window_seconds,
                    error_persistence_behavior,
                    debug_wire_tracing_enabled,
                    sd_upload_batch_size,
                    sd_upload_recovery_window_multiplier,
                    sd_upload_max_errors,
                    sd_upload_max_consecutive_identical_resends,
                    sd_upload_min_performance_percent,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT(id) DO UPDATE SET
                    poll_interval_seconds = excluded.poll_interval_seconds,
                    min_interval_seconds = excluded.min_interval_seconds,
                    temperature_threshold = excluded.temperature_threshold,
                    event_dedup_window_seconds = excluded.event_dedup_window_seconds,
                    error_persistence_behavior = excluded.error_persistence_behavior,
                    debug_wire_tracing_enabled = excluded.debug_wire_tracing_enabled,
                    sd_upload_batch_size = excluded.sd_upload_batch_size,
                    sd_upload_recovery_window_multiplier = excluded.sd_upload_recovery_window_multiplier,
                    sd_upload_max_errors = excluded.sd_upload_max_errors,
                    sd_upload_max_consecutive_identical_resends = excluded.sd_upload_max_consecutive_identical_resends,
                    sd_upload_min_performance_percent = excluded.sd_upload_min_performance_percent,
                    updated_at = CURRENT_TIMESTAMP;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, DEFAULT_RULES_ID);
            statement.setLong(2, monitoringRules.pollIntervalSeconds());
            statement.setLong(3, monitoringRules.snapshotMinimumIntervalSeconds());
            statement.setDouble(4, monitoringRules.temperatureDeltaThreshold());
            statement.setLong(5, monitoringRules.eventDeduplicationWindowSeconds());
            statement.setString(6, monitoringRules.errorPersistenceBehavior().name());
            statement.setInt(7, monitoringRules.debugWireTracingEnabled() ? 1 : 0);
            statement.setInt(8, monitoringRules.sdUploadBatchSize());
            statement.setInt(9, monitoringRules.sdUploadRecoveryWindowMultiplier());
            statement.setInt(10, monitoringRules.sdUploadMaxErrors());
            statement.setInt(11, monitoringRules.sdUploadMaxConsecutiveIdenticalResends());
            statement.setInt(12, monitoringRules.sdUploadMinPerformancePercent());

            statement.executeUpdate();
            return monitoringRules;
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    OperationMessages.FAILED_TO_SAVE_MONITORING_RULES,
                    exception);
        }
    }
}