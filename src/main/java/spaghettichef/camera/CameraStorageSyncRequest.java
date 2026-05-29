package spaghettichef.camera;

public record CameraStorageSyncRequest(
        String layout,
        boolean dryRun,
        boolean syncSnapshots,
        boolean syncDeltas,
        boolean deleteRowsForMissingFiles,
        boolean reactivateDeletedSnapshotRows,
        boolean createMissingCameraJobs,
        boolean createMissingDeltaSets,
        String requiredConfirmation
) {
    public CameraStorageSyncRequest(
            boolean dryRun,
            boolean syncSnapshots,
            boolean syncDeltas,
            boolean deleteRowsForMissingFiles,
            boolean reactivateDeletedSnapshotRows,
            boolean createMissingCameraJobs,
            boolean createMissingDeltaSets,
            String requiredConfirmation) {
        this(
                null,
                dryRun,
                syncSnapshots,
                syncDeltas,
                deleteRowsForMissingFiles,
                reactivateDeletedSnapshotRows,
                createMissingCameraJobs,
                createMissingDeltaSets,
                requiredConfirmation);
    }

    public CameraStorageSyncRequest {
        layout = layout == null || layout.isBlank() ? "runtime-camera-storage" : layout.trim();
        requiredConfirmation = requiredConfirmation == null || requiredConfirmation.isBlank()
                ? null
                : requiredConfirmation.trim();
    }
}
