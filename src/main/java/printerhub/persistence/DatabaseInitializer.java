package printerhub.persistence;

import printerhub.OperationMessages;

import java.sql.Connection;
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
            createConfiguredPrintersTable(statement);
            createMonitoringRulesTable(statement);
            createPrintFileSettingsTable(statement);

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
}
