package printerhub.persistence;

import java.time.Instant;

public record CameraSnapshotJobSummary(
        String jobId,
        Long cameraJobId,
        String printerId,
        String linkedPrintJobId,
        CameraJobState state,
        Instant startedAt,
        Instant stoppedAt,
        int captureIntervalSeconds,
        int retainedSnapshots,
        String sourceType,
        String sourceDescription,
        String snapshotDirectory,
        int fileCount,
        long totalBytes,
        Instant firstCapturedAt,
        Instant lastCapturedAt
) {
    public CameraSnapshotJobSummary(
            String jobId,
            int fileCount,
            long totalBytes,
            Instant firstCapturedAt,
            Instant lastCapturedAt) {
        this(
                jobId,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                null,
                null,
                null,
                fileCount,
                totalBytes,
                firstCapturedAt,
                lastCapturedAt);
    }

    public static CameraSnapshotJobSummary fromCameraJob(
            CameraJob job,
            CameraSnapshotJobSummary snapshotSummary) {
        long cameraJobId = job.requireId();
        return new CameraSnapshotJobSummary(
                Long.toString(cameraJobId),
                cameraJobId,
                job.printerId(),
                job.linkedPrintJobId().orElse(null),
                job.state(),
                job.startedAt(),
                job.stoppedAt().orElse(null),
                job.captureIntervalSeconds(),
                job.retainedSnapshots(),
                job.sourceType(),
                job.sourceDescription().orElse(null),
                job.snapshotDirectory(),
                snapshotSummary == null ? 0 : snapshotSummary.fileCount(),
                snapshotSummary == null ? 0L : snapshotSummary.totalBytes(),
                snapshotSummary == null ? null : snapshotSummary.firstCapturedAt(),
                snapshotSummary == null ? null : snapshotSummary.lastCapturedAt());
    }
}
