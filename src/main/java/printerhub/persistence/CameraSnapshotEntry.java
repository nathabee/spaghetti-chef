package printerhub.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record CameraSnapshotEntry(
        Long id,
        String printerId,
        String jobId,
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
        jobId = jobId == null || jobId.isBlank() ? null : jobId.trim();
        snapshotPath = snapshotPath.trim();
        contentType = contentType.trim();
        capturedAt = Objects.requireNonNull(capturedAt, "capturedAt");
        retainedAt = Objects.requireNonNull(retainedAt, "retainedAt");
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

    public static CameraSnapshotEntry captured(
            String printerId,
            String jobId,
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
                jobId,
                snapshotPath,
                contentType,
                sizeBytes,
                capturedAt,
                retainedAt,
                sourceType,
                message);
    }
}
