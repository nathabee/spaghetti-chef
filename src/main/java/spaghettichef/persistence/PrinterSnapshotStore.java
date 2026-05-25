package spaghettichef.persistence;

import spaghettichef.OperationMessages;
import spaghettichef.PrinterSnapshot;
import spaghettichef.PrinterState;
import spaghettichef.config.RuntimeDefaults;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class PrinterSnapshotStore {

    private final MonitoringRules monitoringRules;

    public PrinterSnapshotStore() {
        this(MonitoringRules.defaults());
    }

    public PrinterSnapshotStore(MonitoringRules monitoringRules) {
        if (monitoringRules == null) {
            throw new IllegalArgumentException(OperationMessages.MONITORING_RULES_MUST_NOT_BE_NULL);
        }

        this.monitoringRules = monitoringRules;
    }

    public void save(String printerId, PrinterSnapshot snapshot) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_ID_MUST_NOT_BE_BLANK);
        }
        if (snapshot == null) {
            throw new IllegalArgumentException(OperationMessages.SNAPSHOT_MUST_NOT_BE_NULL);
        }

        String normalizedPrinterId = printerId.trim();

        if (!shouldStoreSnapshot(normalizedPrinterId, snapshot)) {
            return;
        }

        String sql = """
                INSERT INTO printer_snapshots (
                    printer_id,
                    state,
                    hotend_temperature,
                    bed_temperature,
                    last_response,
                    error_message,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?);
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, normalizedPrinterId);
            statement.setString(2, snapshot.state().name());
            statement.setObject(3, snapshot.hotendTemperature());
            statement.setObject(4, snapshot.bedTemperature());
            statement.setString(5, snapshot.lastResponse());
            statement.setString(6, snapshot.errorMessage());
            statement.setString(7, snapshot.updatedAt().toString());

            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_SAVE_PRINTER_SNAPSHOT, exception);
        }
    }

    public List<PrinterSnapshot> findRecentByPrinterId(String printerId, int limit) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_ID_MUST_NOT_BE_BLANK);
        }

        int safeLimit = limit <= 0
                ? RuntimeDefaults.DEFAULT_RECENT_SNAPSHOT_LIMIT
                : limit;

        String sql = """
                SELECT
                    state,
                    hotend_temperature,
                    bed_temperature,
                    last_response,
                    error_message,
                    created_at
                FROM printer_snapshots
                WHERE printer_id = ?
                ORDER BY id DESC
                LIMIT ?;
                """;

        List<PrinterSnapshot> snapshots = new ArrayList<>();

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, printerId.trim());
            statement.setInt(2, safeLimit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    snapshots.add(toSnapshot(resultSet));
                }
            }

            return snapshots;
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_LOAD_PRINTER_SNAPSHOTS, exception);
        }
    }

    private boolean shouldStoreSnapshot(String printerId, PrinterSnapshot snapshot) {
        String sql = """
                SELECT
                    state,
                    hotend_temperature,
                    bed_temperature,
                    created_at
                FROM printer_snapshots
                WHERE printer_id = ?
                ORDER BY id DESC
                LIMIT 1;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, printerId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return true;
                }

                PrinterState lastState = parseStoredState(resultSet.getString("state"));
                Instant lastCreatedAt = parseStoredInstant(resultSet.getString("created_at"));

                if (lastState != snapshot.state()) {
                    return true;
                }

                if (temperatureDifferenceExceeded(
                        resultSet,
                        snapshot,
                        monitoringRules.temperatureDeltaThreshold()
                )) {
                    return true;
                }

                long secondsSinceLastSnapshot = Duration.between(
                        lastCreatedAt,
                        snapshot.updatedAt()
                ).toSeconds();

                return secondsSinceLastSnapshot >= monitoringRules.snapshotMinimumIntervalSeconds();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_CHECK_LATEST_PRINTER_SNAPSHOT, exception);
        }
    }

    private boolean temperatureDifferenceExceeded(
            ResultSet resultSet,
            PrinterSnapshot snapshot,
            double threshold
    ) throws SQLException {
        if (threshold <= 0) {
            return false;
        }

        Double lastHotend = readNullableDouble(resultSet, "hotend_temperature");
        Double lastBed = readNullableDouble(resultSet, "bed_temperature");

        return differenceExceeded(lastHotend, snapshot.hotendTemperature(), threshold)
                || differenceExceeded(lastBed, snapshot.bedTemperature(), threshold);
    }

    private Double readNullableDouble(ResultSet resultSet, String columnName) throws SQLException {
        double value = resultSet.getDouble(columnName);

        if (resultSet.wasNull()) {
            return null;
        }

        return value;
    }

    private boolean differenceExceeded(Double previous, Double current, double threshold) {
        if (previous == null || current == null) {
            return false;
        }

        return Math.abs(current - previous) >= threshold;
    }

    private PrinterSnapshot toSnapshot(ResultSet resultSet) throws SQLException {
        PrinterState state = parseStoredState(resultSet.getString("state"));
        Double hotendTemperature = readNullableDouble(resultSet, "hotend_temperature");
        Double bedTemperature = readNullableDouble(resultSet, "bed_temperature");
        String lastResponse = resultSet.getString("last_response");
        String errorMessage = resultSet.getString("error_message");
        Instant createdAt = parseStoredInstant(resultSet.getString("created_at"));

        if (state == PrinterState.ERROR || errorMessage != null) {
            return PrinterSnapshot.error(
                    state,
                    hotendTemperature,
                    bedTemperature,
                    lastResponse,
                    errorMessage,
                    createdAt
            );
        }

        return PrinterSnapshot.fromResponse(
                state,
                hotendTemperature,
                bedTemperature,
                lastResponse,
                createdAt
        );
    }

    private PrinterState parseStoredState(String storedState) {
        if (storedState == null || storedState.isBlank()) {
            throw new IllegalStateException(OperationMessages.INVALID_STORED_PRINTER_SNAPSHOT_STATE);
        }

        try {
            return PrinterState.valueOf(storedState);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    OperationMessages.invalidStoredPrinterSnapshotState(storedState),
                    exception
            );
        }
    }

    private Instant parseStoredInstant(String storedTimestamp) {
        if (storedTimestamp == null || storedTimestamp.isBlank()) {
            throw new IllegalStateException(OperationMessages.INVALID_STORED_PRINTER_SNAPSHOT_TIMESTAMP);
        }

        try {
            return Instant.parse(storedTimestamp);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    OperationMessages.invalidStoredPrinterSnapshotTimestamp(storedTimestamp),
                    exception
            );
        }
    }
}