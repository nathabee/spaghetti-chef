package spaghettichef.camera;

import java.util.List;

public record CameraStorageSyncReport(
        String printerId,
        String storageRoot,
        boolean dryRun,
        int scannedJobs,
        int scannedSnapshotFiles,
        int scannedDeltaSetFolders,
        int scannedDeltaFiles,
        int createdCameraJobs,
        int createdSnapshotRows,
        int reactivatedSnapshotRows,
        int deletedSnapshotRows,
        int createdDeltaSets,
        int updatedDeltaSets,
        int createdDeltaFrameRows,
        int deletedDeltaFrameRows,
        List<String> warnings
) {
    public CameraStorageSyncReport {
        warnings = List.copyOf(warnings);
    }
}
