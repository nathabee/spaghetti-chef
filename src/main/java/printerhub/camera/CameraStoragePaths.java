package printerhub.camera;

import java.nio.file.Path;
import java.time.Instant;

import printerhub.config.RuntimeDefaults;
import printerhub.persistence.DatabaseConfig;

public final class CameraStoragePaths {

    private CameraStoragePaths() {
    }

    public static Path defaultBaseDirectory() {
        return DatabaseConfig.dataDirectory().resolve(RuntimeDefaults.DEFAULT_CAMERA_STORAGE_DIRECTORY).normalize();
    }

    public static Path resolveBaseDirectory(String configuredStorageDirectory) {
        String selectedDirectory = configuredStorageDirectory == null || configuredStorageDirectory.isBlank()
                ? RuntimeDefaults.DEFAULT_CAMERA_STORAGE_DIRECTORY
                : configuredStorageDirectory.trim();

        Path selectedPath = Path.of(selectedDirectory);
        if (selectedPath.isAbsolute()) {
            return selectedPath.normalize();
        }

        if (isLegacyWorkingDirectoryDefault(selectedDirectory)) {
            return defaultBaseDirectory();
        }

        return DatabaseConfig.dataDirectory().resolve(selectedPath).normalize();
    }

    public static Path printerDirectory(String configuredStorageDirectory, String printerId) {
        return resolveBaseDirectory(configuredStorageDirectory)
                .resolve(safePathSegment(printerId, "printerId"))
                .normalize();
    }

    public static Path snapshotsDirectory(String configuredStorageDirectory, String printerId, long cameraJobId) {
        return printerDirectory(configuredStorageDirectory, printerId)
                .resolve("snapshots")
                .resolve(cameraJobSegment(cameraJobId))
                .normalize();
    }

    public static Path snapshotPath(
            String configuredStorageDirectory,
            String printerId,
            long cameraJobId,
            Instant capturedAt,
            String extension) {
        String normalizedExtension = normalizeExtension(extension);
        String fileName = safeTimestamp(capturedAt) + "_" + cameraJobSegment(cameraJobId) + normalizedExtension;

        return snapshotsDirectory(configuredStorageDirectory, printerId, cameraJobId)
                .resolve(fileName)
                .normalize();
    }

    public static Path deltasDirectory(
            String configuredStorageDirectory,
            String printerId,
            long cameraJobId,
            long deltaSetId) {
        return printerDirectory(configuredStorageDirectory, printerId)
                .resolve("deltas")
                .resolve(cameraJobSegment(cameraJobId))
                .resolve(deltaSetSegment(deltaSetId))
                .normalize();
    }

    public static Path deltaFramePath(
            String configuredStorageDirectory,
            String printerId,
            long cameraJobId,
            long deltaSetId,
            int fromSequence,
            int toSequence) {
        if (fromSequence <= 0 || toSequence <= 0) {
            throw new IllegalArgumentException("delta frame sequence values must be greater than zero");
        }

        String fileName = "%06d_%06d_delta.jpg".formatted(fromSequence, toSequence);
        return deltasDirectory(configuredStorageDirectory, printerId, cameraJobId, deltaSetId)
                .resolve(fileName)
                .normalize();
    }

    private static boolean isLegacyWorkingDirectoryDefault(String value) {
        String normalized = value.replace('\\', '/');
        return "data/camera".equals(normalized);
    }

    private static String cameraJobSegment(long cameraJobId) {
        if (cameraJobId <= 0L) {
            throw new IllegalArgumentException("cameraJobId must be greater than zero");
        }

        return Long.toString(cameraJobId);
    }

    private static String deltaSetSegment(long deltaSetId) {
        if (deltaSetId <= 0L) {
            throw new IllegalArgumentException("deltaSetId must be greater than zero");
        }

        return Long.toString(deltaSetId);
    }

    private static String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            throw new IllegalArgumentException("extension must not be blank");
        }

        String normalized = extension.trim();
        return normalized.startsWith(".") ? normalized : "." + normalized;
    }

    private static String safeTimestamp(Instant instant) {
        if (instant == null) {
            throw new NullPointerException("capturedAt");
        }

        return instant.toString().replace(':', '-');
    }

    private static String safePathSegment(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        String normalized = value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return normalized;
    }
}
