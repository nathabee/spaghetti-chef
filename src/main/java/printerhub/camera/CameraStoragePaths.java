package printerhub.camera;

import java.nio.file.Path;

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

    private static boolean isLegacyWorkingDirectoryDefault(String value) {
        String normalized = value.replace('\\', '/');
        return "data/camera".equals(normalized);
    }
}
