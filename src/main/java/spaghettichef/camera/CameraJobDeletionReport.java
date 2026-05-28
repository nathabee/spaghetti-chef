package spaghettichef.camera;

import java.util.List;

public record CameraJobDeletionReport(
        String printerId,
        long cameraJobId,
        int deletedSnapshotFiles,
        long deletedSnapshotBytes,
        int deletedSnapshotRows,
        int deletedDeltaFiles,
        long deletedDeltaBytes,
        int deletedDeltaRows,
        int deletedDeltaSetRows,
        int deletedCalculationRunRows,
        int deletedCalculationResultRows,
        int deletedCameraEventRows,
        int deletedCameraJobRows,
        List<String> failedFiles,
        String message
) {
    public CameraJobDeletionReport {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException("printerId must not be blank");
        }
        if (cameraJobId <= 0L) {
            throw new IllegalArgumentException("cameraJobId must be greater than zero");
        }

        printerId = printerId.trim();
        failedFiles = List.copyOf(failedFiles == null ? List.of() : failedFiles);
        message = message == null || message.isBlank() ? "camera_job_deleted" : message.trim();
    }
}
