package printerhub.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import printerhub.config.RuntimeDefaults;

public final class CameraEventStore {

    public CameraEvent save(CameraEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("camera event must not be null");
        }

        String sql = """
                INSERT INTO camera_events (
                    printer_id,
                    event_type,
                    message,
                    confidence,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?);
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, event.printerId());
            statement.setString(2, event.eventType());
            statement.setString(3, event.message());

            if (event.confidence().isPresent()) {
                statement.setDouble(4, event.confidence().getAsDouble());
            } else {
                statement.setObject(4, null);
            }

            statement.setString(5, event.createdAt().toString());

            statement.executeUpdate();
            return event;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save camera event", exception);
        }
    }

    public CameraEvent record(
            String printerId,
            String eventType,
            String message
    ) {
        return save(CameraEvent.newEvent(
                printerId,
                eventType,
                message,
                null,
                Instant.now()
        ));
    }

    public CameraEvent record(
            String printerId,
            String eventType,
            String message,
            Double confidence
    ) {
        return save(CameraEvent.newEvent(
                printerId,
                eventType,
                message,
                confidence,
                Instant.now()
        ));
    }

    public List<CameraEvent> findRecentByPrinterId(String printerId, int limit) {
        String normalizedPrinterId = requirePrinterId(printerId);

        int safeLimit = limit <= 0
                ? RuntimeDefaults.DEFAULT_RECENT_SNAPSHOT_LIMIT
                : limit;

        String sql = """
                SELECT
                    id,
                    printer_id,
                    event_type,
                    message,
                    confidence,
                    created_at
                FROM camera_events
                WHERE printer_id = ?
                ORDER BY id DESC
                LIMIT ?;
                """;

        List<CameraEvent> events = new ArrayList<>();

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, normalizedPrinterId);
            statement.setInt(2, safeLimit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    events.add(mapEvent(resultSet));
                }
            }

            return events;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load camera events", exception);
        }
    }

    private CameraEvent mapEvent(ResultSet resultSet) throws SQLException {
        return new CameraEvent(
                resultSet.getLong("id"),
                resultSet.getString("printer_id"),
                resultSet.getString("event_type"),
                resultSet.getString("message"),
                readNullableDouble(resultSet, "confidence"),
                parseInstant(resultSet.getString("created_at"))
        );
    }

    private static Double readNullableDouble(ResultSet resultSet, String columnName) throws SQLException {
        double value = resultSet.getDouble(columnName);
        if (resultSet.wasNull()) {
            return null;
        }
        return value;
    }

    private static Instant parseInstant(String storedTimestamp) {
        if (storedTimestamp == null || storedTimestamp.isBlank()) {
            throw new IllegalStateException("Invalid stored camera event timestamp");
        }

        try {
            return Instant.parse(storedTimestamp);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Invalid stored camera event timestamp: " + storedTimestamp,
                    exception);
        }
    }

    private static String requirePrinterId(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException("printerId must not be blank");
        }
        return printerId.trim();
    }
}