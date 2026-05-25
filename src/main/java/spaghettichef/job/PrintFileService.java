package spaghettichef.job;

import spaghettichef.OperationMessages;
import spaghettichef.persistence.PrintFileSettingsStore;
import spaghettichef.persistence.PrintFileStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class PrintFileService {

    public static final String GCODE_MEDIA_TYPE = "text/x.gcode";

    private final PrintFileStore printFileStore;
    private final PrintFileSettingsStore printFileSettingsStore;
    private final Clock clock;

    public PrintFileService(PrintFileStore printFileStore) {
        this(printFileStore, new PrintFileSettingsStore(), Clock.systemUTC());
    }

    public PrintFileService(PrintFileStore printFileStore, Clock clock) {
        this(printFileStore, new PrintFileSettingsStore(), clock);
    }

    public PrintFileService(
            PrintFileStore printFileStore,
            PrintFileSettingsStore printFileSettingsStore,
            Clock clock) {
        if (printFileStore == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("printFileStore"));
        }
        if (printFileSettingsStore == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("printFileSettingsStore"));
        }
        if (clock == null) {
            throw new IllegalArgumentException(OperationMessages.CLOCK_MUST_NOT_BE_NULL);
        }

        this.printFileStore = printFileStore;
        this.printFileSettingsStore = printFileSettingsStore;
        this.clock = clock;
    }

    public PrintFile registerHostFile(String pathValue) {
        if (pathValue == null || pathValue.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINT_FILE_PATH_MUST_NOT_BE_BLANK);
        }

        Path path = Path.of(pathValue.trim()).toAbsolutePath().normalize();
        String filename = path.getFileName() == null ? "" : path.getFileName().toString();

        if (!filename.toLowerCase(java.util.Locale.ROOT).endsWith(".gcode")) {
            throw new IllegalArgumentException(OperationMessages.UNSUPPORTED_PRINT_FILE_TYPE);
        }
        if (!Files.exists(path)) {
            throw new IllegalArgumentException(OperationMessages.PRINT_FILE_MUST_EXIST);
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException(OperationMessages.PRINT_FILE_MUST_BE_REGULAR_FILE);
        }
        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException(OperationMessages.PRINT_FILE_MUST_BE_READABLE);
        }

        return savePrintFile(path, filename);
    }

    public PrintFile storeUploadedFile(String originalFilename, byte[] content) {
        String filename = sanitizeFilename(originalFilename);

        if (!filename.toLowerCase(Locale.ROOT).endsWith(".gcode")) {
            throw new IllegalArgumentException(OperationMessages.UNSUPPORTED_PRINT_FILE_TYPE);
        }
        if (content == null) {
            content = new byte[0];
        }

        Path storageDirectory = Path.of(printFileSettingsStore.load().storageDirectory())
                .toAbsolutePath()
                .normalize();
        String storedFilename = UUID.randomUUID() + "-" + filename;
        Path storedPath = storageDirectory.resolve(storedFilename).normalize();

        if (!storedPath.startsWith(storageDirectory)) {
            throw new IllegalArgumentException(OperationMessages.PRINT_FILE_FILENAME_MUST_NOT_BE_BLANK);
        }

        try {
            Files.createDirectories(storageDirectory);
            Files.write(storedPath, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_STORE_UPLOADED_PRINT_FILE, exception);
        }

        return savePrintFile(storedPath, filename);
    }

    public String readContent(String printFileId) {
        PrintFile printFile = findById(printFileId)
                .orElseThrow(() -> new IllegalArgumentException(OperationMessages.PRINT_FILE_NOT_FOUND));

        try {
            return Files.readString(Path.of(printFile.path()), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_READ_PRINT_FILE_CONTENT, exception);
        }
    }

    public Optional<PrintFile> findById(String printFileId) {
        return printFileStore.findById(printFileId);
    }

    public List<PrintFile> findAll() {
        return printFileStore.findAll();
    }

    private String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINT_FILE_FILENAME_MUST_NOT_BE_BLANK);
        }

        String filename = Path.of(originalFilename.trim()).getFileName().toString();
        filename = filename.replaceAll("[^A-Za-z0-9._-]", "_");

        if (filename.isBlank() || ".".equals(filename) || "..".equals(filename)) {
            throw new IllegalArgumentException(OperationMessages.PRINT_FILE_FILENAME_MUST_NOT_BE_BLANK);
        }

        return filename;
    }

    private PrintFile savePrintFile(Path path, String originalFilename) {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException(OperationMessages.PRINT_FILE_MUST_EXIST);
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException(OperationMessages.PRINT_FILE_MUST_BE_REGULAR_FILE);
        }
        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException(OperationMessages.PRINT_FILE_MUST_BE_READABLE);
        }

        long sizeBytes;

        try {
            sizeBytes = Files.size(path);
        } catch (IOException exception) {
            throw new IllegalArgumentException(OperationMessages.PRINT_FILE_MUST_BE_READABLE, exception);
        }

        PrintFile printFile = new PrintFile(
                UUID.randomUUID().toString(),
                originalFilename,
                path.toString(),
                sizeBytes,
                GCODE_MEDIA_TYPE,
                Instant.now(clock));

        printFileStore.save(printFile);
        return printFile;
    }
}
