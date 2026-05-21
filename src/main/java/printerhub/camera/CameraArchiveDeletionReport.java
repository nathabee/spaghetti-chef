package printerhub.camera;

import java.util.List;

public record CameraArchiveDeletionReport(
        String jobId,
        int deletedFiles,
        long deletedBytes,
        int deletedMetadataRows,
        List<String> failedFiles,
        String message
) {
}
