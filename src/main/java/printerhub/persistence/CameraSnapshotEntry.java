package printerhub.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record CameraSnapshotEntry(
        Long id,
        String printerId,
        Long cameraJobId,
        String linkedPrintJobId,
        String snapshotPath,
        String contentType,
        long sizeBytes,
        Instant capturedAt,
        Instant retainedAt,
        String sourceType,
        String message
) {
    public CameraSnapshotEntry {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException("printerId must not be blank");
        }
        if (cameraJobId != null && cameraJobId <= 0L) {
            throw new IllegalArgumentException("cameraJobId must be greater than zero");
        }
        if (snapshotPath == null || snapshotPath.isBlank()) {
            throw new IllegalArgumentException("snapshotPath must not be blank");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("contentType must not be blank");
        }
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must not be negative");
        }

        printerId = printerId.trim();
        linkedPrintJobId = linkedPrintJobId == null || linkedPrintJobId.isBlank() ? null : linkedPrintJobId.trim();
        snapshotPath = snapshotPath.trim();
        contentType = contentType.trim();
        capturedAt = Objects.requireNonNull(capturedAt, "capturedAt");
        retainedAt = Objects.requireNonNull(retainedAt, "retainedAt");
        sourceType = sourceType == null || sourceType.isBlank() ? null : sourceType.trim();
        message = message == null || message.isBlank() ? null : message.trim();
    }

    public Optional<Long> cameraJobIdOptional() {
        return Optional.ofNullable(cameraJobId);
    }

    public Optional<String> linkedPrintJobIdOptional() {
        return Optional.ofNullable(linkedPrintJobId);
    }

    public String cameraJobKey() {
        return cameraJobId == null ? "unassigned" : Long.toString(cameraJobId);
    }

    public Optional<String> sourceTypeOptional() {
        return Optional.ofNullable(sourceType);
    }

    public Optional<String> messageOptional() {
        return Optional.ofNullable(message);
    }

    public static CameraSnapshotEntry captured(
            String printerId,
            Long cameraJobId,
            String linkedPrintJobId,
            String snapshotPath,
            String contentType,
            long sizeBytes,
            Instant capturedAt,
            Instant retainedAt,
            String sourceType,
            String message) {
        return new CameraSnapshotEntry(
                null,
                printerId,
                cameraJobId,
                linkedPrintJobId,
                snapshotPath,
                contentType,
                sizeBytes,
                capturedAt,
                retainedAt,
                sourceType,
                message);
    }
}