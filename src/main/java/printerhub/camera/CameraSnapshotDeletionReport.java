package printerhub.camera;

import java.util.List;

public record CameraSnapshotDeletionReport(
        String jobId,
        int deletedFiles,
        long deletedBytes,
        int deletedMetadataRows,
        List<String> failedFiles,
        String message
) {
}
