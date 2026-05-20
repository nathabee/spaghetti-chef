package printerhub.camera;

import java.nio.file.Path;
import java.time.Instant;

public record ResolvedCameraArchiveFile(
        Path path,
        String contentType,
        long sizeBytes,
        Instant modifiedAt) {
}
