package spaghettichef.camera;

import java.util.List;

public record CameraSnapshotPurgeReport(
        String printerId,
        long cameraJobId,
        int totalSnapshotCount,
        int keptSnapshotCount,
        int purgeCandidateCount,
        int deletedSnapshotCount,
        int alreadyDeletedSnapshotCount,
        int failedSnapshotCount,
        int retentionSnapshotCount,
        int purgeRetentionFrequency,
        List<Long> deletedSnapshotIds,
        List<Long> failedSnapshotIds,
        String message
) {
    public CameraSnapshotPurgeReport {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException("printerId must not be blank");
        }
        if (cameraJobId <= 0L) {
            throw new IllegalArgumentException("cameraJobId must be greater than zero");
        }
        if (totalSnapshotCount < 0 || keptSnapshotCount < 0 || purgeCandidateCount < 0
                || deletedSnapshotCount < 0 || alreadyDeletedSnapshotCount < 0 || failedSnapshotCount < 0) {
            throw new IllegalArgumentException("purge counts must not be negative");
        }
        if (retentionSnapshotCount < 0) {
            throw new IllegalArgumentException("retentionSnapshotCount must not be negative");
        }
        if (purgeRetentionFrequency <= 0) {
            throw new IllegalArgumentException("purgeRetentionFrequency must be greater than zero");
        }

        printerId = printerId.trim();
        deletedSnapshotIds = List.copyOf(deletedSnapshotIds == null ? List.of() : deletedSnapshotIds);
        failedSnapshotIds = List.copyOf(failedSnapshotIds == null ? List.of() : failedSnapshotIds);
        message = message == null || message.isBlank() ? "Snapshot purge completed." : message.trim();
    }
}
