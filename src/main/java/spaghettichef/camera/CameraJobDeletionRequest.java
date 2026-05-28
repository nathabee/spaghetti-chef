package spaghettichef.camera;

public record CameraJobDeletionRequest(
        boolean deleteSnapshotFiles,
        boolean deleteSnapshotRows,
        boolean deleteDeltaFiles,
        boolean deleteDeltaRows,
        boolean deleteCalculationRuns,
        boolean deleteCameraEvents,
        boolean deleteCameraJob,
        String requiredConfirmation
) {
    public static final String CONFIRMATION = "DELETE_CAMERA_JOB";

    public CameraJobDeletionRequest {
        requiredConfirmation = requiredConfirmation == null ? "" : requiredConfirmation.trim();
    }

    public static CameraJobDeletionRequest safeDefault(String requiredConfirmation) {
        return new CameraJobDeletionRequest(
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                requiredConfirmation);
    }

    public void requireConfirmed() {
        if (!CONFIRMATION.equals(requiredConfirmation)) {
            throw new IllegalArgumentException("requiredConfirmation must be DELETE_CAMERA_JOB");
        }
    }
}
