package spaghettichef.camera;

public record CameraDeltaSetDeletionRequest(
        boolean deleteDeltaFiles,
        boolean deleteDeltaRows,
        boolean deleteCalculationRuns,
        String requiredConfirmation
) {
    public static final String CONFIRMATION = "DELETE_DELTA_SET";

    public static CameraDeltaSetDeletionRequest safeDefault(String requiredConfirmation) {
        return new CameraDeltaSetDeletionRequest(
                true,
                true,
                true,
                requiredConfirmation);
    }

    public void requireConfirmed() {
        if (!CONFIRMATION.equals(requiredConfirmation)) {
            throw new IllegalArgumentException("requiredConfirmation must be " + CONFIRMATION);
        }
    }
}
