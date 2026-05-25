package spaghettichef.job;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spaghettichef.OperationMessages;
import spaghettichef.config.RuntimeDefaults;
import spaghettichef.persistence.DatabaseInitializer;
import spaghettichef.persistence.PrintFileStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class PrintFileServiceTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        System.clearProperty(RuntimeDefaults.DATABASE_FILE_PROPERTY);
    }

    @Test
    void registerHostFileAcceptsReadableGcodeFile() throws Exception {
        initializeDatabase("print-file-service-register.db");

        Path gcode = tempDir.resolve("test-cube.gcode");
        Files.writeString(gcode, "G28\nM105\n");

        PrintFileService service = new PrintFileService(
                new PrintFileStore(),
                Clock.fixed(Instant.parse("2026-05-06T08:00:00Z"), ZoneOffset.UTC));

        PrintFile printFile = service.registerHostFile(gcode.toString());

        assertNotNull(printFile.id());
        assertEquals("test-cube.gcode", printFile.originalFilename());
        assertEquals(gcode.toAbsolutePath().normalize().toString(), printFile.path());
        assertEquals(Files.size(gcode), printFile.sizeBytes());
        assertEquals(PrintFileService.GCODE_MEDIA_TYPE, printFile.mediaType());
        assertEquals(Instant.parse("2026-05-06T08:00:00Z"), printFile.createdAt());
        assertTrue(service.findById(printFile.id()).isPresent());
    }

    @Test
    void registerHostFileRejectsUnsupportedExtension() throws Exception {
        initializeDatabase("print-file-service-extension.db");

        Path stl = tempDir.resolve("model.stl");
        Files.writeString(stl, "solid cube");

        PrintFileService service = new PrintFileService(new PrintFileStore());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.registerHostFile(stl.toString()));

        assertEquals(OperationMessages.UNSUPPORTED_PRINT_FILE_TYPE, exception.getMessage());
    }

    @Test
    void registerHostFileRejectsMissingFile() {
        initializeDatabase("print-file-service-missing.db");

        PrintFileService service = new PrintFileService(new PrintFileStore());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.registerHostFile(tempDir.resolve("missing.gcode").toString()));

        assertEquals(OperationMessages.PRINT_FILE_MUST_EXIST, exception.getMessage());
    }

    private void initializeDatabase(String fileName) {
        String databaseFile = tempDir.resolve(fileName).toString();
        System.setProperty(RuntimeDefaults.DATABASE_FILE_PROPERTY, databaseFile);
        new DatabaseInitializer().initialize();
    }
}
