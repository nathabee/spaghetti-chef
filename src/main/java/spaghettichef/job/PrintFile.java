package spaghettichef.job;

import spaghettichef.OperationMessages;

import java.time.Instant;

public final class PrintFile {

    private final String id;
    private final String originalFilename;
    private final String path;
    private final long sizeBytes;
    private final String mediaType;
    private final Instant createdAt;

    public PrintFile(
            String id,
            String originalFilename,
            String path,
            long sizeBytes,
            String mediaType,
            Instant createdAt
    ) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINT_FILE_ID_MUST_NOT_BE_BLANK);
        }
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("originalFilename"));
        }
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINT_FILE_PATH_MUST_NOT_BE_BLANK);
        }
        if (sizeBytes < 0) {
            throw new IllegalArgumentException(OperationMessages.invalidEnumField("sizeBytes", String.valueOf(sizeBytes)));
        }
        if (mediaType == null || mediaType.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("mediaType"));
        }
        if (createdAt == null) {
            throw new IllegalArgumentException(OperationMessages.CREATED_AT_MUST_NOT_BE_NULL);
        }

        this.id = id.trim();
        this.originalFilename = originalFilename.trim();
        this.path = path.trim();
        this.sizeBytes = sizeBytes;
        this.mediaType = mediaType.trim();
        this.createdAt = createdAt;
    }

    public String id() {
        return id;
    }

    public String originalFilename() {
        return originalFilename;
    }

    public String path() {
        return path;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public String mediaType() {
        return mediaType;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
