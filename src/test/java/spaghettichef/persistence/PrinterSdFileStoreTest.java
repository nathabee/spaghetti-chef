package spaghettichef.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spaghettichef.config.RuntimeDefaults;
import spaghettichef.job.PrinterSdFile;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PrinterSdFileStoreTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        System.clearProperty(RuntimeDefaults.DATABASE_FILE_PROPERTY);
    }

    @Test
    void saveAndFindByIdPersistsPrinterSideFileMetadata() {
        initializeDatabase("printer-sd-file-save.db");

        PrinterSdFileStore store = new PrinterSdFileStore();
        Instant createdAt = Instant.parse("2026-05-07T08:00:00Z");
        Instant updatedAt = Instant.parse("2026-05-07T08:01:00Z");

        store.save(new PrinterSdFile(
                "sd-file-1",
                "printer-1",
                "/OLD/BOAT~1.GCO",
                "boat.gcode",
                12345L,
                "/OLD/BOAT~1.GCO 12345",
                "print-file-1",
                createdAt,
                updatedAt));

        PrinterSdFile loaded = store.findById("sd-file-1").orElseThrow();

        assertEquals("printer-1", loaded.printerId());
        assertEquals("/OLD/BOAT~1.GCO", loaded.firmwarePath());
        assertEquals("boat.gcode", loaded.displayName());
        assertEquals(12345L, loaded.sizeBytes());
        assertEquals("/OLD/BOAT~1.GCO 12345", loaded.rawLine());
        assertEquals("print-file-1", loaded.printFileId());
        assertTrue(loaded.enabled());
        assertEquals(createdAt, loaded.createdAt());
        assertEquals(updatedAt, loaded.updatedAt());
    }

    @Test
    void saveUpdatesExistingPrinterPathWithoutChangingId() {
        initializeDatabase("printer-sd-file-upsert.db");

        PrinterSdFileStore store = new PrinterSdFileStore();

        store.save(new PrinterSdFile(
                "sd-file-original",
                "printer-1",
                "CUBE.GCO",
                "cube.gcode",
                100L,
                "CUBE.GCO 100",
                "print-file-1",
                Instant.parse("2026-05-07T08:00:00Z"),
                Instant.parse("2026-05-07T08:00:00Z")));
        store.save(new PrinterSdFile(
                "sd-file-new",
                "printer-1",
                "CUBE.GCO",
                "cube-new.gcode",
                200L,
                "CUBE.GCO 200",
                null,
                Instant.parse("2026-05-07T09:00:00Z"),
                Instant.parse("2026-05-07T09:00:00Z")));

        PrinterSdFile loaded = store.findByPrinterIdAndFirmwarePath("printer-1", "CUBE.GCO").orElseThrow();
        List<PrinterSdFile> files = store.findByPrinterId("printer-1");

        assertEquals(1, files.size());
        assertEquals("sd-file-original", loaded.id());
        assertEquals("cube-new.gcode", loaded.displayName());
        assertEquals(200L, loaded.sizeBytes());
        assertEquals("print-file-1", loaded.printFileId());
        assertTrue(loaded.enabled());
        assertEquals(Instant.parse("2026-05-07T08:00:00Z"), loaded.createdAt());
        assertEquals(Instant.parse("2026-05-07T09:00:00Z"), loaded.updatedAt());
    }

    @Test
    void savePreservesDisabledStateWhenRequested() {
        initializeDatabase("printer-sd-file-disabled.db");

        PrinterSdFileStore store = new PrinterSdFileStore();

        store.save(new PrinterSdFile(
                "sd-file-disabled",
                "printer-1",
                "OLD.GCO",
                "old.gcode",
                null,
                "OLD.GCO",
                null,
                false,
                Instant.parse("2026-05-07T08:00:00Z"),
                Instant.parse("2026-05-07T08:00:00Z")));

        PrinterSdFile loaded = store.findById("sd-file-disabled").orElseThrow();

        assertFalse(loaded.enabled());
    }

    private void initializeDatabase(String fileName) {
        String databaseFile = tempDir.resolve(fileName).toString();
        System.setProperty(RuntimeDefaults.DATABASE_FILE_PROPERTY, databaseFile);
        new DatabaseInitializer().initialize();
    }
}
