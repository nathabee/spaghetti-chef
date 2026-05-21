package printerhub.camera;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import printerhub.OperationMessages;
import printerhub.PrinterHubLog;
import printerhub.job.PrintJob;
import printerhub.persistence.CameraArchiveEntry;
import printerhub.persistence.CameraArchiveEntryStore;
import printerhub.persistence.CameraEventStore;
import printerhub.persistence.CameraSettings;
import printerhub.persistence.CameraSnapshotMetadata;
import printerhub.persistence.CameraSnapshotMetadataStore;
import printerhub.persistence.PrintJobStore;

public final class CameraCaptureService {

    private final CameraSettingsService settingsService;
    private final CameraEventStore eventStore;
    private final CameraSnapshotMetadataStore snapshotMetadataStore;
    private final Clock clock;
    private final FrameAnalyzer frameAnalyzer;
    private final SpaghettiDetectionService spaghettiDetectionService;
    private final CameraArchiveEntryStore archiveEntryStore;
    private final PrintJobStore printJobStore;

    public CameraCaptureService(
            CameraSettingsService settingsService,
            CameraEventStore eventStore,
            CameraSnapshotMetadataStore snapshotMetadataStore,
            Path storageDirectory) {
        this(
                settingsService,
                eventStore,
                snapshotMetadataStore,
                storageDirectory,
                Clock.systemUTC());
    }

    public CameraCaptureService(
            CameraSettingsService settingsService,
            CameraEventStore eventStore,
            CameraSnapshotMetadataStore snapshotMetadataStore,
            Path storageDirectory,
            Clock clock) {
        this(
                settingsService,
                eventStore,
                snapshotMetadataStore,
                storageDirectory,
                clock,
                new ImageDeltaFrameAnalyzer(),
                new SpaghettiDetectionService(),
                new CameraArchiveEntryStore(),
                new PrintJobStore());
    }

    public CameraCaptureService(
            CameraSettingsService settingsService,
            CameraEventStore eventStore,
            CameraSnapshotMetadataStore snapshotMetadataStore,
            Path storageDirectory,
            Clock clock,
            FrameAnalyzer frameAnalyzer,
            SpaghettiDetectionService spaghettiDetectionService) {
        this(
                settingsService,
                eventStore,
                snapshotMetadataStore,
                storageDirectory,
                clock,
                frameAnalyzer,
                spaghettiDetectionService,
                new CameraArchiveEntryStore(),
                new PrintJobStore());
    }

    public CameraCaptureService(
            CameraSettingsService settingsService,
            CameraEventStore eventStore,
            CameraSnapshotMetadataStore snapshotMetadataStore,
            Path storageDirectory,
            Clock clock,
            FrameAnalyzer frameAnalyzer,
            SpaghettiDetectionService spaghettiDetectionService,
            CameraArchiveEntryStore archiveEntryStore,
            PrintJobStore printJobStore) {
        this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore");
        this.snapshotMetadataStore = Objects.requireNonNull(snapshotMetadataStore, "snapshotMetadataStore");
        Objects.requireNonNull(storageDirectory, "storageDirectory");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.frameAnalyzer = Objects.requireNonNull(frameAnalyzer, "frameAnalyzer");
        this.spaghettiDetectionService = Objects.requireNonNull(spaghettiDetectionService, "spaghettiDetectionService");
        this.archiveEntryStore = Objects.requireNonNull(archiveEntryStore, "archiveEntryStore");
        this.printJobStore = Objects.requireNonNull(printJobStore, "printJobStore");
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
                eventStore.record(
                        settings.printerId(),
                        OperationMessages.EVENT_CAMERA_AVAILABLE,
                        OperationMessages.CAMERA_AVAILABLE);

                return CameraStatus.available(
                        settings.printerId(),
                        settings.sourceType(),
                        settings.sourceValue().orElse(null),
                        device.describe(),
                        lastCaptureAt.orElse(null));
            }

            eventStore.record(
                    settings.printerId(),
                    OperationMessages.EVENT_CAMERA_UNAVAILABLE,
                    OperationMessages.CAMERA_UNAVAILABLE);

