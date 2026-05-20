package printerhub.camera;

import java.time.Instant;

public record CameraArchiveFile(
        String id,
        String type,
        String fileName,
        String relativePath,
        String contentType,
        long sizeBytes,
        Instant modifiedAt) {
}
