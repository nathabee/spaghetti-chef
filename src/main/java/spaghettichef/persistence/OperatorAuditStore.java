package spaghettichef.persistence;

import spaghettichef.config.RuntimeDefaults;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class OperatorAuditStore {
    public OperatorAuditEvent save(OperatorAuditEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("operatorAuditEvent must not be null");
        }

        String sql = """
                INSERT INTO operator_audit_events (
                    actor,
                    role,
                    permission,
                    dangerous_action,
                    action_type,
                    target_type,
                    target_id,
                    result,
                    failure_reason,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, event.actor());
            statement.setString(2, event.role());
            statement.setString(3, event.permission());
            statement.setString(4, event.dangerousAction());
            statement.setString(5, event.actionType());
            statement.setString(6, event.targetType());
            statement.setString(7, event.targetId());
            statement.setString(8, event.result());
            statement.setString(9, event.failureReason());
            statement.setString(10, event.createdAt().toString());
            statement.executeUpdate();
            return event;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save operator audit event", exception);
        }
    }

    public List<OperatorAuditEvent> findRecent(int limit) {
        int safeLimit = limit <= 0 ? RuntimeDefaults.DEFAULT_RECENT_JOB_LIMIT : limit;
        String sql = """
                SELECT
                    id,
                    actor,
                    role,
                    permission,
                    dangerous_action,
                    action_type,
                    target_type,
                    target_id,
                    result,
                    failure_reason,
                    created_at
                FROM operator_audit_events
                ORDER BY id DESC
                LIMIT ?;
                """;

        List<OperatorAuditEvent> events = new ArrayList<>();

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setInt(1, safeLimit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    events.add(mapEvent(resultSet));
                }
            }

            return events;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load operator audit events", exception);
        }
    }

    private OperatorAuditEvent mapEvent(ResultSet resultSet) throws SQLException {
        return new OperatorAuditEvent(
                resultSet.getLong("id"),
                resultSet.getString("actor"),
                resultSet.getString("role"),
                resultSet.getString("permission"),
                resultSet.getString("dangerous_action"),
                resultSet.getString("action_type"),
                resultSet.getString("target_type"),
                resultSet.getString("target_id"),
                resultSet.getString("result"),
                resultSet.getString("failure_reason"),
                Instant.parse(resultSet.getString("created_at"))
        );
    }
}
