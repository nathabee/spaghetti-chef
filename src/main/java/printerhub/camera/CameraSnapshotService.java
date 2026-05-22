package printerhub.camera;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import printerhub.persistence.CameraSettings;

public final class CameraSnapshotService {

    private final CameraSettingsService settingsService;

    public CameraSnapshotService(CameraSettingsService settingsService) {
        this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
    }

    public List<CameraSnapshotFile> list(String printerId, Optional<Instant> from, Optional<Instant> to) {
        Path printerDirectory = printerDirectory(printerId);

        if (!Files.isDirectory(printerDirectory)) {
            return List.of();
        }

        List<CameraSnapshotFile> files = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(printerDirectory, 3)) {
            paths
                    .filter(Files::isRegularFile)
                    .filter(path -> normalizeRelativePath(printerDirectory.relativize(path)).startsWith("snapshot/"))
                    .forEach(path -> appendFile(printerDirectory, path, from, to, files));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to list camera snapshot files", exception);
        }

        files.sort(Comparator
                .comparing(CameraSnapshotFile::modifiedAt)
                .reversed()
                .thenComparing(CameraSnapshotFile::relativePath));

        return files;
    }

    public Optional<ResolvedCameraSnapshotFile> resolve(String printerId, String fileId) {
        Path printerDirectory = printerDirectory(printerId);
        Path relativePath = decodeRelativePath(fileId);
        Path resolvedPath = printerDirectory.resolve(relativePath).normalize();
        String normalizedRelativePath = normalizeRelativePath(relativePath);

        if (!normalizedRelativePath.startsWith("snapshot/")
                || !resolvedPath.startsWith(printerDirectory)
                || !Files.isRegularFile(resolvedPath)) {
            return Optional.empty();
        }

        try {
            return Optional.of(new ResolvedCameraSnapshotFile(
                    resolvedPath,
                    contentType(resolvedPath),
                    Files.size(resolvedPath),
                    Files.getLastModifiedTime(resolvedPath).toInstant()));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read camera snapshot file", exception);
        }
    }

    private void appendFile(
            Path printerDirectory,
            Path path,
            Optional<Instant> from,
            Optional<Instant> to,
            List<CameraSnapshotFile> files) {
        try {
            Instant modifiedAt = Files.getLastModifiedTime(path).toInstant();

            if (from.isPresent() && modifiedAt.isBefore(from.get())) {
                return;
            }

            if (to.isPresent() && modifiedAt.isAfter(to.get())) {
                return;
            }

            String relativePath = normalizeRelativePath(printerDirectory.relativize(path));

            files.add(new CameraSnapshotFile(
                    encodeRelativePath(relativePath),
                    fileType(relativePath),
                    path.getFileName().toString(),
                    relativePath,
                    contentType(path),
                    Files.size(path),
                    modifiedAt));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to inspect camera snapshot file", exception);
        }
    }

    private Path printerDirectory(String printerId) {
        String normalizedPrinterId = requirePrinterId(printerId);
        CameraSettings settings = settingsService.load(normalizedPrinterId);

        return CameraStoragePaths
                .resolveBaseDirectory(settings.storageDirectory())
                .resolve(safePathSegment(normalizedPrinterId))
                .normalize();
    }

    private static String encodeRelativePath(String relativePath) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(relativePath.getBytes(StandardCharsets.UTF_8));
    }

    private static Path decodeRelativePath(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            throw new IllegalArgumentException("camera snapshot file id must not be blank");
        }

        try {
            String relativePath = new String(Base64.getUrlDecoder().decode(fileId), StandardCharsets.UTF_8);
            Path path = Path.of(relativePath);

            if (path.isAbsolute() || relativePath.contains("..")) {
                throw new IllegalArgumentException("invalid camera snapshot file id");
            }

            return path.normalize();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid camera snapshot file id", exception);
        }
    }

    private static String normalizeRelativePath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String fileType(String relativePath) {
        if (relativePath.startsWith("snapshot/")) {
            return "snapshot";
        }
        if (relativePath.startsWith("snapshots/")) {
            return "snapshot";
        }
        if (relativePath.startsWith("latest.")) {
            return "latest";
        }
        if (relativePath.startsWith("previous.")) {
            return "previous";
        }
        if ("delta.jpg".equals(relativePath)) {
            return "delta";
        }
        return "other";
    }

    private static String contentType(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);

        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (fileName.endsWith(".png")) {
            return "image/png";
        }
        return "application/octet-stream";
    }

    private static String safePathSegment(String value) {
        String normalized = requirePrinterId(value).replaceAll("[^A-Za-z0-9._-]", "_");

        if (normalized.isBlank()) {
            throw new IllegalArgumentException("printerId must not be blank");
        }

        return normalized;
    }

    private static String requirePrinterId(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException("printerId must not be blank");
        }

        return printerId.trim();
    }
}
