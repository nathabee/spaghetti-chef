package spaghettichef.persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CameraSnapshotMetadataStoreTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearDatabaseProperty() {
        System.clearProperty("spaghettichef.databaseFile");
    }

    @Test
    void saveStoresSnapshotMetadata() throws Exception {
        useDatabase("camera-snapshot-save.db");

        CameraSnapshotMetadataStore store = new CameraSnapshotMetadataStore();
        CameraSnapshotMetadata metadata = new CameraSnapshotMetadata(
                null,
                "printer-1",
                Instant.parse("2026-05-18T10:00:00Z"),
                "image/jpeg",
                "/tmp/spaghettichef-camera/printer-1/latest.jpg",
                320,
                240,
                "simulated-camera:printer-1");

        CameraSnapshotMetadata saved = store.save(metadata);

        assertSame(metadata, saved);
        assertEquals(1, countRows());

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        """
                                SELECT
                                    printer_id,
                                    captured_at,
                                    content_type,
                                    file_path,
                                    width,
                                    height,
                                    source_description
                                FROM camera_snapshot_metadata
                                ORDER BY id ASC
                                LIMIT 1
                                """);
                ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next());
            assertEquals("printer-1", resultSet.getString("printer_id"));
            assertEquals("2026-05-18T10:00:00Z", resultSet.getString("captured_at"));
            assertEquals("image/jpeg", resultSet.getString("content_type"));
            assertEquals("/tmp/spaghettichef-camera/printer-1/latest.jpg", resultSet.getString("file_path"));
            assertEquals(320, resultSet.getInt("width"));
            assertEquals(240, resultSet.getInt("height"));
            assertEquals("simulated-camera:printer-1", resultSet.getString("source_description"));
        }
    }

    @Test
    void saveStoresNullableDimensionsAndSourceDescription() throws Exception {
        useDatabase("camera-snapshot-nullable.db");

        CameraSnapshotMetadataStore store = new CameraSnapshotMetadataStore();

        store.save(new CameraSnapshotMetadata(
                null,
                "printer-1",
                Instant.parse("2026-05-18T10:00:00Z"),
                "image/jpeg",
                "/tmp/latest.jpg",
                null,
                null,
                null));

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT width, height, source_description FROM camera_snapshot_metadata LIMIT 1");
                ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next());

            resultSet.getInt("width");
            assertTrue(resultSet.wasNull());

            resultSet.getInt("height");
            assertTrue(resultSet.wasNull());

            assertEquals(null, resultSet.getString("source_description"));
        }
    }

    @Test
    void findLatestByPrinterIdReturnsMostRecentInsertedMetadata() {
        useDatabase("camera-snapshot-latest.db");

        CameraSnapshotMetadataStore store = new CameraSnapshotMetadataStore();

        store.save(new CameraSnapshotMetadata(
                null,
                "printer-1",
                Instant.parse("2026-05-18T10:00:00Z"),
                "image/jpeg",
                "/tmp/first.jpg",
                320,
                240,
                "first"));

        store.save(new CameraSnapshotMetadata(
                null,
                "printer-1",
                Instant.parse("2026-05-18T10:01:00Z"),
                "image/jpeg",
                "/tmp/second.jpg",
                320,
                240,
                "second"));

        store.save(new CameraSnapshotMetadata(
                null,
                "printer-2",
                Instant.parse("2026-05-18T10:02:00Z"),
                "image/jpeg",
                "/tmp/other.jpg",
                320,
                240,
                "other"));

        CameraSnapshotMetadata latest = store.findLatestByPrinterId("printer-1").orElseThrow();

        assertEquals("/tmp/second.jpg", latest.filePath());
        assertEquals("second", latest.sourceDescription().orElseThrow());
    }

    @Test
    void findLatestByPrinterIdReturnsEmptyWhenMissing() {
        useDatabase("camera-snapshot-missing.db");

        CameraSnapshotMetadataStore store = new CameraSnapshotMetadataStore();

        Optional<CameraSnapshotMetadata> latest = store.findLatestByPrinterId("printer-1");

        assertTrue(latest.isEmpty());
    }

    @Test
    void findRecentByPrinterIdReturnsMetadataInDescendingOrder() {
        useDatabase("camera-snapshot-recent.db");

        CameraSnapshotMetadataStore store = new CameraSnapshotMetadataStore();

        store.save(CameraSnapshotMetadata.newSnapshot(
                "printer-1",
                Instant.parse("2026-05-18T10:00:00Z"),
                "image/jpeg",
                "/tmp/first.jpg",
                320,
                240,
                "first"));

        store.save(CameraSnapshotMetadata.newSnapshot(
                "printer-1",
                Instant.parse("2026-05-18T10:01:00Z"),
                "image/jpeg",
                "/tmp/second.jpg",
                320,
                240,
                "second"));

        store.save(CameraSnapshotMetadata.newSnapshot(
                "printer-2",
                Instant.parse("2026-05-18T10:02:00Z"),
                "image/jpeg",
                "/tmp/other.jpg",
                320,
                240,
                "other"));

        List<CameraSnapshotMetadata> snapshots = store.findRecentByPrinterId("printer-1", 10);

        assertEquals(2, snapshots.size());
        assertEquals("/tmp/second.jpg", snapshots.get(0).filePath());
        assertEquals("/tmp/first.jpg", snapshots.get(1).filePath());
    }

    @Test
    void findRecentByPrinterIdUsesDefaultLimitWhenNonPositive() {
        useDatabase("camera-snapshot-default-limit.db");

        CameraSnapshotMetadataStore store = new CameraSnapshotMetadataStore();

        for (int i = 0; i < 25; i++) {
            store.save(CameraSnapshotMetadata.newSnapshot(
                    "printer-1",
                    Instant.parse("2026-05-18T10:00:00Z").plusSeconds(i),
                    "image/jpeg",
                    "/tmp/snapshot-" + i + ".jpg",
                    320,
                    240,
                    "snapshot-" + i));
        }

        List<CameraSnapshotMetadata> snapshots = store.findRecentByPrinterId("printer-1", 0);

        assertEquals(20, snapshots.size());
    }

    @Test
    void saveFailsForNullMetadata() {
        CameraSnapshotMetadataStore store = new CameraSnapshotMetadataStore();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> store.save(null));

        assertEquals("camera snapshot metadata must not be null", exception.getMessage());
    }

    @Test
    void findLatestByPrinterIdFailsForBlankPrinterId() {
        CameraSnapshotMetadataStore store = new CameraSnapshotMetadataStore();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> store.findLatestByPrinterId("   "));

        assertEquals("printerId must not be blank", exception.getMessage());
    }

    @Test
    void findRecentByPrinterIdFailsForBlankPrinterId() {
        CameraSnapshotMetadataStore store = new CameraSnapshotMetadataStore();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> store.findRecentByPrinterId("   ", 10));

        assertEquals("printerId must not be blank", exception.getMessage());
    }

    @Test
    void saveWrapsDatabaseFailure() {
        System.setProperty("spaghettichef.databaseFile", tempDir.resolve("not-a-db-dir").toString());
        assertDoesNotThrow(() -> java.nio.file.Files.createDirectories(tempDir.resolve("not-a-db-dir")));

        CameraSnapshotMetadataStore store = new CameraSnapshotMetadataStore();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> store.save(CameraSnapshotMetadata.newSnapshot(
                        "printer-1",
                        Instant.parse("2026-05-18T10:00:00Z"),
                        "image/jpeg",
                        "/tmp/latest.jpg",
                        320,
                        240,
                        "test")));

        assertEquals("Failed to save camera snapshot metadata", exception.getMessage());
    }

    private void useDatabase(String fileName) {
        Path dbFile = tempDir.resolve(fileName);
        System.setProperty("spaghettichef.databaseFile", dbFile.toString());
        new DatabaseInitializer().initialize();
    }

    private int countRows() throws Exception {
        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM camera_snapshot_metadata");
                ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}