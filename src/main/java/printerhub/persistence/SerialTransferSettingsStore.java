package printerhub.persistence;

import printerhub.OperationMessages;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

public final class SerialTransferSettingsStore {

    private static final String SETTINGS_ID = "default";

    public SerialTransferSettings load() {
        String sql = """
                SELECT
                    sd_upload_batch_size,
                    sd_upload_recovery_window_multiplier,
                    sd_upload_max_errors,
                    sd_upload_max_consecutive_identical_resends,
                    sd_upload_min_performance_percent,
                    sd_upload_max_retries_per_line,
                    file_streaming_read_timeout_ms,
                    file_streaming_quiet_period_ms,
                    file_streaming_read_activity_sleep_ms,
                    file_streaming_read_idle_sleep_ms,
                    file_streaming_recovery_replay_delay_ms
                FROM serial_transfer_settings
                WHERE id = ?
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, SETTINGS_ID);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return SerialTransferSettings.defaults();
                }

                return new SerialTransferSettings(
                        resultSet.getInt("sd_upload_batch_size"),
                        resultSet.getInt("sd_upload_recovery_window_multiplier"),
                        resultSet.getInt("sd_upload_max_errors"),
                        resultSet.getInt("sd_upload_max_consecutive_identical_resends"),
                        resultSet.getInt("sd_upload_min_performance_percent"),
                        resultSet.getInt("sd_upload_max_retries_per_line"),
                        resultSet.getInt("file_streaming_read_timeout_ms"),
                        resultSet.getInt("file_streaming_quiet_period_ms"),
                        resultSet.getInt("file_streaming_read_activity_sleep_ms"),
                        resultSet.getInt("file_streaming_read_idle_sleep_ms"),
                        resultSet.getInt("file_streaming_recovery_replay_delay_ms"));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    OperationMessages.FAILED_TO_LOAD_SERIAL_TRANSFER_SETTINGS,
                    exception);
        }
    }

    public SerialTransferSettings save(SerialTransferSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException(OperationMessages.SERIAL_TRANSFER_SETTINGS_MUST_NOT_BE_NULL);
        }

        String sql = """
                INSERT INTO serial_transfer_settings (
                    id,
                    sd_upload_batch_size,
                    sd_upload_recovery_window_multiplier,
                    sd_upload_max_errors,
                    sd_upload_max_consecutive_identical_resends,
                    sd_upload_min_performance_percent,
                    sd_upload_max_retries_per_line,
                    file_streaming_read_timeout_ms,
                    file_streaming_quiet_period_ms,
                    file_streaming_read_activity_sleep_ms,
                    file_streaming_read_idle_sleep_ms,
                    file_streaming_recovery_replay_delay_ms,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    sd_upload_batch_size = excluded.sd_upload_batch_size,
                    sd_upload_recovery_window_multiplier = excluded.sd_upload_recovery_window_multiplier,
                    sd_upload_max_errors = excluded.sd_upload_max_errors,
                    sd_upload_max_consecutive_identical_resends = excluded.sd_upload_max_consecutive_identical_resends,
                    sd_upload_min_performance_percent = excluded.sd_upload_min_performance_percent,
                    sd_upload_max_retries_per_line = excluded.sd_upload_max_retries_per_line,
                    file_streaming_read_timeout_ms = excluded.file_streaming_read_timeout_ms,
                    file_streaming_quiet_period_ms = excluded.file_streaming_quiet_period_ms,
                    file_streaming_read_activity_sleep_ms = excluded.file_streaming_read_activity_sleep_ms,
                    file_streaming_read_idle_sleep_ms = excluded.file_streaming_read_idle_sleep_ms,
                    file_streaming_recovery_replay_delay_ms = excluded.file_streaming_recovery_replay_delay_ms,
                    updated_at = excluded.updated_at
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, SETTINGS_ID);
            statement.setInt(2, settings.sdUploadBatchSize());
            statement.setInt(3, settings.sdUploadRecoveryWindowMultiplier());
            statement.setInt(4, settings.sdUploadMaxErrors());
            statement.setInt(5, settings.sdUploadMaxConsecutiveIdenticalResends());
            statement.setInt(6, settings.sdUploadMinPerformancePercent());
            statement.setInt(7, settings.sdUploadMaxRetriesPerLine());
            statement.setInt(8, settings.fileStreamingReadTimeoutMs());
            statement.setInt(9, settings.fileStreamingQuietPeriodMs());
            statement.setInt(10, settings.fileStreamingReadActivitySleepMs());
            statement.setInt(11, settings.fileStreamingReadIdleSleepMs());
            statement.setInt(12, settings.fileStreamingRecoveryReplayDelayMs());
            statement.setString(13, Instant.now().toString());
            statement.executeUpdate();
            return settings;
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    OperationMessages.FAILED_TO_SAVE_SERIAL_TRANSFER_SETTINGS,
                    exception);
        }
    }
}
