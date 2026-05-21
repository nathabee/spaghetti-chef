package printerhub.persistence;

import java.time.Instant;

public record CameraArchiveJobSummary(
        String jobId,
        int fileCount,
        long totalBytes,
        Instant firstCapturedAt,
        Instant lastCapturedAt
) {
}
