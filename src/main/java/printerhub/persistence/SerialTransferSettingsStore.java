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
                    sd_upload_min_batch_size,
                    sd_upload_batch_upgrade_step,
                    sd_upload_batch_downgrade_step,
                    sd_upload_stable_lines_for_upgrade,
                    sd_upload_resend_window_lines,
                    sd_upload_resend_threshold_for_downgrade,
                    sd_upload_recovery_threshold_for_min_batch,
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
                        resultSet.getInt("sd_upload_min_batch_size"),
                        resultSet.getInt("sd_upload_batch_upgrade_step"),
                        resultSet.getInt("sd_upload_batch_downgrade_step"),
                        resultSet.getInt("sd_upload_stable_lines_for_upgrade"),
                        resultSet.getInt("sd_upload_resend_window_lines"),
                        resultSet.getInt("sd_upload_resend_threshold_for_downgrade"),
                        resultSet.getInt("sd_upload_recovery_threshold_for_min_batch"),
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
                    sd_upload_min_batch_size,
                    sd_upload_batch_upgrade_step,
                    sd_upload_batch_downgrade_step,
                    sd_upload_stable_lines_for_upgrade,
                    sd_upload_resend_window_lines,
                    sd_upload_resend_threshold_for_downgrade,
                    sd_upload_recovery_threshold_for_min_batch,
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
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    sd_upload_batch_size = excluded.sd_upload_batch_size,
                    sd_upload_min_batch_size = excluded.sd_upload_min_batch_size,
                    sd_upload_batch_upgrade_step = excluded.sd_upload_batch_upgrade_step,
                    sd_upload_batch_downgrade_step = excluded.sd_upload_batch_downgrade_step,
                    sd_upload_stable_lines_for_upgrade = excluded.sd_upload_stable_lines_for_upgrade,
                    sd_upload_resend_window_lines = excluded.sd_upload_resend_window_lines,
                    sd_upload_resend_threshold_for_downgrade = excluded.sd_upload_resend_threshold_for_downgrade,
                    sd_upload_recovery_threshold_for_min_batch = excluded.sd_upload_recovery_threshold_for_min_batch,
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
            statement.setInt(3, settings.sdUploadMinBatchSize());
            statement.setInt(4, settings.sdUploadBatchUpgradeStep());
            statement.setInt(5, settings.sdUploadBatchDowngradeStep());
            statement.setInt(6, settings.sdUploadStableLinesForUpgrade());
            statement.setInt(7, settings.sdUploadResendWindowLines());
            statement.setInt(8, settings.sdUploadResendThresholdForDowngrade());
            statement.setInt(9, settings.sdUploadRecoveryThresholdForMinBatch());
            statement.setInt(10, settings.sdUploadRecoveryWindowMultiplier());
            statement.setInt(11, settings.sdUploadMaxErrors());
            statement.setInt(12, settings.sdUploadMaxConsecutiveIdenticalResends());
            statement.setInt(13, settings.sdUploadMinPerformancePercent());
            statement.setInt(14, settings.sdUploadMaxRetriesPerLine());
            statement.setInt(15, settings.fileStreamingReadTimeoutMs());
            statement.setInt(16, settings.fileStreamingQuietPeriodMs());
            statement.setInt(17, settings.fileStreamingReadActivitySleepMs());
            statement.setInt(18, settings.fileStreamingReadIdleSleepMs());
            statement.setInt(19, settings.fileStreamingRecoveryReplayDelayMs());
            statement.setString(20, Instant.now().toString());
            statement.executeUpdate();
            return settings;
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    OperationMessages.FAILED_TO_SAVE_SERIAL_TRANSFER_SETTINGS,
                    exception);
        }
    }
}