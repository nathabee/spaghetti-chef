package printerhub.persistence;

import printerhub.OperationMessages;
import printerhub.config.RuntimeDefaults;
import printerhub.security.RoleProfile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseInitializer {

    public void initialize() {
        try (
                Connection connection = Database.getConnection();
                Statement statement = connection.createStatement()) {
            createPrintFilesTable(statement);
            createPrinterSdFilesTable(statement);
            createPrintJobsTable(statement);
            createPrintJobExecutionStepsTable(statement);
            createPrinterSnapshotsTable(statement);
            createPrinterEventsTable(statement);
            createOperatorAuditEventsTable(statement);
            createConfiguredPrintersTable(statement);
            createMonitoringRulesTable(statement);
            createPrintFileSettingsTable(statement);
            createSerialTransferSettingsTable(statement);
            createCameraSettingsTable(statement);
            createCameraEventsTable(statement);
            createCameraSnapshotMetadataTable(statement);
            createCameraAnalysisSessionsTable(statement);
            createCameraAnalysisSamplesTable(statement);
            createSecuritySettingsTable(statement);
            createRoleProfilesTable(statement);

            ensureColumn(connection, "print_jobs", "print_file_id", "TEXT");
            ensureColumn(connection, "print_jobs", "printer_sd_file_id", "TEXT");
            ensureColumn(connection, "printer_sd_files", "enabled", "INTEGER NOT NULL DEFAULT 1");
            ensureColumn(connection, "printer_sd_files", "deleted", "INTEGER NOT NULL DEFAULT 0");
            ensureColumn(connection, "printer_sd_files", "deleted_at", "TEXT");

            ensureColumn(connection, "monitoring_rules", "poll_interval_seconds", "INTEGER NOT NULL DEFAULT 5");
            ensureColumn(connection, "monitoring_rules", "event_dedup_window_seconds", "INTEGER NOT NULL DEFAULT 60");
            ensureColumn(connection, "monitoring_rules", "error_persistence_behavior",
                    "TEXT NOT NULL DEFAULT 'DEDUPLICATED'");
            ensureColumn(connection, "monitoring_rules", "debug_wire_tracing_enabled", "INTEGER NOT NULL DEFAULT 0");
            ensureColumn(connection, "monitoring_rules", "sd_upload_batch_size", "INTEGER NOT NULL DEFAULT 5");
            ensureColumn(connection, "monitoring_rules", "sd_upload_recovery_window_multiplier",
                    "INTEGER NOT NULL DEFAULT 2");
            ensureColumn(connection, "monitoring_rules", "sd_upload_max_errors", "INTEGER NOT NULL DEFAULT 100");
            ensureColumn(connection, "monitoring_rules", "sd_upload_max_consecutive_identical_resends",
                    "INTEGER NOT NULL DEFAULT 10");
            ensureColumn(connection, "monitoring_rules", "sd_upload_min_performance_percent",
                    "INTEGER NOT NULL DEFAULT 5");
            ensureColumn(connection, "serial_transfer_settings", "sd_upload_batch_size", "INTEGER NOT NULL DEFAULT 5");
            ensureColumn(connection, "serial_transfer_settings", "sd_upload_min_batch_size",
                    "INTEGER NOT NULL DEFAULT 1");
            ensureColumn(connection, "serial_transfer_settings", "sd_upload_batch_upgrade_step",
                    "INTEGER NOT NULL DEFAULT 1");
            ensureColumn(connection, "serial_transfer_settings", "sd_upload_batch_downgrade_step",
                    "INTEGER NOT NULL DEFAULT 1");
            ensureColumn(connection, "serial_transfer_settings", "sd_upload_stable_lines_for_upgrade",
                    "INTEGER NOT NULL DEFAULT 200");
            ensureColumn(connection, "serial_transfer_settings", "sd_upload_resend_window_lines",
                    "INTEGER NOT NULL DEFAULT 50");
            ensureColumn(connection, "serial_transfer_settings", "sd_upload_resend_threshold_for_downgrade",
                    "INTEGER NOT NULL DEFAULT 1");
            ensureColumn(connection, "serial_transfer_settings", "sd_upload_recovery_threshold_for_min_batch",
                    "INTEGER NOT NULL DEFAULT 3");
            ensureColumn(connection, "serial_transfer_settings", "sd_upload_recovery_window_multiplier",
                    "INTEGER NOT NULL DEFAULT 2");
            ensureColumn(connection, "serial_transfer_settings", "sd_upload_max_errors",
                    "INTEGER NOT NULL DEFAULT 100");
            ensureColumn(connection, "serial_transfer_settings", "sd_upload_max_consecutive_identical_resends",
                    "INTEGER NOT NULL DEFAULT 10");
            ensureColumn(connection, "serial_transfer_settings", "sd_upload_min_performance_percent",
                    "INTEGER NOT NULL DEFAULT 5");
            ensureColumn(connection, "serial_transfer_settings", "sd_upload_max_retries_per_line",
                    "INTEGER NOT NULL DEFAULT 3");
            ensureColumn(connection, "serial_transfer_settings", "file_streaming_read_timeout_ms",
                    "INTEGER NOT NULL DEFAULT 5000");
            ensureColumn(connection, "serial_transfer_settings", "file_streaming_quiet_period_ms",
                    "INTEGER NOT NULL DEFAULT 10");
            ensureColumn(connection, "serial_transfer_settings", "file_streaming_read_activity_sleep_ms",
                    "INTEGER NOT NULL DEFAULT 1");
            ensureColumn(connection, "serial_transfer_settings", "file_streaming_read_idle_sleep_ms",
                    "INTEGER NOT NULL DEFAULT 1");
            ensureColumn(connection, "serial_transfer_settings", "file_streaming_recovery_replay_delay_ms",
                    "INTEGER NOT NULL DEFAULT 15");
            ensureColumn(connection, "security_settings", "security_enabled", "INTEGER NOT NULL DEFAULT 0");
            ensureColumn(connection, "security_settings", "default_role", "TEXT NOT NULL DEFAULT 'ADMIN'");
            ensureColumn(connection, "security_settings", "require_dangerous_action_confirmation",
                    "INTEGER NOT NULL DEFAULT 1");
            ensureColumn(connection, "security_settings", "created_at", "TEXT");
            ensureColumn(connection, "security_settings", "updated_at", "TEXT");
            ensureColumn(connection, "role_profiles", "permissions_json", "TEXT NOT NULL DEFAULT '[]'");
            ensureColumn(connection, "role_profiles", "built_in", "INTEGER NOT NULL DEFAULT 1");
            ensureColumn(connection, "role_profiles", "created_at", "TEXT");
            ensureColumn(connection, "role_profiles", "updated_at", "TEXT");
            ensureColumn(connection, "camera_settings", "ffmpeg_command",
                    "TEXT NOT NULL DEFAULT '" + RuntimeDefaults.DEFAULT_CAMERA_FFMPEG_COMMAND + "'");
            ensureColumn(connection, "camera_settings", "ffmpeg_input_format", "TEXT");
            ensureColumn(connection, "camera_settings", "ffmpeg_video_size",
                    "TEXT DEFAULT '" + RuntimeDefaults.DEFAULT_CAMERA_FFMPEG_VIDEO_SIZE + "'");
            ensureColumn(connection, "camera_settings", "ffmpeg_timeout_ms",
                    "INTEGER NOT NULL DEFAULT " + RuntimeDefaults.DEFAULT_CAMERA_FFMPEG_TIMEOUT_MS);
            ensureColumn(connection, "camera_settings", "ffmpeg_jpeg_quality",
                    "INTEGER NOT NULL DEFAULT " + RuntimeDefaults.DEFAULT_CAMERA_FFMPEG_JPEG_QUALITY);

            ensureBuiltInRoleProfiles(connection);

            System.out.println(OperationMessages.databaseInitialized(DatabaseConfig.databaseFile()));
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_INITIALIZE_DATABASE_SCHEMA, exception);
        }
    }

    private void createPrintFilesTable(Statement statement) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS print_files (
                    id TEXT PRIMARY KEY,
                    original_filename TEXT NOT NULL,
                    path TEXT NOT NULL,
                    size_bytes INTEGER NOT NULL,
                    media_type TEXT NOT NULL,
                    created_at TEXT NOT NULL
                );
                """;

        statement.execute(sql);
    }

    private void createPrinterSdFilesTable(Statement statement) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS printer_sd_files (
                    id TEXT PRIMARY KEY,
                    printer_id TEXT NOT NULL,
                    firmware_path TEXT NOT NULL,
                    display_name TEXT NOT NULL,
                    size_bytes INTEGER,
                    raw_line TEXT,
                    print_file_id TEXT,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    deleted INTEGER NOT NULL DEFAULT 0,
                    deleted_at TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    UNIQUE(printer_id, firmware_path)
                );
                """;

        statement.execute(sql);
    }

    private void createPrintJobsTable(Statement statement) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS print_jobs (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    state TEXT NOT NULL,
                    printer_id TEXT,
                    print_file_id TEXT,
                    printer_sd_file_id TEXT,
                    target_temperature REAL,
                    fan_speed INTEGER,
                    failure_reason TEXT,
                    failure_detail TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    started_at TEXT,
                    finished_at TEXT
                );
                """;

        statement.execute(sql);
    }

    private void ensureColumn(
            Connection connection,
            String tableName,
            String columnName,
            String definition) throws SQLException {
        try (ResultSet resultSet = connection.getMetaData().getColumns(null, null, tableName, columnName)) {
            if (resultSet.next()) {
                return;
            }
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
        }
    }

    private void createPrintJobExecutionStepsTable(Statement statement) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS print_job_execution_steps (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    job_id TEXT NOT NULL,
                    step_index INTEGER NOT NULL,
                    step_name TEXT NOT NULL,
                    wire_command TEXT,
                    response TEXT,
                    outcome TEXT NOT NULL,
                    success INTEGER NOT NULL,
                    failure_reason TEXT,
                    failure_detail TEXT,
                    created_at TEXT NOT NULL
                );
                """;

        statement.execute(sql);
    }

    private void createPrinterSnapshotsTable(Statement statement) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS printer_snapshots (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    printer_id TEXT NOT NULL,
                    state TEXT NOT NULL,
                    hotend_temperature REAL,
                    bed_temperature REAL,
                    last_response TEXT,
                    error_message TEXT,
                    created_at TEXT NOT NULL
                );
                """;

        statement.execute(sql);
    }

    private void createPrinterEventsTable(Statement statement) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS printer_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    printer_id TEXT,
                    job_id TEXT,
                    event_type TEXT NOT NULL,
                    message TEXT,
                    created_at TEXT NOT NULL
                );
                """;

        statement.execute(sql);
    }

    private void createCameraAnalysisSessionsTable(Statement statement) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS camera_analysis_sessions (
                    id TEXT PRIMARY KEY,
                    printer_id TEXT NOT NULL,
                    state TEXT NOT NULL,
                    started_at TEXT,
                    stopped_at TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    message TEXT
                );
                """;

        statement.execute(sql);
    }

    private void createCameraAnalysisSamplesTable(Statement statement) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS camera_analysis_samples (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id TEXT NOT NULL,
                    printer_id TEXT NOT NULL,
                    captured_at TEXT NOT NULL,
                    analyzed_at TEXT NOT NULL,
                    latest_snapshot_path TEXT,
                    previous_snapshot_path TEXT,
                    delta_snapshot_path TEXT,
                    delta_score REAL NOT NULL,
                    changed_pixel_ratio REAL NOT NULL,
                    average_pixel_delta REAL NOT NULL,
                    confidence REAL NOT NULL,
                    suspected INTEGER NOT NULL,
                    reason_codes TEXT,
                    message TEXT
                );
                """;

        statement.execute(sql);
    }

    private void createOperatorAuditEventsTable(Statement statement) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS operator_audit_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    actor TEXT NOT NULL,
                    role TEXT NOT NULL,
                    permission TEXT,
                    dangerous_action TEXT,
                    action_type TEXT NOT NULL,
                    target_type TEXT,
                    target_id TEXT,
                    result TEXT NOT NULL,
                    failure_reason TEXT,
                    created_at TEXT NOT NULL
                );
                """;

        statement.execute(sql);
    }

    private void createConfiguredPrintersTable(Statement statement) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS configured_printers (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    port_name TEXT NOT NULL,
                    mode TEXT NOT NULL,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
                """;

        statement.execute(sql);
    }

    private void createMonitoringRulesTable(Statement statement) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS monitoring_rules (
                    id TEXT PRIMARY KEY,
                    snapshot_on_state_change INTEGER NOT NULL DEFAULT 1,
                    temperature_threshold REAL NOT NULL,
                    min_interval_seconds INTEGER NOT NULL,
                    poll_interval_seconds INTEGER NOT NULL DEFAULT 5,
                    event_dedup_window_seconds INTEGER NOT NULL DEFAULT 60,
                    error_persistence_behavior TEXT NOT NULL DEFAULT 'DEDUPLICATED',
                    debug_wire_tracing_enabled INTEGER NOT NULL DEFAULT 0,
                    sd_upload_batch_size INTEGER NOT NULL DEFAULT 5,
                    sd_upload_recovery_window_multiplier INTEGER NOT NULL DEFAULT 2,
                    sd_upload_max_errors INTEGER NOT NULL DEFAULT 100,
                    sd_upload_max_consecutive_identical_resends INTEGER NOT NULL DEFAULT 10,
                    sd_upload_min_performance_percent INTEGER NOT NULL DEFAULT 5,
                    updated_at TEXT NOT NULL
                );
                """;

        statement.execute(sql);
    }

    private void createPrintFileSettingsTable(Statement statement) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS print_file_settings (
                    id TEXT PRIMARY KEY,
                    storage_directory TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
                """;

        statement.execute(sql);
    }

    private void createSerialTransferSettingsTable(Statement statement) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS serial_transfer_settings (
                    id TEXT PRIMARY KEY,
                    sd_upload_batch_size INTEGER NOT NULL DEFAULT 5,
                    sd_upload_min_batch_size INTEGER NOT NULL DEFAULT 1,
                    sd_upload_batch_upgrade_step INTEGER NOT NULL DEFAULT 1,
                    sd_upload_batch_downgrade_step INTEGER NOT NULL DEFAULT 1,
                    sd_upload_stable_lines_for_upgrade INTEGER NOT NULL DEFAULT 200,
                    sd_upload_resend_window_lines INTEGER NOT NULL DEFAULT 50,
                    sd_upload_resend_threshold_for_downgrade INTEGER NOT NULL DEFAULT 1,
                    sd_upload_recovery_threshold_for_min_batch INTEGER NOT NULL DEFAULT 3,
                    sd_upload_recovery_window_multiplier INTEGER NOT NULL DEFAULT 2,
                    sd_upload_max_errors INTEGER NOT NULL DEFAULT 100,
                    sd_upload_max_consecutive_identical_resends INTEGER NOT NULL DEFAULT 10,
                    sd_upload_min_performance_percent INTEGER NOT NULL DEFAULT 5,
                    sd_upload_max_retries_per_line INTEGER NOT NULL DEFAULT 3,
                    file_streaming_read_timeout_ms INTEGER NOT NULL DEFAULT 5000,
                    file_streaming_quiet_period_ms INTEGER NOT NULL DEFAULT 10,
                    file_streaming_read_activity_sleep_ms INTEGER NOT NULL DEFAULT 1,
                    file_streaming_read_idle_sleep_ms INTEGER NOT NULL DEFAULT 1,
                    file_streaming_recovery_replay_delay_ms INTEGER NOT NULL DEFAULT 15,
                    updated_at TEXT NOT NULL
                );
                """;

        statement.execute(sql);
    }

    private void createCameraSettingsTable(Statement statement) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS camera_settings (
                    printer_id TEXT PRIMARY KEY,
                    enabled INTEGER NOT NULL,
                    source_type TEXT NOT NULL,
                    source_value TEXT,
                    capture_interval_seconds INTEGER NOT NULL,
                    retention_snapshot_count INTEGER NOT NULL,
                    analysis_enabled INTEGER NOT NULL,
                    safety_enabled INTEGER NOT NULL,
                    pause_on_confirmed_spaghetti INTEGER NOT NULL,
                    confidence_threshold REAL NOT NULL,
                    confirmations_required INTEGER NOT NULL,
                    ffmpeg_command TEXT NOT NULL DEFAULT 'ffmpeg',
                    ffmpeg_input_format TEXT,
                    ffmpeg_video_size TEXT DEFAULT '640x480',
                    ffmpeg_timeout_ms INTEGER NOT NULL DEFAULT 5000,
                    ffmpeg_jpeg_quality INTEGER NOT NULL DEFAULT 3,
                    updated_at TEXT NOT NULL
                );
                """;

        statement.execute(sql);
    }

    private void createCameraEventsTable(Statement statement) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS camera_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    printer_id TEXT NOT NULL,
                    event_type TEXT NOT NULL,
                    message TEXT NOT NULL,
                    confidence REAL,
                    created_at TEXT NOT NULL
                );
                """;

        statement.execute(sql);
    }

    private void createCameraSnapshotMetadataTable(Statement statement) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS camera_snapshot_metadata (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    printer_id TEXT NOT NULL,
                    captured_at TEXT NOT NULL,
                    content_type TEXT NOT NULL,
                    file_path TEXT NOT NULL,
                    width INTEGER,
                    height INTEGER,
                    source_description TEXT
                );
                """;

        statement.execute(sql);
    }

    private void createSecuritySettingsTable(Statement statement) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS security_settings (
                    id TEXT PRIMARY KEY,
                    security_enabled INTEGER NOT NULL DEFAULT 0,
                    default_role TEXT NOT NULL DEFAULT 'ADMIN',
                    require_dangerous_action_confirmation INTEGER NOT NULL DEFAULT 1,
                    created_at TEXT,
                    updated_at TEXT
                );
                """;

        statement.execute(sql);
    }

    private void createRoleProfilesTable(Statement statement) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS role_profiles (
                    role_name TEXT PRIMARY KEY,
                    permissions_json TEXT NOT NULL DEFAULT '[]',
                    built_in INTEGER NOT NULL DEFAULT 1,
                    created_at TEXT,
                    updated_at TEXT
                );
                """;

        statement.execute(sql);
    }

    private void ensureBuiltInRoleProfiles(Connection connection) throws SQLException {
        String sql = """
                INSERT INTO role_profiles (
                    role_name,
                    permissions_json,
                    built_in,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT(role_name) DO NOTHING;
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (RoleProfile profile : RoleProfile.builtInProfiles().values()) {
                statement.setString(1, profile.role().name());
                statement.setString(2, RoleProfileStore.permissionsJson(profile.permissions()));
                statement.addBatch();
            }

            statement.executeBatch();
        }
    }
}
