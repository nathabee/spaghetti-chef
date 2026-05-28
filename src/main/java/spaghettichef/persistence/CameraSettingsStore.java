package spaghettichef.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

import spaghettichef.camera.CameraSourceType;
import spaghettichef.config.RuntimeDefaults;

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
                    ffmpeg_command,
                    ffmpeg_input_format,
                    ffmpeg_video_size,
                    ffmpeg_timeout_ms,
                    ffmpeg_jpeg_quality,
                    storage_directory,
                    diagnostic_logging_enabled,
                    purge_automatically,
                    purge_retention_frequency,
                    capture_crop_enabled,
                    capture_crop_x1_percent,
                    capture_crop_y1_percent,
                    capture_crop_x2_percent,
                    capture_crop_y2_percent,
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
                    ffmpeg_command,
                    ffmpeg_input_format,
                    ffmpeg_video_size,
                    ffmpeg_timeout_ms,
                    ffmpeg_jpeg_quality,
                    storage_directory,
                    diagnostic_logging_enabled,
                    purge_automatically,
                    purge_retention_frequency,
                    capture_crop_enabled,
                    capture_crop_x1_percent,
                    capture_crop_y1_percent,
                    capture_crop_x2_percent,
                    capture_crop_y2_percent,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                    ffmpeg_command = excluded.ffmpeg_command,
                    ffmpeg_input_format = excluded.ffmpeg_input_format,
                    ffmpeg_video_size = excluded.ffmpeg_video_size,
                    ffmpeg_timeout_ms = excluded.ffmpeg_timeout_ms,
                    ffmpeg_jpeg_quality = excluded.ffmpeg_jpeg_quality,
                    storage_directory = excluded.storage_directory,
                    diagnostic_logging_enabled = excluded.diagnostic_logging_enabled,
                    purge_automatically = excluded.purge_automatically,
                    purge_retention_frequency = excluded.purge_retention_frequency,
                    capture_crop_enabled = excluded.capture_crop_enabled,
                    capture_crop_x1_percent = excluded.capture_crop_x1_percent,
                    capture_crop_y1_percent = excluded.capture_crop_y1_percent,
                    capture_crop_x2_percent = excluded.capture_crop_x2_percent,
                    capture_crop_y2_percent = excluded.capture_crop_y2_percent,
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
            statement.setString(12, settings.ffmpegCommand());
            statement.setString(13, settings.ffmpegInputFormat().orElse(null));
            statement.setString(14, settings.ffmpegVideoSize().orElse(null));
            statement.setInt(15, settings.ffmpegTimeoutMs());
            statement.setInt(16, settings.ffmpegJpegQuality());
            statement.setString(17, settings.storageDirectory());
            statement.setInt(18, settings.diagnosticLoggingEnabled() ? 1 : 0);
            statement.setInt(19, settings.purgeAutomatically() ? 1 : 0);
            statement.setInt(20, settings.purgeRetentionFrequency());
            statement.setInt(21, settings.captureCropEnabled() ? 1 : 0);
            statement.setInt(22, settings.captureCropX1Percent());
            statement.setInt(23, settings.captureCropY1Percent());
            statement.setInt(24, settings.captureCropX2Percent());
            statement.setInt(25, settings.captureCropY2Percent());
            statement.setString(26, settings.updatedAt().toString());

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
                readStringOrDefault(resultSet, "ffmpeg_command", RuntimeDefaults.DEFAULT_CAMERA_FFMPEG_COMMAND),
                resultSet.getString("ffmpeg_input_format"),
                resultSet.getString("ffmpeg_video_size"),
                readIntOrDefault(resultSet, "ffmpeg_timeout_ms", RuntimeDefaults.DEFAULT_CAMERA_FFMPEG_TIMEOUT_MS),
                readIntOrDefault(resultSet, "ffmpeg_jpeg_quality", RuntimeDefaults.DEFAULT_CAMERA_FFMPEG_JPEG_QUALITY),
                readStringOrDefault(
                        resultSet,
                        "storage_directory",
                        RuntimeDefaults.DEFAULT_CAMERA_STORAGE_DIRECTORY),
                resultSet.getInt("diagnostic_logging_enabled") == 1,
                resultSet.getInt("purge_automatically") == 1,
                readIntOrDefault(
                        resultSet,
                        "purge_retention_frequency",
                        CameraSettings.DEFAULT_PURGE_RETENTION_FREQUENCY),
                readBooleanOrDefault(
                        resultSet,
                        "capture_crop_enabled",
                        CameraSettings.DEFAULT_CAPTURE_CROP_ENABLED),
                readIntOrDefault(
                        resultSet,
                        "capture_crop_x1_percent",
                        CameraSettings.DEFAULT_CAPTURE_CROP_X1_PERCENT),
                readIntOrDefault(
                        resultSet,
                        "capture_crop_y1_percent",
                        CameraSettings.DEFAULT_CAPTURE_CROP_Y1_PERCENT),
                readIntOrDefault(
                        resultSet,
                        "capture_crop_x2_percent",
                        CameraSettings.DEFAULT_CAPTURE_CROP_X2_PERCENT),
                readIntOrDefault(
                        resultSet,
                        "capture_crop_y2_percent",
                        CameraSettings.DEFAULT_CAPTURE_CROP_Y2_PERCENT),
                parseInstant(resultSet.getString("updated_at"))
        );
    }

    private static boolean readBooleanOrDefault(ResultSet resultSet, String columnName, boolean fallback)
            throws SQLException {
        int value = resultSet.getInt(columnName);
        if (resultSet.wasNull()) {
            return fallback;
        }
        return value == 1;
    }

    private static String readStringOrDefault(ResultSet resultSet, String columnName, String fallback)
            throws SQLException {
        String value = resultSet.getString(columnName);
        if (resultSet.wasNull() || value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private static int readIntOrDefault(ResultSet resultSet, String columnName, int fallback) throws SQLException {
        int value = resultSet.getInt(columnName);
        if (resultSet.wasNull()) {
            return fallback;
        }
        return value;
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
