package spaghettichef.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import spaghettichef.persistence.CameraSnapshotEntry;
import spaghettichef.persistence.CameraSnapshotEntryStore;
import spaghettichef.persistence.DatabaseInitializer;

class CameraSnapshotPurgeServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-05-27T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @AfterEach
    void clearDatabaseProperty() {
        System.clearProperty("spaghettichef.databaseFile");
    }

    @Test
    void purgeKeepsFrequencySnapshotsAndLatestRetentionWindow() throws Exception {
        useDatabase("camera-snapshot-purge.db");
        CameraSnapshotEntryStore store = new CameraSnapshotEntryStore();
        List<Path> files = saveSnapshots(store, 30);

        CameraSnapshotPurgeReport report = new CameraSnapshotPurgeService(store, FIXED_CLOCK)
                .purge("printer-1", 1L, 5, 5, "test purge");

        assertEquals(30, report.totalSnapshotCount());
        assertEquals(10, report.keptSnapshotCount());
        assertEquals(20, report.deletedSnapshotCount());
        assertEquals(0, report.failedSnapshotCount());
        assertTrue(Files.exists(files.get(0)));
        assertTrue(Files.exists(files.get(5)));
        assertTrue(Files.exists(files.get(10)));
        assertTrue(Files.exists(files.get(15)));
        assertTrue(Files.exists(files.get(20)));
        assertTrue(Files.exists(files.get(25)));
        assertTrue(Files.exists(files.get(29)));
        assertFalse(Files.exists(files.get(1)));
        assertFalse(Files.exists(files.get(24)));

        List<CameraSnapshotEntry> entries = store.findByPrinterIdAndJobId("printer-1", "1");
        assertFalse(entries.get(0).fileDeleted());
        assertTrue(entries.get(1).fileDeleted());
        assertEquals(FIXED_INSTANT, entries.get(1).deletedAt());
        assertEquals("test purge", entries.get(1).deletionReason());
    }

    @Test
    void purgeIsIdempotentForAlreadyDeletedRows() throws Exception {
        useDatabase("camera-snapshot-purge-idempotent.db");
        CameraSnapshotEntryStore store = new CameraSnapshotEntryStore();
        saveSnapshots(store, 8);
        CameraSnapshotPurgeService service = new CameraSnapshotPurgeService(store, FIXED_CLOCK);

        CameraSnapshotPurgeReport first = service.purge("printer-1", 1L, 2, 3, "first purge");
        CameraSnapshotPurgeReport second = service.purge("printer-1", 1L, 2, 3, "second purge");

        assertEquals(4, first.deletedSnapshotCount());
        assertEquals(0, second.deletedSnapshotCount());
        assertEquals(4, second.alreadyDeletedSnapshotCount());
        assertEquals(0, second.failedSnapshotCount());
    }

    private List<Path> saveSnapshots(CameraSnapshotEntryStore store, int count) throws Exception {
        Path directory = tempDir.resolve("camera").resolve("p1").resolve("snapshots").resolve("1");
        Files.createDirectories(directory);
        java.util.ArrayList<Path> files = new java.util.ArrayList<>();

        for (int index = 1; index <= count; index++) {
            Path path = directory.resolve("%06d_snapshot.jpg".formatted(index));
            Files.write(path, new byte[] {(byte) index});
            files.add(path);
            store.save(CameraSnapshotEntry.captured(
                    "printer-1",
                    1L,
                    null,
                    path.toString(),
                    "image/jpeg",
                    Files.size(path),
                    FIXED_INSTANT.plusSeconds(index),
                    FIXED_INSTANT.plusSeconds(index),
                    "simulated",
                    "snapshot " + index));
        }

        Files.write(tempDir.resolve("camera").resolve("p1").resolve("latest.jpg"), new byte[] {1});
        return files;
    }

    private void useDatabase(String fileName) {
        Path dbFile = tempDir.resolve(fileName);
        System.setProperty("spaghettichef.databaseFile", dbFile.toString());
        new DatabaseInitializer().initialize();
    }
}
