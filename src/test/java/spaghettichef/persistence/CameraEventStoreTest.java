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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CameraEventStoreTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearDatabaseProperty() {
        System.clearProperty("spaghettichef.databaseFile");
    }

    @Test
    void saveStoresCameraEvent() throws Exception {
        useDatabase("camera-event-save.db");

        CameraEventStore store = new CameraEventStore();
        CameraEvent event = new CameraEvent(
                null,
                "printer-1",
                "CAMERA_FRAME_CAPTURED",
                "Camera frame captured",
                0.75,
                Instant.parse("2026-05-18T10:00:00Z"));

        CameraEvent saved = store.save(event);

        assertSame(event, saved);
        assertEquals(1, countRows());

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        """
                                SELECT printer_id, event_type, message, confidence, created_at
                                FROM camera_events
                                ORDER BY id ASC
                                LIMIT 1
                                """);
                ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next());
            assertEquals("printer-1", resultSet.getString("printer_id"));
            assertEquals("CAMERA_FRAME_CAPTURED", resultSet.getString("event_type"));
            assertEquals("Camera frame captured", resultSet.getString("message"));
            assertEquals(0.75, resultSet.getDouble("confidence"));
            assertEquals("2026-05-18T10:00:00Z", resultSet.getString("created_at"));
        }
    }

    @Test
    void saveStoresNullConfidence() throws Exception {
        useDatabase("camera-event-null-confidence.db");

        CameraEventStore store = new CameraEventStore();
        store.save(new CameraEvent(
                null,
                "printer-1",
                "CAMERA_AVAILABLE",
                "Camera available",
                null,
                Instant.parse("2026-05-18T10:00:00Z")));

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT confidence FROM camera_events LIMIT 1");
                ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next());
            resultSet.getDouble("confidence");
            assertTrue(resultSet.wasNull());
        }
    }

    @Test
    void recordCreatesAndStoresEvent() {
        useDatabase("camera-event-record.db");

        CameraEventStore store = new CameraEventStore();

        CameraEvent event = store.record(
                "printer-1",
                "CAMERA_CAPTURE_FAILED",
                "No frame returned");

        assertEquals("printer-1", event.printerId());
        assertEquals("CAMERA_CAPTURE_FAILED", event.eventType());
        assertEquals("No frame returned", event.message());
        assertTrue(event.confidence().isEmpty());
        assertEquals(1, countRows());
    }

    @Test
    void recordCreatesAndStoresEventWithConfidence() {
        useDatabase("camera-event-record-confidence.db");

        CameraEventStore store = new CameraEventStore();

        CameraEvent event = store.record(
                "printer-1",
                "SPAGHETTI_SUSPECTED",
                "Visual anomaly suspected",
                0.82);

        assertEquals("printer-1", event.printerId());
        assertEquals("SPAGHETTI_SUSPECTED", event.eventType());
        assertEquals("Visual anomaly suspected", event.message());
        assertEquals(0.82, event.confidence().orElseThrow());
        assertEquals(1, countRows());
    }

    @Test
    void findRecentByPrinterIdReturnsEventsInDescendingOrder() {
        useDatabase("camera-event-find-recent.db");

        CameraEventStore store = new CameraEventStore();

        store.save(new CameraEvent(
                null,
                "printer-1",
                "CAMERA_AVAILABLE",
                "first",
                null,
                Instant.parse("2026-05-18T10:00:00Z")));

        store.save(new CameraEvent(
                null,
                "printer-1",
                "CAMERA_FRAME_CAPTURED",
                "second",
                null,
                Instant.parse("2026-05-18T10:01:00Z")));

        store.save(new CameraEvent(
                null,
                "printer-2",
                "CAMERA_FRAME_CAPTURED",
                "other printer",
                null,
                Instant.parse("2026-05-18T10:02:00Z")));

        List<CameraEvent> events = store.findRecentByPrinterId("printer-1", 10);

        assertEquals(2, events.size());
        assertEquals("CAMERA_FRAME_CAPTURED", events.get(0).eventType());
        assertEquals("second", events.get(0).message());
        assertEquals("CAMERA_AVAILABLE", events.get(1).eventType());
        assertEquals("first", events.get(1).message());
    }

    @Test
    void findRecentByPrinterIdUsesDefaultLimitWhenNonPositive() {
        useDatabase("camera-event-default-limit.db");

        CameraEventStore store = new CameraEventStore();

        for (int i = 0; i < 25; i++) {
            store.save(new CameraEvent(
                    null,
                    "printer-1",
                    "CAMERA_FRAME_CAPTURED",
                    "event-" + i,
                    null,
                    Instant.parse("2026-05-18T10:00:00Z").plusSeconds(i)));
        }

        List<CameraEvent> events = store.findRecentByPrinterId("printer-1", 0);

        assertEquals(20, events.size());
    }

    @Test
    void saveFailsForNullEvent() {
        CameraEventStore store = new CameraEventStore();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> store.save(null));

        assertEquals("camera event must not be null", exception.getMessage());
    }

    @Test
    void findRecentByPrinterIdFailsForBlankPrinterId() {
        CameraEventStore store = new CameraEventStore();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> store.findRecentByPrinterId("   ", 10));

        assertEquals("printerId must not be blank", exception.getMessage());
    }

    @Test
    void saveWrapsDatabaseFailure() {
        System.setProperty("spaghettichef.databaseFile", tempDir.resolve("not-a-db-dir").toString());
        assertDoesNotThrow(() -> java.nio.file.Files.createDirectories(tempDir.resolve("not-a-db-dir")));

        CameraEventStore store = new CameraEventStore();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> store.save(CameraEvent.newEvent(
                        "printer-1",
                        "CAMERA_CAPTURE_FAILED",
                        "No frame returned",
                        null,
                        Instant.parse("2026-05-18T10:00:00Z"))));

        assertEquals("Failed to save camera event", exception.getMessage());
    }

    private void useDatabase(String fileName) {
        Path dbFile = tempDir.resolve(fileName);
        System.setProperty("spaghettichef.databaseFile", dbFile.toString());
        new DatabaseInitializer().initialize();
    }

    private int countRows() {
        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM camera_events");
                ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to count camera events", exception);
        }
    }
}