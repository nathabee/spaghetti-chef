package spaghettichef.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spaghettichef.config.RuntimeDefaults;
import spaghettichef.job.PrintFile;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PrintFileStoreTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        System.clearProperty(RuntimeDefaults.DATABASE_FILE_PROPERTY);
    }

    @Test
    void saveAndFindByIdPersistsPrintFileMetadata() {
        initializeDatabase("print-file-save.db");

        PrintFileStore store = new PrintFileStore();
        Instant now = Instant.parse("2026-05-06T08:00:00Z");

        store.save(new PrintFile(
                "file-1",
                "cube.gcode",
                tempDir.resolve("cube.gcode").toString(),
                123L,
                "text/x.gcode",
                now));

        PrintFile loaded = store.findById("file-1").orElseThrow();

        assertEquals("file-1", loaded.id());
        assertEquals("cube.gcode", loaded.originalFilename());
        assertEquals(123L, loaded.sizeBytes());
        assertEquals("text/x.gcode", loaded.mediaType());
        assertEquals(now, loaded.createdAt());
    }

    @Test
    void findAllReturnsNewestFirst() {
        initializeDatabase("print-file-list.db");

        PrintFileStore store = new PrintFileStore();

        store.save(new PrintFile(
                "file-old",
                "old.gcode",
                tempDir.resolve("old.gcode").toString(),
                10L,
                "text/x.gcode",
                Instant.parse("2026-05-06T08:00:00Z")));
        store.save(new PrintFile(
                "file-new",
                "new.gcode",
                tempDir.resolve("new.gcode").toString(),
                20L,
                "text/x.gcode",
                Instant.parse("2026-05-06T09:00:00Z")));

        List<PrintFile> files = store.findAll();

        assertEquals(2, files.size());
        assertEquals("file-new", files.get(0).id());
        assertEquals("file-old", files.get(1).id());
    }

    private void initializeDatabase(String fileName) {
        String databaseFile = tempDir.resolve(fileName).toString();
        System.setProperty(RuntimeDefaults.DATABASE_FILE_PROPERTY, databaseFile);
        new DatabaseInitializer().initialize();
    }
}
