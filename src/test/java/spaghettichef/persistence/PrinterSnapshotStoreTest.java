package spaghettichef.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spaghettichef.PrinterSnapshot;
import spaghettichef.PrinterState;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrinterSnapshotStoreTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearDatabaseProperty() {
        System.clearProperty("spaghettichef.databaseFile");
    }

    @Test
    void saveStoresFirstSnapshot() {
        useDatabase("snapshot-first.db");

        PrinterSnapshotStore store = new PrinterSnapshotStore(
                new MonitoringRules(
                        5,
                        30,
                        1.0,
                        60,
                        MonitoringRules.ErrorPersistenceBehavior.DEDUPLICATED,
                        false));

        PrinterSnapshot snapshot = PrinterSnapshot.fromResponse(
                PrinterState.IDLE,
                21.5,
                22.0,
                "ok T:21.5 B:22.0",
                Instant.parse("2026-04-29T10:00:00Z"));

        store.save("printer-1", snapshot);

        List<PrinterSnapshot> snapshots = store.findRecentByPrinterId("printer-1", 10);
        assertEquals(1, snapshots.size());
        assertEquals(PrinterState.IDLE, snapshots.get(0).state());
        assertEquals(21.5, snapshots.get(0).hotendTemperature());
        assertEquals(22.0, snapshots.get(0).bedTemperature());
        assertEquals("ok T:21.5 B:22.0", snapshots.get(0).lastResponse());
    }

    @Test
    void stateChangeForcesSnapshotPersistence() {
        useDatabase("snapshot-state-change.db");

        PrinterSnapshotStore store = new PrinterSnapshotStore(
                new MonitoringRules(
                        5,
                        9999,
                        100.0,
                        60,
                        MonitoringRules.ErrorPersistenceBehavior.DEDUPLICATED,
                        false));

        store.save("printer-1", PrinterSnapshot.fromResponse(
                PrinterState.IDLE,
                21.0,
                22.0,
                "ok",
                Instant.parse("2026-04-29T10:00:00Z")));

        store.save("printer-1", PrinterSnapshot.fromResponse(
                PrinterState.HEATING,
                21.1,
                22.0,
                "ok",
                Instant.parse("2026-04-29T10:00:05Z")));

        List<PrinterSnapshot> snapshots = store.findRecentByPrinterId("printer-1", 10);
        assertEquals(2, snapshots.size());
    }

    @Test
    void belowThresholdDuplicateSnapshotIsSkipped() {
        useDatabase("snapshot-skip.db");

        PrinterSnapshotStore store = new PrinterSnapshotStore(
                new MonitoringRules(
                        5,
                        9999,
                        5.0,
                        60,
                        MonitoringRules.ErrorPersistenceBehavior.DEDUPLICATED,
                        false));

        store.save("printer-1", PrinterSnapshot.fromResponse(
                PrinterState.IDLE,
                20.0,
                20.0,
                "ok",
                Instant.parse("2026-04-29T10:00:00Z")));

        store.save("printer-1", PrinterSnapshot.fromResponse(
                PrinterState.IDLE,
                20.5,
                20.2,
                "ok",
                Instant.parse("2026-04-29T10:00:05Z")));

        List<PrinterSnapshot> snapshots = store.findRecentByPrinterId("printer-1", 10);
        assertEquals(1, snapshots.size());
    }

    @Test
    void temperatureDeltaAboveThresholdForcesSnapshotPersistence() {
        useDatabase("snapshot-temp-delta.db");

        PrinterSnapshotStore store = new PrinterSnapshotStore(
                new MonitoringRules(
                        5,
                        9999,
                        1.0,
                        60,
                        MonitoringRules.ErrorPersistenceBehavior.DEDUPLICATED,
                        false));

        store.save("printer-1", PrinterSnapshot.fromResponse(
                PrinterState.IDLE,
                20.0,
                20.0,
                "ok",
                Instant.parse("2026-04-29T10:00:00Z")));

        store.save("printer-1", PrinterSnapshot.fromResponse(
                PrinterState.IDLE,
                21.2,
                20.0,
                "ok",
                Instant.parse("2026-04-29T10:00:05Z")));

        List<PrinterSnapshot> snapshots = store.findRecentByPrinterId("printer-1", 10);
        assertEquals(2, snapshots.size());
    }

    @Test
    void minIntervalAllowsLaterSnapshotPersistence() {
        useDatabase("snapshot-min-interval.db");

        PrinterSnapshotStore store = new PrinterSnapshotStore(
                new MonitoringRules(
                        5,
                        30,
                        100.0,
                        60,
                        MonitoringRules.ErrorPersistenceBehavior.DEDUPLICATED,
                        false));

        store.save("printer-1", PrinterSnapshot.fromResponse(
                PrinterState.IDLE,
                20.0,
                20.0,
                "ok",
                Instant.parse("2026-04-29T10:00:00Z")));

        store.save("printer-1", PrinterSnapshot.fromResponse(
                PrinterState.IDLE,
                20.0,
                20.0,
                "ok",
                Instant.parse("2026-04-29T10:00:31Z")));

        List<PrinterSnapshot> snapshots = store.findRecentByPrinterId("printer-1", 10);
        assertEquals(2, snapshots.size());
    }

    @Test
    void findRecentByPrinterIdUsesDefaultLimitWhenNonPositive() {
        useDatabase("snapshot-default-limit.db");

        PrinterSnapshotStore store = new PrinterSnapshotStore();

        for (int i = 0; i < 25; i++) {
            store.save("printer-1", PrinterSnapshot.fromResponse(
                    PrinterState.IDLE,
                    20.0 + i,
                    20.0,
                    "ok",
                    Instant.parse("2026-04-29T10:00:00Z").plusSeconds(i * 31L)));
        }

        List<PrinterSnapshot> snapshots = store.findRecentByPrinterId("printer-1", 0);
        assertEquals(20, snapshots.size());
    }

    @Test
    void errorSnapshotIsLoadedAsErrorSnapshot() {
        useDatabase("snapshot-error.db");

        PrinterSnapshotStore store = new PrinterSnapshotStore();

        store.save("printer-1", PrinterSnapshot.error(
                PrinterState.ERROR,
                50.0,
                25.0,
                "Error: heater failure",
                "Heater failure",
                Instant.parse("2026-04-29T10:00:00Z")));

        List<PrinterSnapshot> snapshots = store.findRecentByPrinterId("printer-1", 10);
        assertEquals(1, snapshots.size());
        assertEquals(PrinterState.ERROR, snapshots.get(0).state());
        assertEquals("Heater failure", snapshots.get(0).errorMessage());
    }

    @Test
    void saveFailsForBlankPrinterId() {
        PrinterSnapshotStore store = new PrinterSnapshotStore();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> store.save("   ",
                        PrinterSnapshot.disconnected(Instant.parse("2026-04-29T10:00:00Z"))));

        assertEquals("printerId must not be blank", exception.getMessage());
    }

    @Test
    void saveFailsForNullSnapshot() {
        PrinterSnapshotStore store = new PrinterSnapshotStore();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> store.save("printer-1", null));

        assertEquals("snapshot must not be null", exception.getMessage());
    }

    @Test
    void findRecentByPrinterIdFailsForBlankPrinterId() {
        PrinterSnapshotStore store = new PrinterSnapshotStore();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> store.findRecentByPrinterId("   ", 10));

        assertEquals("printerId must not be blank", exception.getMessage());
    }

    @Test
    void saveWrapsDatabaseFailure() {
        System.setProperty("spaghettichef.databaseFile", tempDir.resolve("not-a-db-dir").toString());
        assertDoesNotThrow(() -> java.nio.file.Files.createDirectories(tempDir.resolve("not-a-db-dir")));

        PrinterSnapshotStore store = new PrinterSnapshotStore();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> store.save(
                        "printer-1",
                        PrinterSnapshot.disconnected(Instant.parse("2026-04-29T10:00:00Z"))));

        assertEquals("Failed to check latest printer snapshot", exception.getMessage());
    }

    private void useDatabase(String fileName) {
        Path dbFile = tempDir.resolve(fileName);
        System.setProperty("spaghettichef.databaseFile", dbFile.toString());
        new DatabaseInitializer().initialize();
    }
}