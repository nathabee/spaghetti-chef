package spaghettichef.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PrinterEventStoreTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearDatabaseProperty() {
        System.clearProperty("spaghettichef.databaseFile");
    }

    @Test
    void saveStoresEvent() throws Exception {
        useDatabase("event-save.db");

        PrinterEventStore store = new PrinterEventStore();
        PrinterEvent event = new PrinterEvent(
                0L,
                "printer-1",
                "job-1",
                "PRINTER_ERROR",
                "Heater failure",
                Instant.parse("2026-04-29T10:00:00Z")
        );

        PrinterEvent saved = store.save(event);

        assertSame(event, saved);
        assertEquals(1, countRows());

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        """
                        SELECT printer_id, job_id, event_type, message, created_at
                        FROM printer_events
                        ORDER BY id ASC
                        LIMIT 1
                        """
                );
                ResultSet resultSet = statement.executeQuery()
        ) {
            assertTrue(resultSet.next());
            assertEquals("printer-1", resultSet.getString("printer_id"));
            assertEquals("job-1", resultSet.getString("job_id"));
            assertEquals("PRINTER_ERROR", resultSet.getString("event_type"));
            assertEquals("Heater failure", resultSet.getString("message"));
            assertEquals("2026-04-29T10:00:00Z", resultSet.getString("created_at"));
        }
    }

    @Test
    void recordCreatesAndStoresEvent() throws Exception {
        useDatabase("event-record.db");

        PrinterEventStore store = new PrinterEventStore();

        PrinterEvent event = store.record(
                "printer-2",
                null,
                "PRINTER_TIMEOUT",
                "No response for command M105"
        );

        assertEquals("printer-2", event.printerId());
        assertNull(event.jobId());
        assertEquals("PRINTER_TIMEOUT", event.eventType());
        assertEquals("No response for command M105", event.message());
        assertEquals(1, countRows());
    }

    @Test
    void findRecentByPrinterIdReturnsPrinterEventsInDescendingOrder() throws Exception {
        useDatabase("event-find-recent.db");

        PrinterEventStore store = new PrinterEventStore();
        store.save(new PrinterEvent(
                0L,
                "printer-1",
                null,
                "COMMAND_EXECUTED",
                "first",
                Instant.parse("2026-04-29T10:00:00Z")
        ));
        store.save(new PrinterEvent(
                0L,
                "printer-1",
                null,
                "COMMAND_FAILED",
                "second",
                Instant.parse("2026-04-29T10:01:00Z")
        ));
        store.save(new PrinterEvent(
                0L,
                "printer-2",
                null,
                "PRINTER_ERROR",
                "other printer",
                Instant.parse("2026-04-29T10:02:00Z")
        ));

        List<PrinterEvent> events = store.findRecentByPrinterId("printer-1", 10);

        assertEquals(2, events.size());
        assertEquals("COMMAND_FAILED", events.get(0).eventType());
        assertEquals("second", events.get(0).message());
        assertEquals("COMMAND_EXECUTED", events.get(1).eventType());
        assertEquals("first", events.get(1).message());
    }

    @Test
    void findRecentByPrinterIdUsesDefaultLimitWhenNonPositive() throws Exception {
        useDatabase("event-default-limit.db");

        PrinterEventStore store = new PrinterEventStore();

        for (int i = 0; i < 25; i++) {
            store.save(new PrinterEvent(
                    0L,
                    "printer-1",
                    null,
                    "COMMAND_EXECUTED",
                    "event-" + i,
                    Instant.parse("2026-04-29T10:00:00Z").plusSeconds(i)
            ));
        }

        List<PrinterEvent> events = store.findRecentByPrinterId("printer-1", 0);

        assertEquals(20, events.size());
    }

    @Test
    void saveFailsForNullEvent() {
        PrinterEventStore store = new PrinterEventStore();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> store.save(null)
        );

        assertEquals("printer event must not be null", exception.getMessage());
    }

    @Test
    void findRecentByPrinterIdFailsForBlankPrinterId() {
        PrinterEventStore store = new PrinterEventStore();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> store.findRecentByPrinterId("   ", 10)
        );

        assertEquals("printerId must not be blank", exception.getMessage());
    }

    @Test
    void saveWrapsDatabaseFailure() {
        System.setProperty("spaghettichef.databaseFile", tempDir.resolve("not-a-db-dir").toString());
        assertDoesNotThrow(() -> java.nio.file.Files.createDirectories(tempDir.resolve("not-a-db-dir")));

        PrinterEventStore store = new PrinterEventStore();
        PrinterEvent event = PrinterEvent.create(
                "printer-1",
                null,
                "PRINTER_ERROR",
                "Heater failure"
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> store.save(event)
        );

        assertEquals("Failed to save printer event", exception.getMessage());
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
                        "SELECT COUNT(*) FROM printer_events"
                );
                ResultSet resultSet = statement.executeQuery()
        ) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}