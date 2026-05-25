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
import printerhub.persistence.CameraSnapshotEntry;
import printerhub.persistence.CameraSnapshotEntryStore;
import printerhub.persistence.CameraEventStore;
import printerhub.persistence.CameraSettings;
import printerhub.persistence.CameraSnapshotMetadata;
import printerhub.persistence.CameraSnapshotMetadataStore;
import printerhub.persistence.CameraJob;

public final class CameraCaptureService {

    private final CameraSettingsService settingsService;
    private final CameraEventStore eventStore;
    private final CameraSnapshotMetadataStore snapshotMetadataStore;
    private final Clock clock;
    private final FrameAnalyzer frameAnalyzer;
    private final SpaghettiDetectionService spaghettiDetectionService;
    private final CameraSnapshotEntryStore snapshotEntryStore;
    private final CameraJobService cameraJobService;
    private final CameraLiveDeltaPipelineService liveDeltaPipelineService;

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
                new CameraSnapshotEntryStore(),
                new CameraJobService(),
                new CameraLiveDeltaPipelineService());
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
                new CameraSnapshotEntryStore(),
                new CameraJobService(),
                new CameraLiveDeltaPipelineService());
    }

    public CameraCaptureService(
            CameraSettingsService settingsService,
            CameraEventStore eventStore,
            CameraSnapshotMetadataStore snapshotMetadataStore,
            Path storageDirectory,
            Clock clock,
            FrameAnalyzer frameAnalyzer,
            SpaghettiDetectionService spaghettiDetectionService,
            CameraSnapshotEntryStore snapshotEntryStore,
            CameraJobService cameraJobService) {
        this(
                settingsService,
                eventStore,
                snapshotMetadataStore,
                storageDirectory,
                clock,
                frameAnalyzer,
                spaghettiDetectionService,
                snapshotEntryStore,
                cameraJobService,
                new CameraLiveDeltaPipelineService());
    }

    public CameraCaptureService(
            CameraSettingsService settingsService,
            CameraEventStore eventStore,
            CameraSnapshotMetadataStore snapshotMetadataStore,
            Path storageDirectory,
            Clock clock,
            FrameAnalyzer frameAnalyzer,
            SpaghettiDetectionService spaghettiDetectionService,
            CameraSnapshotEntryStore snapshotEntryStore,
            CameraJobService cameraJobService,
            CameraLiveDeltaPipelineService liveDeltaPipelineService) {
        this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore");
        this.snapshotMetadataStore = Objects.requireNonNull(snapshotMetadataStore, "snapshotMetadataStore");
        Objects.requireNonNull(storageDirectory, "storageDirectory");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.frameAnalyzer = Objects.requireNonNull(frameAnalyzer, "frameAnalyzer");
        this.spaghettiDetectionService = Objects.requireNonNull(spaghettiDetectionService, "spaghettiDetectionService");
        this.snapshotEntryStore = Objects.requireNonNull(snapshotEntryStore, "snapshotEntryStore");
        this.cameraJobService = Objects.requireNonNull(cameraJobService, "cameraJobService");
        this.liveDeltaPipelineService = Objects.requireNonNull(liveDeltaPipelineService, "liveDeltaPipelineService");
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
        CameraJob cameraJob = cameraJobService.start(settings);
        return captureForCameraJob(settings, cameraJob);
    }

    public CameraCaptureResult captureForCameraJob(String printerId, long cameraJobId) {
        CameraSettings settings = settingsService.load(requirePrinterId(printerId));
        CameraJob cameraJob = cameraJobService.findById(cameraJobId)
                .orElseThrow(() -> new IllegalStateException("Camera job not found: " + cameraJobId));

        if (!settings.printerId().equals(cameraJob.printerId())) {
            throw new IllegalStateException("Camera job " + cameraJobId
                    + " belongs to printer " + cameraJob.printerId()
                    + " but capture was requested for " + settings.printerId());
        }

        if (!"RUNNING".equalsIgnoreCase(cameraJob.state().name())) {
            throw new IllegalStateException("Camera job " + cameraJobId
                    + " is not running: " + cameraJob.state().name());
        }

        return captureForCameraJob(settings, cameraJob);
    }

    private CameraCaptureResult captureForCameraJob(CameraSettings settings, CameraJob cameraJob) {
        if (settings.diagnosticLoggingEnabled()) {
            PrinterHubLog.info("Camera capture requested printerId=" + settings.printerId()
                    + " cameraJobId=" + cameraJob.requireId()
                    + " enabled=" + settings.enabled()
                    + " sourceType=" + settings.sourceType().wireValue()
                    + " sourceValue=" + settings.sourceValue().orElse("")
                    + " ffmpegCommand=" + settings.ffmpegCommand()
                    + " ffmpegInputFormat=" + settings.ffmpegInputFormat().orElse("")
                    + " ffmpegVideoSize=" + settings.ffmpegVideoSize().orElse("")
                    + " ffmpegTimeoutMs=" + settings.ffmpegTimeoutMs()
                    + " ffmpegJpegQuality=" + settings.ffmpegJpegQuality()
                    + " storageDirectory=" + CameraStoragePaths.resolveBaseDirectory(settings.storageDirectory()));
        }

        if (!settings.enabled()) {
            eventStore.record(
                    settings.printerId(),
                    OperationMessages.EVENT_CAMERA_CAPTURE_SKIPPED,
                    OperationMessages.CAMERA_DISABLED);

            return CameraCaptureResult.skipped(OperationMessages.CAMERA_DISABLED);
        }

        try (CameraDevice device = createDevice(settings)) {
            if (settings.diagnosticLoggingEnabled()) {
                PrinterHubLog.info("Camera capture device printerId=" + settings.printerId()
                        + " cameraJobId=" + cameraJob.requireId()
                        + " description=" + device.describe()
                        + " available=" + device.isAvailable());
            }

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
                        + " cameraJobId=" + cameraJob.requireId()
                        + " device=" + device.describe());
                eventStore.record(
                        settings.printerId(),
                        OperationMessages.EVENT_CAMERA_CAPTURE_FAILED,
                        OperationMessages.CAMERA_RETURNED_NO_FRAME);

                return CameraCaptureResult.failed(OperationMessages.CAMERA_RETURNED_NO_FRAME);
            }

            PersistedCameraFramePaths persistedPaths = persistFrame(settings, frame.get(), cameraJob);

            if (settings.diagnosticLoggingEnabled()) {
                PrinterHubLog.info("Camera capture persisted printerId=" + settings.printerId()
                        + " cameraJobId=" + cameraJob.requireId()
                        + " latest=" + persistedPaths.latestPath()
                        + " snapshot=" + persistedPaths.snapshotPath());
            }

            eventStore.record(
                    settings.printerId(),
                    OperationMessages.EVENT_CAMERA_FRAME_CAPTURED,
                    OperationMessages.CAMERA_FRAME_CAPTURED);

            analyzeCapturedFrame(settings, persistedPaths);
            processLiveDeltaPipeline(settings, persistedPaths.cameraJobId());

            return CameraCaptureResult.captured(frame.get());
        } catch (RuntimeException exception) {
            PrinterHubLog.error("Camera capture failed printerId=" + settings.printerId()
                    + " cameraJobId=" + cameraJob.requireId()
                    + ": " + OperationMessages.safeDetail(exception.getMessage(), OperationMessages.UNKNOWN_API_ERROR));
            eventStore.record(
                    settings.printerId(),
                    OperationMessages.EVENT_CAMERA_CAPTURE_FAILED,
                    OperationMessages.cameraCaptureFailed(exception.getMessage()));

            return CameraCaptureResult.failed(OperationMessages.cameraCaptureFailed(exception.getMessage()));
        }
    }

    public CameraCaptureResult captureDiagnostic(String printerId) {
        CameraSettings settings = settingsService.load(requirePrinterId(printerId));

        if (settings.diagnosticLoggingEnabled()) {
            PrinterHubLog.info("Camera diagnostic capture requested printerId=" + settings.printerId()
                    + " enabled=" + settings.enabled()
                    + " sourceType=" + settings.sourceType().wireValue()
                    + " sourceValue=" + settings.sourceValue().orElse("")
                    + " storageDirectory=" + CameraStoragePaths.resolveBaseDirectory(settings.storageDirectory()));
        }

        if (!settings.enabled()) {
            eventStore.record(
                    settings.printerId(),
                    OperationMessages.EVENT_CAMERA_CAPTURE_SKIPPED,
                    OperationMessages.CAMERA_DISABLED);

            return CameraCaptureResult.skipped(OperationMessages.CAMERA_DISABLED);
        }

        Optional<CameraJob> activeJob = cameraJobService.findActive(settings.printerId());
        if (activeJob.isPresent()) {
            String message = "Camera job is already active; diagnostic capture skipped.";
            eventStore.record(
                    settings.printerId(),
                    OperationMessages.EVENT_CAMERA_CAPTURE_SKIPPED,
                    message);

            return CameraCaptureResult.skipped(message);
        }

        try (CameraDevice device = createDevice(settings)) {
            if (settings.diagnosticLoggingEnabled()) {
                PrinterHubLog.info("Camera diagnostic capture device printerId=" + settings.printerId()
                        + " description=" + device.describe()
                        + " available=" + device.isAvailable());
            }

            if (!device.isAvailable()) {
                eventStore.record(
                        settings.printerId(),
                        OperationMessages.EVENT_CAMERA_CAPTURE_FAILED,
                        OperationMessages.CAMERA_UNAVAILABLE);

                return CameraCaptureResult.failed(OperationMessages.CAMERA_UNAVAILABLE);
            }

            Optional<CameraFrame> frame = device.captureFrame();

            if (frame.isEmpty()) {
                PrinterHubLog.error("Camera diagnostic capture returned no frame printerId="
                        + settings.printerId()
                        + " device=" + device.describe());
                eventStore.record(
                        settings.printerId(),
                        OperationMessages.EVENT_CAMERA_CAPTURE_FAILED,
                        OperationMessages.CAMERA_RETURNED_NO_FRAME);

                return CameraCaptureResult.failed(OperationMessages.CAMERA_RETURNED_NO_FRAME);
            }

            PersistedDiagnosticFramePaths persistedPaths = persistDiagnosticFrame(settings, frame.get());

            if (settings.diagnosticLoggingEnabled()) {
                PrinterHubLog.info("Camera diagnostic capture persisted printerId=" + settings.printerId()
                        + " latest=" + persistedPaths.latestPath());
            }

            eventStore.record(
                    settings.printerId(),
                    OperationMessages.EVENT_CAMERA_FRAME_CAPTURED,
                    "Camera diagnostic frame captured");

            updateDiagnosticDelta(settings, persistedPaths);

            return CameraCaptureResult.captured(frame.get());
        } catch (RuntimeException exception) {
            PrinterHubLog.error("Camera diagnostic capture failed printerId=" + settings.printerId()
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
                    settings.diagnosticLoggingEnabled(),
                    clock);
        }

        return new NoopCameraDevice("unsupported-camera-source:" + settings.sourceType().wireValue());
    }

    private PersistedCameraFramePaths persistFrame(CameraSettings settings, CameraFrame frame, CameraJob cameraJob) {

        String extension = extensionFor(frame.contentType());
        Path printerDirectory = CameraStoragePaths.printerDirectory(settings.storageDirectory(), frame.printerId());
        Path snapshotsDirectory = CameraStoragePaths
                .snapshotsDirectory(settings.storageDirectory(), frame.printerId(), cameraJob.requireId());
        Path latestPath = printerDirectory.resolve("latest" + extension);
        Path previousPath = printerDirectory.resolve("previous" + extension);
        Path deltaPath = printerDirectory.resolve("delta.jpg");

        try {
            Files.createDirectories(printerDirectory);
            Files.createDirectories(snapshotsDirectory);

            if (Files.isRegularFile(latestPath)) {
                Files.copy(latestPath, previousPath, StandardCopyOption.REPLACE_EXISTING);
            }

            Path pendingSnapshotPath = Files.createTempFile(snapshotsDirectory, "pending-snapshot-", extension);
            Files.write(pendingSnapshotPath, frame.bytes());
            Files.write(latestPath, frame.bytes());

            CameraSnapshotEntry snapshotEntry = snapshotEntryStore.save(CameraSnapshotEntry.captured(
                    frame.printerId(),
                    cameraJob.requireId(),
                    cameraJob.linkedPrintJobId().orElse(null),
                    pendingSnapshotPath.toString(),
                    frame.contentType(),
                    Files.size(pendingSnapshotPath),
                    frame.capturedAt(),
                    clock.instant(),
                    settings.sourceType().wireValue(),
                    frame.sourceDescription().orElse(null)));
            long snapshotEntryId = requireSnapshotEntryId(snapshotEntry);
            Path snapshotPath = CameraStoragePaths.snapshotPathForEntryId(
                    settings.storageDirectory(),
                    frame.printerId(),
                    cameraJob.requireId(),
                    snapshotEntryId,
                    extension);
            Files.move(pendingSnapshotPath, snapshotPath, StandardCopyOption.REPLACE_EXISTING);
            snapshotEntry = snapshotEntryStore.updateSnapshotPath(snapshotEntryId, snapshotPath.toString());

            enforceSnapshotRetention(snapshotsDirectory, settings.retentionSnapshotCount());

            snapshotMetadataStore.save(CameraSnapshotMetadata.newSnapshot(
                    frame.printerId(),
                    frame.capturedAt(),
                    frame.contentType(),
                    snapshotPath.toString(),
                    frame.width().isPresent() ? frame.width().getAsInt() : null,
                    frame.height().isPresent() ? frame.height().getAsInt() : null,
                    frame.sourceDescription().orElse(null)));

            return new PersistedCameraFramePaths(
                    cameraJob.requireId(),
                    snapshotEntry,
                    snapshotPath,
                    latestPath,
                    previousPath,
                    deltaPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist camera frame", exception);
        }
    }

    private PersistedDiagnosticFramePaths persistDiagnosticFrame(CameraSettings settings, CameraFrame frame) {
        String extension = extensionFor(frame.contentType());
        Path printerDirectory = CameraStoragePaths.printerDirectory(settings.storageDirectory(), frame.printerId());
        Path latestPath = printerDirectory.resolve("latest" + extension);
        Path previousPath = printerDirectory.resolve("previous" + extension);
        Path deltaPath = printerDirectory.resolve("delta.jpg");

        try {
            Files.createDirectories(printerDirectory);

            Path pendingDiagnosticPath = Files.createTempFile(printerDirectory, "pending-diagnostic-", extension);
            Files.write(pendingDiagnosticPath, frame.bytes());

            if (Files.isRegularFile(latestPath)) {
                Files.copy(latestPath, previousPath, StandardCopyOption.REPLACE_EXISTING);
            }

            Files.move(pendingDiagnosticPath, latestPath, StandardCopyOption.REPLACE_EXISTING);

            snapshotMetadataStore.save(CameraSnapshotMetadata.newSnapshot(
                    frame.printerId(),
                    frame.capturedAt(),
                    frame.contentType(),
                    latestPath.toString(),
                    frame.width().isPresent() ? frame.width().getAsInt() : null,
                    frame.height().isPresent() ? frame.height().getAsInt() : null,
                    frame.sourceDescription().orElse(null)));

            return new PersistedDiagnosticFramePaths(
                    latestPath,
                    previousPath,
                    deltaPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist diagnostic camera frame", exception);
        }
    }

    private void updateDiagnosticDelta(CameraSettings settings, PersistedDiagnosticFramePaths persistedPaths) {
        if (!Files.isRegularFile(persistedPaths.previousPath())
                || !Files.isRegularFile(persistedPaths.latestPath())) {
            return;
        }

        try {
            frameAnalyzer.analyze(
                    settings.printerId(),
                    persistedPaths.previousPath(),
                    persistedPaths.latestPath(),
                    Optional.of(persistedPaths.deltaPath()));
        } catch (RuntimeException exception) {
            eventStore.record(
                    settings.printerId(),
                    OperationMessages.EVENT_CAMERA_ANALYSIS_FAILED,
                    "Diagnostic delta update failed: " + exception.getMessage());
        }
    }

    private static long requireSnapshotEntryId(CameraSnapshotEntry snapshotEntry) {
        if (snapshotEntry.id() == null || snapshotEntry.id() <= 0L) {
            throw new IllegalStateException("camera snapshot entry id is required");
        }

        return snapshotEntry.id();
    }

    private void processLiveDeltaPipeline(CameraSettings settings, long cameraJobId) {
        if (!settings.analysisEnabled()) {
            return;
        }

        try {
            liveDeltaPipelineService.processLatestSnapshot(settings, cameraJobId);
        } catch (RuntimeException exception) {
            eventStore.record(
                    settings.printerId(),
                    OperationMessages.EVENT_CAMERA_ANALYSIS_FAILED,
                    OperationMessages.cameraAnalysisFailed(exception.getMessage()));
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

    private static String requirePrinterId(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException("printerId must not be blank");
        }

        return printerId.trim();
    }

    private record PersistedCameraFramePaths(
            long cameraJobId,
            CameraSnapshotEntry snapshotEntry,
            Path snapshotPath,
            Path latestPath,
            Path previousPath,
            Path deltaPath) {
    }

    private record PersistedDiagnosticFramePaths(
            Path latestPath,
            Path previousPath,
            Path deltaPath) {
    }

}
