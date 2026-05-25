package spaghettichef.runtime;

import org.junit.jupiter.api.Test;
import spaghettichef.PrinterSnapshot;
import spaghettichef.PrinterState;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class PrinterRuntimeStateCacheTest {

    @Test
    void constructorFailsWhenClockIsNull() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PrinterRuntimeStateCache(null)
        );

        assertEquals("clock must not be null", exception.getMessage());
    }

    @Test
    void initializePrinterCreatesDisconnectedSnapshot() {
        Instant fixedInstant = Instant.parse("2026-04-29T10:00:00Z");
        Clock clock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
        PrinterRuntimeStateCache cache = new PrinterRuntimeStateCache(clock);

        cache.initializePrinter("printer-1");

        PrinterSnapshot snapshot = cache.findByPrinterId("printer-1").orElseThrow();
        assertEquals(PrinterState.DISCONNECTED, snapshot.state());
        assertNull(snapshot.hotendTemperature());
        assertNull(snapshot.bedTemperature());
        assertNull(snapshot.lastResponse());
        assertNull(snapshot.errorMessage());
        assertEquals(fixedInstant, snapshot.updatedAt());
    }

    @Test
    void updateStoresSnapshot() {
        PrinterRuntimeStateCache cache = new PrinterRuntimeStateCache();
        PrinterSnapshot snapshot = PrinterSnapshot.fromResponse(
                PrinterState.IDLE,
                21.5,
                22.0,
                "ok T:21.5 B:22.0",
                Instant.parse("2026-04-29T10:05:00Z")
        );

        cache.update("printer-1", snapshot);

        PrinterSnapshot stored = cache.findByPrinterId("printer-1").orElseThrow();
        assertSame(snapshot, stored);
    }

    @Test
    void updateTrimsPrinterId() {
        PrinterRuntimeStateCache cache = new PrinterRuntimeStateCache();
        PrinterSnapshot snapshot = PrinterSnapshot.disconnected(
                Instant.parse("2026-04-29T10:06:00Z")
        );

        cache.update("  printer-1  ", snapshot);

        assertTrue(cache.findByPrinterId("printer-1").isPresent());
        assertSame(snapshot, cache.findByPrinterId("printer-1").orElseThrow());
    }

    @Test
    void updateFailsForNullOrBlankPrinterId() {
        PrinterRuntimeStateCache cache = new PrinterRuntimeStateCache();
        PrinterSnapshot snapshot = PrinterSnapshot.disconnected(
                Instant.parse("2026-04-29T10:07:00Z")
        );

        IllegalArgumentException ex1 = assertThrows(
                IllegalArgumentException.class,
                () -> cache.update(null, snapshot)
        );
        assertEquals("printerId must not be blank", ex1.getMessage());

        IllegalArgumentException ex2 = assertThrows(
                IllegalArgumentException.class,
                () -> cache.update("", snapshot)
        );
        assertEquals("printerId must not be blank", ex2.getMessage());

        IllegalArgumentException ex3 = assertThrows(
                IllegalArgumentException.class,
                () -> cache.update("   ", snapshot)
        );
        assertEquals("printerId must not be blank", ex3.getMessage());
    }

    @Test
    void updateFailsForNullSnapshot() {
        PrinterRuntimeStateCache cache = new PrinterRuntimeStateCache();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> cache.update("printer-1", null)
        );

        assertEquals("snapshot must not be null", exception.getMessage());
    }

    @Test
    void findByPrinterIdReturnsEmptyForNullOrBlankId() {
        PrinterRuntimeStateCache cache = new PrinterRuntimeStateCache();

        assertTrue(cache.findByPrinterId(null).isEmpty());
        assertTrue(cache.findByPrinterId("").isEmpty());
        assertTrue(cache.findByPrinterId("   ").isEmpty());
    }

    @Test
    void findByPrinterIdTrimsInput() {
        PrinterRuntimeStateCache cache = new PrinterRuntimeStateCache();
        PrinterSnapshot snapshot = PrinterSnapshot.disconnected(
                Instant.parse("2026-04-29T10:08:00Z")
        );

        cache.update("printer-1", snapshot);

        assertTrue(cache.findByPrinterId("  printer-1  ").isPresent());
        assertSame(snapshot, cache.findByPrinterId("  printer-1  ").orElseThrow());
    }

    @Test
    void currentOrDisconnectedReturnsStoredSnapshotWhenPresent() {
        PrinterRuntimeStateCache cache = new PrinterRuntimeStateCache();
        PrinterSnapshot snapshot = PrinterSnapshot.fromResponse(
                PrinterState.HEATING,
                55.0,
                24.0,
                "ok T:55.0 B:24.0",
                Instant.parse("2026-04-29T10:09:00Z")
        );

        cache.update("printer-1", snapshot);

        PrinterSnapshot current = cache.currentOrDisconnected("printer-1");

        assertSame(snapshot, current);
    }

    @Test
    void currentOrDisconnectedReturnsDisconnectedSnapshotWhenAbsent() {
        Instant fixedInstant = Instant.parse("2026-04-29T10:10:00Z");
        Clock clock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
        PrinterRuntimeStateCache cache = new PrinterRuntimeStateCache(clock);

        PrinterSnapshot current = cache.currentOrDisconnected("missing-printer");

        assertEquals(PrinterState.DISCONNECTED, current.state());
        assertEquals(fixedInstant, current.updatedAt());
    }

    @Test
    void findAllReturnsAllSnapshots() {
        PrinterRuntimeStateCache cache = new PrinterRuntimeStateCache();

        cache.update(
                "printer-1",
                PrinterSnapshot.disconnected(Instant.parse("2026-04-29T10:11:00Z"))
        );
        cache.update(
                "printer-2",
                PrinterSnapshot.fromResponse(
                        PrinterState.IDLE,
                        20.0,
                        21.0,
                        "ok",
                        Instant.parse("2026-04-29T10:12:00Z")
                )
        );

        assertEquals(2, cache.findAll().size());
    }

    @Test
    void removeDeletesStoredSnapshot() {
        PrinterRuntimeStateCache cache = new PrinterRuntimeStateCache();
        cache.update(
                "printer-1",
                PrinterSnapshot.disconnected(Instant.parse("2026-04-29T10:13:00Z"))
        );

        cache.remove("printer-1");

        assertTrue(cache.findByPrinterId("printer-1").isEmpty());
    }

    @Test
    void removeTrimsPrinterId() {
        PrinterRuntimeStateCache cache = new PrinterRuntimeStateCache();
        cache.update(
                "printer-1",
                PrinterSnapshot.disconnected(Instant.parse("2026-04-29T10:14:00Z"))
        );

        cache.remove("  printer-1  ");

        assertTrue(cache.findByPrinterId("printer-1").isEmpty());
    }

    @Test
    void removeIgnoresNullOrBlankPrinterId() {
        PrinterRuntimeStateCache cache = new PrinterRuntimeStateCache();
        cache.update(
                "printer-1",
                PrinterSnapshot.disconnected(Instant.parse("2026-04-29T10:15:00Z"))
        );

        assertDoesNotThrow(() -> cache.remove(null));
        assertDoesNotThrow(() -> cache.remove(""));
        assertDoesNotThrow(() -> cache.remove("   "));

        assertTrue(cache.findByPrinterId("printer-1").isPresent());
    }

    @Test
    void clearRemovesAllSnapshots() {
        PrinterRuntimeStateCache cache = new PrinterRuntimeStateCache();

        cache.update(
                "printer-1",
                PrinterSnapshot.disconnected(Instant.parse("2026-04-29T10:16:00Z"))
        );
        cache.update(
                "printer-2",
                PrinterSnapshot.disconnected(Instant.parse("2026-04-29T10:17:00Z"))
        );

        cache.clear();

        assertTrue(cache.findAll().isEmpty());
        assertTrue(cache.findByPrinterId("printer-1").isEmpty());
        assertTrue(cache.findByPrinterId("printer-2").isEmpty());
    }
}