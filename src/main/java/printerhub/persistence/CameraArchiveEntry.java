package printerhub.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record CameraArchiveEntry(
        Long id,
        String printerId,
        String jobId,
        String archivePath,
        String contentType,
        long sizeBytes,
        Instant capturedAt,
        Instant archivedAt,
        String sourceType,
        String message
) {
    public CameraArchiveEntry {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException("printerId must not be blank");
        }
        if (archivePath == null || archivePath.isBlank()) {
            throw new IllegalArgumentException("archivePath must not be blank");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("contentType must not be blank");
        }
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must not be negative");
        }

        printerId = printerId.trim();
        jobId = jobId == null || jobId.isBlank() ? null : jobId.trim();
        archivePath = archivePath.trim();
        contentType = contentType.trim();
        capturedAt = Objects.requireNonNull(capturedAt, "capturedAt");
        archivedAt = Objects.requireNonNull(archivedAt, "archivedAt");
        sourceType = sourceType == null || sourceType.isBlank() ? null : sourceType.trim();
        message = message == null || message.isBlank() ? null : message.trim();
    }

    public Optional<String> jobIdOptional() {
        return Optional.ofNullable(jobId);
    }

    public String jobKey() {
        return jobId == null ? "unassigned" : jobId;
    }

    public Optional<String> sourceTypeOptional() {
        return Optional.ofNullable(sourceType);
    }

    public Optional<String> messageOptional() {
        return Optional.ofNullable(message);
    }

    public static CameraArchiveEntry captured(
            String printerId,
            String jobId,
            String archivePath,
            String contentType,
            long sizeBytes,
            Instant capturedAt,
            Instant archivedAt,
            String sourceType,
            String message) {
        return new CameraArchiveEntry(
                null,
                printerId,
                jobId,
                archivePath,
                contentType,
                sizeBytes,
                capturedAt,
                archivedAt,
                sourceType,
                message);
    }
}
