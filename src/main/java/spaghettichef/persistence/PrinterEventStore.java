package spaghettichef.persistence;

import spaghettichef.OperationMessages;
import spaghettichef.config.RuntimeDefaults;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class PrinterEventStore {

    public PrinterEvent save(PrinterEvent event) {
        if (event == null) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_EVENT_MUST_NOT_BE_NULL);
        }

        String sql = """
                INSERT INTO printer_events (
                    printer_id,
                    job_id,
                    event_type,
                    message,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?);
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, event.printerId());
            statement.setString(2, event.jobId());
            statement.setString(3, event.eventType());
            statement.setString(4, event.message());
            statement.setString(5, event.createdAt().toString());

            statement.executeUpdate();
            return event;
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_SAVE_PRINTER_EVENT, exception);
        }
    }

    public PrinterEvent record(
            String printerId,
            String jobId,
            String eventType,
            String message
    ) {
        return save(PrinterEvent.create(
                printerId,
                jobId,
                eventType,
                message
        ));
    }

    public List<PrinterEvent> findRecentByPrinterId(String printerId, int limit) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_ID_MUST_NOT_BE_BLANK);
        }

        int safeLimit = limit <= 0
                ? RuntimeDefaults.DEFAULT_RECENT_SNAPSHOT_LIMIT
                : limit;

        String sql = """
                SELECT
                    id,
                    printer_id,
                    job_id,
                    event_type,
                    message,
                    created_at
                FROM printer_events
                WHERE printer_id = ?
                ORDER BY id DESC
                LIMIT ?;
                """;

        List<PrinterEvent> events = new ArrayList<>();

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, printerId.trim());
            statement.setInt(2, safeLimit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    events.add(mapEvent(resultSet));
                }
            }

            return events;
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_LOAD_PRINTER_EVENTS, exception);
        }
    }

    public List<PrinterEvent> findRecentByJobId(String jobId, int limit) {
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.JOB_ID_MUST_NOT_BE_BLANK);
        }

        int safeLimit = limit <= 0
                ? RuntimeDefaults.DEFAULT_RECENT_JOB_LIMIT
                : limit;

        String sql = """
                SELECT
                    id,
                    printer_id,
                    job_id,
                    event_type,
                    message,
                    created_at
                FROM printer_events
                WHERE job_id = ?
                ORDER BY id DESC
                LIMIT ?;
                """;

        List<PrinterEvent> events = new ArrayList<>();

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, jobId.trim());
            statement.setInt(2, safeLimit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    events.add(mapEvent(resultSet));
                }
            }

            return events;
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_LOAD_PRINTER_EVENTS, exception);
        }
    }

    private PrinterEvent mapEvent(ResultSet resultSet) throws SQLException {
        return new PrinterEvent(
                resultSet.getLong("id"),
                resultSet.getString("printer_id"),
                resultSet.getString("job_id"),
                resultSet.getString("event_type"),
                resultSet.getString("message"),
                Instant.parse(resultSet.getString("created_at"))
        );
    }
}