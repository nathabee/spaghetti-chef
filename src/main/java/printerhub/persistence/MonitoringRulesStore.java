package printerhub.persistence;

import printerhub.OperationMessages;

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
                    debug_wire_tracing_enabled
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

                return new MonitoringRules(
                        resultSet.getLong("poll_interval_seconds"),
                        resultSet.getLong("min_interval_seconds"),
                        resultSet.getDouble("temperature_threshold"),
                        resultSet.getLong("event_dedup_window_seconds"),
                        MonitoringRules.parseErrorPersistenceBehavior(
                                resultSet.getString("error_persistence_behavior")),
                        resultSet.getInt("debug_wire_tracing_enabled") == 1);
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
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT(id) DO UPDATE SET
                    poll_interval_seconds = excluded.poll_interval_seconds,
                    min_interval_seconds = excluded.min_interval_seconds,
                    temperature_threshold = excluded.temperature_threshold,
                    event_dedup_window_seconds = excluded.event_dedup_window_seconds,
                    error_persistence_behavior = excluded.error_persistence_behavior,
                    debug_wire_tracing_enabled = excluded.debug_wire_tracing_enabled,
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

            statement.executeUpdate();
            return monitoringRules;
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    OperationMessages.FAILED_TO_SAVE_MONITORING_RULES,
                    exception);
        }
    }
}