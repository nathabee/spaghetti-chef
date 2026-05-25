package spaghettichef.job;

import spaghettichef.OperationMessages;
import spaghettichef.command.SdCardFile;
import spaghettichef.persistence.PrintFileStore;
import spaghettichef.persistence.PrinterSdFileStore;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PrinterSdFileService {

    private final PrinterSdFileStore printerSdFileStore;
    private final PrintFileStore printFileStore;
    private final Clock clock;

    public PrinterSdFileService(
            PrinterSdFileStore printerSdFileStore,
            PrintFileStore printFileStore) {
        this(printerSdFileStore, printFileStore, Clock.systemUTC());
    }

    public PrinterSdFileService(
            PrinterSdFileStore printerSdFileStore,
            PrintFileStore printFileStore,
            Clock clock) {
        if (printerSdFileStore == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("printerSdFileStore"));
        }
        if (printFileStore == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("printFileStore"));
        }
        if (clock == null) {
            throw new IllegalArgumentException(OperationMessages.CLOCK_MUST_NOT_BE_NULL);
        }

        this.printerSdFileStore = printerSdFileStore;
        this.printFileStore = printFileStore;
        this.clock = clock;
    }

    public PrinterSdFile register(
            String printerId,
            String firmwarePath,
            String displayName,
            Long sizeBytes,
            String rawLine,
            String printFileId) {
        validatePrinterId(printerId);
        if (firmwarePath == null || firmwarePath.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_SD_FILE_PATH_MUST_NOT_BE_BLANK);
        }

        if (printFileId != null && !printFileId.isBlank() && printFileStore.findById(printFileId).isEmpty()) {
            throw new IllegalArgumentException(OperationMessages.PRINT_FILE_NOT_FOUND);
        }

        String normalizedPrinterId = printerId.trim();
        String normalizedFirmwarePath = firmwarePath.trim();
        Optional<PrinterSdFile> existingFile = printerSdFileStore.findByPrinterIdAndFirmwarePath(normalizedPrinterId,
                normalizedFirmwarePath);
        Instant now = Instant.now(clock);
        PrinterSdFile file = new PrinterSdFile(
                existingFile.map(PrinterSdFile::id).orElseGet(() -> UUID.randomUUID().toString()),
                normalizedPrinterId,
                normalizedFirmwarePath,
                displayName,
                sizeBytes,
                rawLine,
                printFileId == null || printFileId.isBlank()
                        ? existingFile.map(PrinterSdFile::printFileId).orElse(null)
                        : printFileId,
                existingFile.map(existing -> existing.deleted() ? true : existing.enabled()).orElse(true),
                false,
                null,
                existingFile.map(PrinterSdFile::createdAt).orElse(now),
                now);

        printerSdFileStore.save(file);
        return file;
    }

    public PrinterSdFile registerListedFile(String printerId, SdCardFile listedFile) {
        if (listedFile == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("sdCardFile"));
        }

        return register(
                printerId,
                listedFile.filename(),
                listedFile.filename(),
                listedFile.sizeBytes(),
                listedFile.rawLine(),
                null);
    }

    public Optional<PrinterSdFile> findById(String id) {
        return printerSdFileStore.findById(id);
    }

    public PrinterSdFile setEnabled(String id, boolean enabled) {
        PrinterSdFile existingFile = printerSdFileStore.findById(id)
                .orElseThrow(() -> new IllegalStateException(OperationMessages.PRINTER_SD_FILE_NOT_FOUND));
        Instant now = Instant.now(clock);
        PrinterSdFile updatedFile = new PrinterSdFile(
                existingFile.id(),
                existingFile.printerId(),
                existingFile.firmwarePath(),
                existingFile.displayName(),
                existingFile.sizeBytes(),
                existingFile.rawLine(),
                existingFile.printFileId(),
                enabled && !existingFile.deleted(),
                existingFile.deleted(),
                existingFile.deletedAt(),
                existingFile.createdAt(),
                now);

        printerSdFileStore.save(updatedFile);
        return updatedFile;
    }

    public List<PrinterSdFile> findByPrinterId(String printerId) {
        validatePrinterId(printerId);
        return printerSdFileStore.findByPrinterId(printerId);
    }

    public List<PrinterSdFile> findAll() {
        return printerSdFileStore.findAll();
    }

    public PrinterSdFile markDeleted(String id) {
        PrinterSdFile existingFile = printerSdFileStore.findById(id)
                .orElseThrow(() -> new IllegalStateException(OperationMessages.PRINTER_SD_FILE_NOT_FOUND));
        Instant now = Instant.now(clock);
        PrinterSdFile updatedFile = new PrinterSdFile(
                existingFile.id(),
                existingFile.printerId(),
                existingFile.firmwarePath(),
                existingFile.displayName(),
                existingFile.sizeBytes(),
                existingFile.rawLine(),
                existingFile.printFileId(),
                false,
                true,
                now,
                existingFile.createdAt(),
                now);

        printerSdFileStore.save(updatedFile);
        return updatedFile;
    }

    private void validatePrinterId(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_ID_MUST_NOT_BE_BLANK);
        }
    }

    public Optional<PrinterSdFile> findByPrinterIdAndFirmwarePath(String printerId, String firmwarePath) {
        validatePrinterId(printerId);

        if (firmwarePath == null || firmwarePath.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_SD_FILE_PATH_MUST_NOT_BE_BLANK);
        }

        return printerSdFileStore.findByPrinterIdAndFirmwarePath(printerId.trim(), firmwarePath.trim());
    }
}
