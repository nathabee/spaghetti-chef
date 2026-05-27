package spaghettichef.camera;

import java.util.List;

public record CameraDeltaSetDeletionReport(
        String printerId,
        long cameraJobId,
        long deltaSetId,
        int deletedDeltaFiles,
        long deletedDeltaBytes,
        int deletedDeltaRows,
        int deletedDeltaSetRows,
        int deletedCalculationRunRows,
        int deletedCalculationResultRows,
        List<String> failedFiles,
        String message
) {
    public CameraDeltaSetDeletionReport {
        failedFiles = List.copyOf(failedFiles == null ? List.of() : failedFiles);
    }
}