            return CameraStatus.unavailable(
                    settings.printerId(),
                    settings.sourceType(),
                    settings.sourceValue().orElse(null),
                    device.describe(),
                    OperationMessages.CAMERA_UNAVAILABLE);
        }
    }

    public CameraCaptureResult capture(String printerId) {
        CameraSettings settings = settingsService.load(requirePrinterId(printerId));
        PrinterHubLog.info("Camera capture requested printerId=" + settings.printerId()
                + " enabled=" + settings.enabled()
                + " sourceType=" + settings.sourceType().wireValue()
                + " sourceValue=" + settings.sourceValue().orElse("")
                + " ffmpegCommand=" + settings.ffmpegCommand()
                + " ffmpegInputFormat=" + settings.ffmpegInputFormat().orElse("")
                + " ffmpegVideoSize=" + settings.ffmpegVideoSize().orElse("")
                + " ffmpegTimeoutMs=" + settings.ffmpegTimeoutMs()
                + " ffmpegJpegQuality=" + settings.ffmpegJpegQuality()
                + " storageDirectory=" + CameraStoragePaths.resolveBaseDirectory(settings.storageDirectory()));

        if (!settings.enabled()) {
            eventStore.record(
                    settings.printerId(),
                    OperationMessages.EVENT_CAMERA_CAPTURE_SKIPPED,
                    OperationMessages.CAMERA_DISABLED);

            return CameraCaptureResult.skipped(OperationMessages.CAMERA_DISABLED);
        }

        try (CameraDevice device = createDevice(settings)) {
            PrinterHubLog.info("Camera capture device printerId=" + settings.printerId()
                    + " description=" + device.describe()
                    + " available=" + device.isAvailable());

            if (!device.isAvailable()) {
                eventStore.record(
                        settings.printerId(),
                        OperationMessages.EVENT_CAMERA_CAPTURE_FAILED,
                        OperationMessages.CAMERA_UNAVAILABLE);

                return CameraCaptureResult.failed(OperationMessages.CAMERA_UNAVAILABLE);
            }

            Optional<CameraFrame> frame = device.captureFrame();

            if (frame.isEmpty()) {
                PrinterHubLog.error("Camera capture returned no frame printerId="
                        + settings.printerId()
                        + " device=" + device.describe());
                eventStore.record(
                        settings.printerId(),
                        OperationMessages.EVENT_CAMERA_CAPTURE_FAILED,
                        OperationMessages.CAMERA_RETURNED_NO_FRAME);

                return CameraCaptureResult.failed(OperationMessages.CAMERA_RETURNED_NO_FRAME);
            }

            PersistedCameraFramePaths persistedPaths = persistFrame(settings, frame.get());
            PrinterHubLog.info("Camera capture persisted printerId=" + settings.printerId()
                    + " latest=" + persistedPaths.latestPath()
                    + " snapshot=" + persistedPaths.snapshotPath());

            eventStore.record(
                    settings.printerId(),
                    OperationMessages.EVENT_CAMERA_FRAME_CAPTURED,
                    OperationMessages.CAMERA_FRAME_CAPTURED);

            analyzeCapturedFrame(settings, persistedPaths);

            return CameraCaptureResult.captured(frame.get());
        } catch (RuntimeException exception) {
            PrinterHubLog.error("Camera capture failed printerId=" + settings.printerId()
                    + ": " + OperationMessages.safeDetail(exception.getMessage(), OperationMessages.UNKNOWN_API_ERROR));
            eventStore.record(
                    settings.printerId(),
                    OperationMessages.EVENT_CAMERA_CAPTURE_FAILED,
                    OperationMessages.cameraCaptureFailed(exception.getMessage()));

            return CameraCaptureResult.failed(OperationMessages.cameraCaptureFailed(exception.getMessage()));
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

        if (settings.sourceType() == CameraSourceType.FFMPEG) {
            Optional<String> sourceValue = settings.sourceValue();

            if (sourceValue.isEmpty()) {
                return new NoopCameraDevice("ffmpeg-camera-missing-source");
            }

            return new FfmpegCameraDevice(
                    settings.printerId(),
                    sourceValue.get(),
                    settings.ffmpegCommand(),
                    settings.ffmpegInputFormat().orElse(null),
                    settings.ffmpegVideoSize().orElse(null),
                    settings.ffmpegTimeoutMs(),
                    settings.ffmpegJpegQuality(),
                    clock);
        }

        return new NoopCameraDevice("unsupported-camera-source:" + settings.sourceType().wireValue());
    }

    private PersistedCameraFramePaths persistFrame(CameraSettings settings, CameraFrame frame) {
        Path printerDirectory = CameraStoragePaths
                .resolveBaseDirectory(settings.storageDirectory())
                .resolve(safePathSegment(frame.printerId()));
        Path snapshotsDirectory = printerDirectory.resolve("snapshots");
        Optional<String> activeJobId = activePrintJobId(settings.printerId());
        String archiveJobSegment = safePathSegment(activeJobId.orElse("unassigned"));
        Path archiveDirectory = printerDirectory.resolve("archive").resolve(archiveJobSegment);

        String extension = extensionFor(frame.contentType());
        String fileName = safeTimestamp(frame.capturedAt()) + extension;
        String archiveFileName = safeTimestamp(frame.capturedAt()) + "_"
                + archiveJobSegment + extension;

        Path snapshotPath = snapshotsDirectory.resolve(fileName);
        Path archivePath = archiveDirectory.resolve(archiveFileName);
        Path latestPath = printerDirectory.resolve("latest" + extension);
        Path previousPath = printerDirectory.resolve("previous" + extension);
        Path deltaPath = printerDirectory.resolve("delta.jpg");

        try {
            Files.createDirectories(snapshotsDirectory);
            Files.createDirectories(archiveDirectory);
            Files.createDirectories(printerDirectory);

            if (Files.isRegularFile(latestPath)) {
                Files.copy(latestPath, previousPath, StandardCopyOption.REPLACE_EXISTING);
            }

            Files.write(snapshotPath, frame.bytes());
            Files.write(archivePath, frame.bytes());
            Files.write(latestPath, frame.bytes());
            enforceSnapshotRetention(snapshotsDirectory, settings.retentionSnapshotCount());

            snapshotMetadataStore.save(CameraSnapshotMetadata.newSnapshot(
                    frame.printerId(),
                    frame.capturedAt(),
                    frame.contentType(),
                    snapshotPath.toString(),
                    frame.width().isPresent() ? frame.width().getAsInt() : null,
                    frame.height().isPresent() ? frame.height().getAsInt() : null,
                    frame.sourceDescription().orElse(null)));
            archiveEntryStore.save(CameraArchiveEntry.captured(
                    frame.printerId(),
                    activeJobId.orElse(null),
                    archivePath.toString(),
                    frame.contentType(),
                    Files.size(archivePath),
                    frame.capturedAt(),
                    clock.instant(),
                    settings.sourceType().wireValue(),
                    frame.sourceDescription().orElse(null)));

            return new PersistedCameraFramePaths(
                    snapshotPath,
                    latestPath,
                    previousPath,
                    deltaPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist camera frame", exception);
        }
    }

    private Optional<String> activePrintJobId(String printerId) {
        try {
            return printJobStore
                    .findActivePrintFileJobByPrinterId(printerId)
                    .map(PrintJob::id);
        } catch (RuntimeException exception) {
            PrinterHubLog.error("Camera archive could not resolve active print job printerId="
                    + printerId
                    + ": "
                    + OperationMessages.safeDetail(exception.getMessage(), OperationMessages.UNKNOWN_API_ERROR));
            return Optional.empty();
        }
    }

    private static void enforceSnapshotRetention(Path snapshotsDirectory, int retainedSnapshots) throws IOException {
        if (retainedSnapshots < 1 || !Files.isDirectory(snapshotsDirectory)) {
            return;
        }

        List<Path> snapshots = new ArrayList<>();
        try (var paths = Files.list(snapshotsDirectory)) {
            paths
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(CameraCaptureService::lastModified))
                    .forEach(snapshots::add);
        }

        int deleteCount = snapshots.size() - retainedSnapshots;
        for (int index = 0; index < deleteCount; index++) {
            Files.deleteIfExists(snapshots.get(index));
        }
    }

    private static Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException exception) {
            return Instant.EPOCH;
        }
    }

    private void analyzeCapturedFrame(CameraSettings settings, PersistedCameraFramePaths persistedPaths) {
        if (!settings.analysisEnabled()) {
            return;
        }

        try {
            FrameAnalysisResult analysisResult = frameAnalyzer.analyze(
                    settings.printerId(),
                    persistedPaths.previousPath(),
                    persistedPaths.latestPath(),
                    Optional.of(persistedPaths.deltaPath()));

            if (!analysisResult.completed()) {
                eventStore.record(
                        settings.printerId(),
                        OperationMessages.EVENT_CAMERA_ANALYSIS_SKIPPED,
                        analysisMessage(OperationMessages.CAMERA_ANALYSIS_SKIPPED, analysisResult));

                return;
            }

            eventStore.record(
                    settings.printerId(),
                    OperationMessages.EVENT_CAMERA_ANALYSIS_COMPLETED,
                    analysisMessage(OperationMessages.CAMERA_ANALYSIS_COMPLETED, analysisResult));

            SpaghettiDetectionResult detectionResult = spaghettiDetectionService.detect(analysisResult);

            if (detectionResult.suspected()) {
                eventStore.record(
                        settings.printerId(),
                        OperationMessages.EVENT_SPAGHETTI_SUSPECTED,
                        detectionMessage(detectionResult),
                        detectionResult.confidence());
            }
        } catch (RuntimeException exception) {
            eventStore.record(
                    settings.printerId(),
                    OperationMessages.EVENT_CAMERA_ANALYSIS_FAILED,
                    OperationMessages.cameraAnalysisFailed(exception.getMessage()));
        }
    }

    private static String analysisMessage(String prefix, FrameAnalysisResult result) {
        return OperationMessages.cameraAnalysisMessage(
                prefix,
                formatRatio(result.deltaScore()),
                formatRatio(result.changedPixelRatio()),
                formatRatio(result.averagePixelDelta()),
                result.reasons().toString());
    }

    private static String detectionMessage(SpaghettiDetectionResult result) {
        return OperationMessages.spaghettiDetectionMessage(
                result.message().orElse(OperationMessages.POSSIBLE_SPAGHETTI_FAILURE_DETECTED),
                formatRatio(result.confidence()),
                result.reasons().toString());
    }

    private static String formatRatio(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
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

    private record PersistedCameraFramePaths(
            Path snapshotPath,
            Path latestPath,
            Path previousPath,
            Path deltaPath) {
    }
}
