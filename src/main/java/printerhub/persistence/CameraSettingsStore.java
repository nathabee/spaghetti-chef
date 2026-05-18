package printerhub.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

import printerhub.camera.CameraSourceType;

public final class CameraSettingsStore {

    public Optional<CameraSettings> findByPrinterId(String printerId) {
        String normalizedPrinterId = requirePrinterId(printerId);

        String sql = """
                SELECT
                    printer_id,
                    enabled,
                    source_type,
                    source_value,
                    capture_interval_seconds,
                    retention_snapshot_count,
                    analysis_enabled,
                    safety_enabled,
                    pause_on_confirmed_spaghetti,
                    confidence_threshold,
                    confirmations_required,
                    updated_at
                FROM camera_settings
                WHERE printer_id = ?;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, normalizedPrinterId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapSettings(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load camera settings", exception);
        }
    }

    public CameraSettings loadOrDefault(String printerId) {
        String normalizedPrinterId = requirePrinterId(printerId);
        return findByPrinterId(normalizedPrinterId)
                .orElseGet(() -> CameraSettings.disabled(normalizedPrinterId, Instant.now()));
    }

    public CameraSettings save(CameraSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("camera settings must not be null");
        }

        String sql = """
                INSERT INTO camera_settings (
                    printer_id,
                    enabled,
                    source_type,
                    source_value,
                    capture_interval_seconds,
                    retention_snapshot_count,
                    analysis_enabled,
                    safety_enabled,
                    pause_on_confirmed_spaghetti,
                    confidence_threshold,
                    confirmations_required,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(printer_id) DO UPDATE SET
                    enabled = excluded.enabled,
                    source_type = excluded.source_type,
                    source_value = excluded.source_value,
                    capture_interval_seconds = excluded.capture_interval_seconds,
                    retention_snapshot_count = excluded.retention_snapshot_count,
                    analysis_enabled = excluded.analysis_enabled,
                    safety_enabled = excluded.safety_enabled,
                    pause_on_confirmed_spaghetti = excluded.pause_on_confirmed_spaghetti,
                    confidence_threshold = excluded.confidence_threshold,
                    confirmations_required = excluded.confirmations_required,
                    updated_at = excluded.updated_at;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, settings.printerId());
            statement.setInt(2, settings.enabled() ? 1 : 0);
            statement.setString(3, settings.sourceType().wireValue());
            statement.setString(4, settings.sourceValue().orElse(null));
            statement.setInt(5, settings.captureIntervalSeconds());
            statement.setInt(6, settings.retentionSnapshotCount());
            statement.setInt(7, settings.analysisEnabled() ? 1 : 0);
            statement.setInt(8, settings.safetyEnabled() ? 1 : 0);
            statement.setInt(9, settings.pauseOnConfirmedSpaghetti() ? 1 : 0);
            statement.setDouble(10, settings.confidenceThreshold());
            statement.setInt(11, settings.confirmationsRequired());
            statement.setString(12, settings.updatedAt().toString());

            statement.executeUpdate();
            return settings;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save camera settings", exception);
        }
    }

    private CameraSettings mapSettings(ResultSet resultSet) throws SQLException {
        return new CameraSettings(
                resultSet.getString("printer_id"),
                resultSet.getInt("enabled") == 1,
                CameraSourceType.fromWireValue(resultSet.getString("source_type")),
                resultSet.getString("source_value"),
                resultSet.getInt("capture_interval_seconds"),
                resultSet.getInt("retention_snapshot_count"),
                resultSet.getInt("analysis_enabled") == 1,
                resultSet.getInt("safety_enabled") == 1,
                resultSet.getInt("pause_on_confirmed_spaghetti") == 1,
                resultSet.getDouble("confidence_threshold"),
                resultSet.getInt("confirmations_required"),
                parseInstant(resultSet.getString("updated_at"))
        );
    }

    private static Instant parseInstant(String storedTimestamp) {
        if (storedTimestamp == null || storedTimestamp.isBlank()) {
            throw new IllegalStateException("Invalid stored camera settings timestamp");
        }

        try {
            return Instant.parse(storedTimestamp);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Invalid stored camera settings timestamp: " + storedTimestamp,
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