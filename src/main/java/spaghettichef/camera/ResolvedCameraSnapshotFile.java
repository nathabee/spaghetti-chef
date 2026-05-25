package spaghettichef.camera;

import java.nio.file.Path;
import java.time.Instant;

public record ResolvedCameraSnapshotFile(
        Path path,
        String contentType,
        long sizeBytes,
        Instant modifiedAt) {
}
