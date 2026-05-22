package printerhub.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseInitializerTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearDatabaseProperty() {
        System.clearProperty("printerhub.databaseFile");
    }

    @Test
    void initializeCreatesAllExpectedTables() throws Exception {
        Path dbFile = tempDir.resolve("printerhub-init.db");
        System.setProperty("printerhub.databaseFile", dbFile.toString());

        DatabaseInitializer initializer = new DatabaseInitializer();

        assertDoesNotThrow(initializer::initialize);

        assertTrue(tableExists("print_jobs"));
        assertTrue(tableExists("printer_snapshots"));
        assertTrue(tableExists("printer_events"));
        assertTrue(tableExists("configured_printers"));
        assertTrue(tableExists("monitoring_rules"));
        assertTrue(tableExists("serial_transfer_settings"));
        assertTrue(tableExists("camera_snapshot_entries"));
        assertTrue(tableExists("camera_delta_sets"));
        assertTrue(tableExists("camera_delta_frames"));
        assertTrue(tableExists("camera_calculation_runs"));
        assertTrue(tableExists("camera_calculation_results"));
    }

    @Test
    void initializeCanBeCalledTwice() throws Exception {
        Path dbFile = tempDir.resolve("printerhub-init-twice.db");
        System.setProperty("printerhub.databaseFile", dbFile.toString());

        DatabaseInitializer initializer = new DatabaseInitializer();

        assertDoesNotThrow(initializer::initialize);
        assertDoesNotThrow(initializer::initialize);

        assertTrue(tableExists("print_jobs"));
        assertTrue(tableExists("printer_snapshots"));
        assertTrue(tableExists("printer_events"));
        assertTrue(tableExists("configured_printers"));
        assertTrue(tableExists("monitoring_rules"));
        assertTrue(tableExists("serial_transfer_settings"));
        assertTrue(tableExists("camera_snapshot_entries"));
        assertTrue(tableExists("camera_delta_sets"));
        assertTrue(tableExists("camera_delta_frames"));
        assertTrue(tableExists("camera_calculation_runs"));
        assertTrue(tableExists("camera_calculation_results"));
    }

    @Test
    void initializeRespectsDatabaseFileOverride() throws Exception {
        Path dbFile = tempDir.resolve("custom-printerhub.db");
        System.setProperty("printerhub.databaseFile", dbFile.toString());

        DatabaseInitializer initializer = new DatabaseInitializer();
        initializer.initialize();

        assertEquals(dbFile.toString(), DatabaseConfig.databaseFile());
        assertTrue(tableExists("printer_events"));
    }

    @Test
    void initializeFailsForUnavailableDatabasePath() {
        Path invalidPath = tempDir.resolve("not-a-db-dir");
        System.setProperty("printerhub.databaseFile", invalidPath.toString());

        assertDoesNotThrow(() -> java.nio.file.Files.createDirectories(invalidPath));

        DatabaseInitializer initializer = new DatabaseInitializer();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                initializer::initialize
        );

        assertEquals("Failed to initialize database schema", exception.getMessage());
    }

    private boolean tableExists(String tableName) throws Exception {
        String sql = """
                SELECT name
                FROM sqlite_master
                WHERE type = 'table' AND name = ?;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, tableName);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }
}
