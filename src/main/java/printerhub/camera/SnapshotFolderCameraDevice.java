package printerhub.camera;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

public final class SnapshotFolderCameraDevice implements CameraDevice {

    private final String printerId;
    private final Path folder;
    private final Clock clock;
    private boolean closed;

    public SnapshotFolderCameraDevice(String printerId, Path folder) {
        this(printerId, folder, Clock.systemUTC());
    }

    public SnapshotFolderCameraDevice(String printerId, Path folder, Clock clock) {
        this.printerId = requireText(printerId, "printerId");
        this.folder = Objects.requireNonNull(folder, "folder");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<CameraFrame> captureFrame() {
        if (closed || !isAvailable()) {
            return Optional.empty();
        }

        Optional<Path> latestImage = findLatestSupportedImage();
        if (latestImage.isEmpty()) {
            return Optional.empty();
        }

        Path imagePath = latestImage.get();

        try {
            byte[] bytes = Files.readAllBytes(imagePath);
            String contentType = contentTypeFor(imagePath);
            ImageDimensions dimensions = readDimensions(imagePath);
            Instant capturedAt = Instant.now(clock);

            return Optional.of(new CameraFrame(
                    printerId,
                    capturedAt,
                    contentType,
                    bytes,
                    dimensions.width(),
                    dimensions.height(),
                    describe() + ":" + imagePath.getFileName()));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    @Override
    public boolean isAvailable() {
        return !closed && Files.isDirectory(folder) && Files.isReadable(folder);
    }

    @Override
    public String describe() {
        return "snapshot-folder:" + folder.toAbsolutePath().normalize();
    }

    @Override
    public void close() {
        closed = true;
    }

    private Optional<Path> findLatestSupportedImage() {
        try (Stream<Path> stream = Files.list(folder)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(SnapshotFolderCameraDevice::isSupportedImage)
                    .max(Comparator.comparingLong(SnapshotFolderCameraDevice::lastModifiedMillis));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private static boolean isSupportedImage(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".jpg")
                || fileName.endsWith(".jpeg")
                || fileName.endsWith(".png");
    }

    private static String contentTypeFor(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".png")) {
            return "image/png";
        }
        return "image/jpeg";
    }

    private static long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return Long.MIN_VALUE;
        }
    }

    private static ImageDimensions readDimensions(Path imagePath) {
        try {
            BufferedImage image = ImageIO.read(imagePath.toFile());
            if (image == null) {
                return new ImageDimensions(null, null);
            }
            return new ImageDimensions(image.getWidth(), image.getHeight());
        } catch (IOException exception) {
            return new ImageDimensions(null, null);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static final class ImageDimensions {

        private final Integer width;
        private final Integer height;

        private ImageDimensions(Integer width, Integer height) {
            this.width = width;
            this.height = height;
        }

        private Integer width() {
            return width;
        }

        private Integer height() {
            return height;
        }
    }
}