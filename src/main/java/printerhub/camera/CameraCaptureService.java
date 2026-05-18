package printerhub.camera;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import printerhub.persistence.CameraEventStore;
import printerhub.persistence.CameraSettings;
import printerhub.persistence.CameraSnapshotMetadata;
import printerhub.persistence.CameraSnapshotMetadataStore;

public final class CameraCaptureService {

    public static final String EVENT_CAMERA_FRAME_CAPTURED = "CAMERA_FRAME_CAPTURED";
    public static final String EVENT_CAMERA_CAPTURE_SKIPPED = "CAMERA_CAPTURE_SKIPPED";
    public static final String EVENT_CAMERA_CAPTURE_FAILED = "CAMERA_CAPTURE_FAILED";
    public static final String EVENT_CAMERA_AVAILABLE = "CAMERA_AVAILABLE";
    public static final String EVENT_CAMERA_UNAVAILABLE = "CAMERA_UNAVAILABLE";

    private final CameraSettingsService settingsService;
    private final CameraEventStore eventStore;
    private final CameraSnapshotMetadataStore snapshotMetadataStore;
    private final Path storageDirectory;
    private final Clock clock;

    public CameraCaptureService(
            CameraSettingsService settingsService,
            CameraEventStore eventStore,
            CameraSnapshotMetadataStore snapshotMetadataStore,
            Path storageDirectory) {
        this(settingsService, eventStore, snapshotMetadataStore, storageDirectory, Clock.systemUTC());
    }

    public CameraCaptureService(
            CameraSettingsService settingsService,
            CameraEventStore eventStore,
            CameraSnapshotMetadataStore snapshotMetadataStore,
            Path storageDirectory,
            Clock clock) {
        this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore");
        this.snapshotMetadataStore = Objects.requireNonNull(snapshotMetadataStore, "snapshotMetadataStore");
        this.storageDirectory = Objects.requireNonNull(storageDirectory, "storageDirectory");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CameraStatus status(String printerId) {
        CameraSettings settings = settingsService.load(requirePrinterId(printerId));

        if (!settings.enabled()) {
            return CameraStatus.disabled(settings.printerId());
        }

        try (CameraDevice device = createDevice(settings)) {
            Optional<Instant> lastCaptureAt = snapshotMetadataStore
                    .findLatestByPrinterId(settings.printerId())
                    .map(CameraSnapshotMetadata::capturedAt);

            if (device.isAvailable()) {
                safeRecord(settings.printerId(), EVENT_CAMERA_AVAILABLE, "Camera available");
                return CameraStatus.available(
                        settings.printerId(),
                        settings.sourceType(),
                        settings.sourceValue().orElse(null),
                        device.describe(),
                        lastCaptureAt.orElse(null));
            }

            safeRecord(settings.printerId(), EVENT_CAMERA_UNAVAILABLE, "Camera unavailable");
            return CameraStatus.unavailable(
                    settings.printerId(),
                    settings.sourceType(),
                    settings.sourceValue().orElse(null),
                    device.describe(),
                    "Camera unavailable");
        }
    }

    public CameraCaptureResult capture(String printerId) {
        CameraSettings settings = settingsService.load(requirePrinterId(printerId));

        if (!settings.enabled()) {
            safeRecord(settings.printerId(), EVENT_CAMERA_CAPTURE_SKIPPED, "Camera disabled");
            return CameraCaptureResult.skipped("Camera disabled");
        }

        try (CameraDevice device = createDevice(settings)) {
            if (!device.isAvailable()) {
                safeRecord(settings.printerId(), EVENT_CAMERA_CAPTURE_FAILED, "Camera unavailable");
                return CameraCaptureResult.failed("Camera unavailable");
            }

            Optional<CameraFrame> frame = device.captureFrame();

            if (frame.isEmpty()) {
                safeRecord(settings.printerId(), EVENT_CAMERA_CAPTURE_FAILED, "Camera returned no frame");
                return CameraCaptureResult.failed("Camera returned no frame");
            }

            persistFrame(frame.get());
            safeRecord(settings.printerId(), EVENT_CAMERA_FRAME_CAPTURED, "Camera frame captured");

            return CameraCaptureResult.captured(frame.get());
        } catch (RuntimeException exception) {
            safeRecord(settings.printerId(), EVENT_CAMERA_CAPTURE_FAILED, exception.getMessage());
            return CameraCaptureResult.failed("Camera capture failed: " + exception.getMessage());
        }
    }

    private CameraDevice createDevice(CameraSettings settings) {
        if (!settings.enabled()) {
            return new NoopCameraDevice("camera-disabled");
        }

        if (settings.sourceType() == CameraSourceType.SIMULATED) {
            return new SimulatedCameraDevice(settings.printerId(), clock);
        }

        if (settings.sourceType() == CameraSourceType.SNAPSHOT_FOLDER) {
            Optional<String> sourceValue = settings.sourceValue();

            if (sourceValue.isEmpty()) {
                return new NoopCameraDevice("snapshot-folder-camera-missing-source");
            }

            return new SnapshotFolderCameraDevice(
                    settings.printerId(),
                    Path.of(sourceValue.get()),
                    clock);
        }

        return new NoopCameraDevice("unsupported-camera-source:" + settings.sourceType().wireValue());
    }

    private void persistFrame(CameraFrame frame) {
        Path printerDirectory = storageDirectory.resolve(safePathSegment(frame.printerId()));
        Path snapshotsDirectory = printerDirectory.resolve("snapshots");

        String extension = extensionFor(frame.contentType());
        String fileName = safeTimestamp(frame.capturedAt()) + extension;

        Path snapshotPath = snapshotsDirectory.resolve(fileName);
        Path latestPath = printerDirectory.resolve("latest" + extension);

        try {
            Files.createDirectories(snapshotsDirectory);
            Files.write(snapshotPath, frame.bytes());
            Files.write(latestPath, frame.bytes());

            snapshotMetadataStore.save(CameraSnapshotMetadata.newSnapshot(
                    frame.printerId(),
                    frame.capturedAt(),
                    frame.contentType(),
                    snapshotPath.toString(),
                    frame.width().isPresent() ? frame.width().getAsInt() : null,
                    frame.height().isPresent() ? frame.height().getAsInt() : null,
                    frame.sourceDescription().orElse(null)));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist camera frame", exception);
        }
    }

    private void safeRecord(String printerId, String eventType, String message) {
        try {
            eventStore.record(
                    printerId,
                    eventType,
                    message == null || message.isBlank() ? eventType : message);
        } catch (RuntimeException ignored) {
            // Capture must not crash only because event persistence failed.
        }
    }

    private static String extensionFor(String contentType) {
        if ("image/png".equalsIgnoreCase(contentType)) {
            return ".png";
        }

        return ".jpg";
    }

    private static String safeTimestamp(Instant instant) {
        return instant.toString().replaceAll("[^A-Za-z0-9._-]", "-");
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