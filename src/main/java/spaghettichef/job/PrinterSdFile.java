package spaghettichef.job;

import spaghettichef.OperationMessages;

import java.time.Instant;

public final class PrinterSdFile {

    private final String id;
    private final String printerId;
    private final String firmwarePath;
    private final String displayName;
    private final Long sizeBytes;
    private final String rawLine;
    private final String printFileId;
    private final boolean enabled;
    private final boolean deleted;
    private final Instant deletedAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    public PrinterSdFile(
            String id,
            String printerId,
            String firmwarePath,
            String displayName,
            Long sizeBytes,
            String rawLine,
            String printFileId,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(
                id,
                printerId,
                firmwarePath,
                displayName,
                sizeBytes,
                rawLine,
                printFileId,
                true,
                false,
                null,
                createdAt,
                updatedAt
        );
    }

    public PrinterSdFile(
            String id,
            String printerId,
            String firmwarePath,
            String displayName,
            Long sizeBytes,
            String rawLine,
            String printFileId,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(
                id,
                printerId,
                firmwarePath,
                displayName,
                sizeBytes,
                rawLine,
                printFileId,
                enabled,
                false,
                null,
                createdAt,
                updatedAt
        );
    }

    public PrinterSdFile(
            String id,
            String printerId,
            String firmwarePath,
            String displayName,
            Long sizeBytes,
            String rawLine,
            String printFileId,
            boolean enabled,
            boolean deleted,
            Instant deletedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_SD_FILE_ID_MUST_NOT_BE_BLANK);
        }
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_ID_MUST_NOT_BE_BLANK);
        }
        if (firmwarePath == null || firmwarePath.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_SD_FILE_PATH_MUST_NOT_BE_BLANK);
        }
        if (createdAt == null) {
            throw new IllegalArgumentException(OperationMessages.CREATED_AT_MUST_NOT_BE_NULL);
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException(OperationMessages.UPDATED_AT_MUST_NOT_BE_NULL);
        }

        this.id = id.trim();
        this.printerId = printerId.trim();
        this.firmwarePath = firmwarePath.trim();
        this.displayName = displayName == null || displayName.isBlank() ? this.firmwarePath : displayName.trim();
        this.sizeBytes = sizeBytes;
        this.rawLine = rawLine == null || rawLine.isBlank() ? this.firmwarePath : rawLine.trim();
        this.printFileId = printFileId == null || printFileId.isBlank() ? null : printFileId.trim();
        this.enabled = enabled;
        this.deleted = deleted;
        this.deletedAt = deletedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String id() {
        return id;
    }

    public String printerId() {
        return printerId;
    }

    public String firmwarePath() {
        return firmwarePath;
    }

    public String displayName() {
        return displayName;
    }

    public Long sizeBytes() {
        return sizeBytes;
    }

    public String rawLine() {
        return rawLine;
    }

    public String printFileId() {
        return printFileId;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean deleted() {
        return deleted;
    }

    public Instant deletedAt() {
        return deletedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
