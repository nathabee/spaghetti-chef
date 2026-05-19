package printerhub.camera;

import printerhub.persistence.CameraAnalysisSample;
import printerhub.persistence.CameraAnalysisSampleStore;
import printerhub.persistence.CameraAnalysisSession;
import printerhub.persistence.CameraAnalysisSessionState;
import printerhub.persistence.CameraAnalysisSessionStore;
import printerhub.persistence.CameraSnapshotMetadata;
import printerhub.persistence.CameraSnapshotMetadataStore;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public final class CameraAnalysisSessionService {

    private final CameraCaptureService captureService;
    private final CameraSnapshotMetadataStore snapshotMetadataStore;
    private final CameraAnalysisSessionStore sessionStore;
    private final CameraAnalysisSampleStore sampleStore;
    private final FrameAnalyzer frameAnalyzer;
    private final SpaghettiDetectionService spaghettiDetectionService;
    private final Path storageDirectory;
    private final Clock clock;
    private final CameraSafetyDecisionService safetyDecisionService;

    public CameraAnalysisSessionService(
            CameraCaptureService captureService,
            CameraSnapshotMetadataStore snapshotMetadataStore,
            CameraAnalysisSessionStore sessionStore,
            CameraAnalysisSampleStore sampleStore,
            CameraSafetyDecisionService safetyDecisionService,
            Path storageDirectory) {
        this(
                captureService,
                snapshotMetadataStore,
                sessionStore,
                sampleStore,
                new ImageDeltaFrameAnalyzer(),
                new SpaghettiDetectionService(),
                storageDirectory,
                Clock.systemUTC(),
                safetyDecisionService);
    }

    public CameraAnalysisSessionService(
            CameraCaptureService captureService,
            CameraSnapshotMetadataStore snapshotMetadataStore,
            CameraAnalysisSessionStore sessionStore,
            CameraAnalysisSampleStore sampleStore,
            FrameAnalyzer frameAnalyzer,
            SpaghettiDetectionService spaghettiDetectionService,
            Path storageDirectory,
            Clock clock,
            CameraSafetyDecisionService safetyDecisionService) {
        this.captureService = Objects.requireNonNull(captureService, "captureService");
        this.snapshotMetadataStore = Objects.requireNonNull(snapshotMetadataStore, "snapshotMetadataStore");
        this.sessionStore = Objects.requireNonNull(sessionStore, "sessionStore");
        this.sampleStore = Objects.requireNonNull(sampleStore, "sampleStore");
        this.frameAnalyzer = Objects.requireNonNull(frameAnalyzer, "frameAnalyzer");
        this.spaghettiDetectionService = Objects.requireNonNull(spaghettiDetectionService, "spaghettiDetectionService");
        this.storageDirectory = Objects.requireNonNull(storageDirectory, "storageDirectory");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.safetyDecisionService = Objects.requireNonNull(safetyDecisionService, "safetyDecisionService");
    }

    public CameraAnalysisSession start(String printerId) {
        String normalizedPrinterId = requireText(printerId, "printerId");
        Optional<CameraAnalysisSession> active = sessionStore.findActiveByPrinterId(normalizedPrinterId);

        if (active.isPresent()) {
            return active.get();
        }

        Instant now = Instant.now(clock);
        CameraAnalysisSession session = new CameraAnalysisSession(
                UUID.randomUUID().toString(),
                normalizedPrinterId,
                CameraAnalysisSessionState.RUNNING,
                now,
                null,
                now,
                now,
                "Camera analysis session started");

        sessionStore.save(session);
        captureSample(session.id(), normalizedPrinterId);
        return session;
    }

    public CameraAnalysisSession stop(String printerId, String sessionId) {
        CameraAnalysisSession session = find(printerId, sessionId);
        if (!session.running()) {
            return session;
        }

        Instant now = Instant.now(clock);
        CameraAnalysisSession stopped = new CameraAnalysisSession(
                session.id(),
                session.printerId(),
                CameraAnalysisSessionState.COMPLETED,
                session.startedAt().orElse(null),
                now,
                session.createdAt(),
                now,
                "Camera analysis session stopped");
        return sessionStore.update(stopped);
    }

    public List<CameraAnalysisSession> list(String printerId) {
        return sessionStore.findByPrinterId(printerId, 20);
    }

    public CameraAnalysisSession find(String printerId, String sessionId) {
        return sessionStore.findById(printerId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("camera analysis session not found"));
    }

    public List<CameraAnalysisSample> samples(String printerId, String sessionId) {
        find(printerId, sessionId);
        return sampleStore.findBySession(printerId, sessionId);
    }

    public CameraAnalysisSample captureSample(String sessionId, String printerId) {
        CameraAnalysisSession session = find(printerId, sessionId);
        if (!session.running()) {
            throw new IllegalStateException("camera analysis session is not running");
        }

        CameraCaptureResult captureResult = captureService.capture(printerId);
        if (!captureResult.success()) {
            Instant now = Instant.now(clock);
            CameraAnalysisSample sample = sampleStore.save(new CameraAnalysisSample(
                    null,
                    sessionId,
                    printerId,
                    now,
                    now,
                    null,
                    null,
                    null,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    false,
                    "CAPTURE_FAILED",
                    captureResult.message().orElse("Camera capture failed")));
            safetyDecisionService.evaluate(sample);
            return sample;
        }

        CameraSnapshotMetadata latest = snapshotMetadataStore.findLatestByPrinterId(printerId)
                .orElseThrow(() -> new IllegalStateException("captured camera snapshot metadata was not found"));
        Path latestPath = Path.of(latest.filePath());
        String extension = extensionOf(latestPath);
        Path printerDirectory = storageDirectory.resolve(safePathSegment(printerId));
        Path previousPath = printerDirectory.resolve("previous" + extension);
        Path deltaPath = printerDirectory.resolve("delta.jpg");

        FrameAnalysisResult analysis = frameAnalyzer.analyze(
                printerId,
                previousPath,
                latestPath,
                Optional.of(deltaPath));
        SpaghettiDetectionResult detection = spaghettiDetectionService.detect(analysis);

        String reasons = detection.reasons().stream()
                .map(Enum::name)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining(","));

        CameraAnalysisSample sample = sampleStore.save(new CameraAnalysisSample(
                null,
                sessionId,
                printerId,
                latest.capturedAt(),
                detection.detectedAt(),
                latestPath.toString(),
                previousPath.toString(),
                deltaPath.toString(),
                analysis.deltaScore(),
                analysis.changedPixelRatio(),
                analysis.averagePixelDelta(),
                detection.confidence(),
                detection.suspected(),
                reasons,
                detection.message().or(() -> analysis.message()).orElse(null)));
        safetyDecisionService.evaluate(sample);
        return sample;
    }

    private static String extensionOf(Path path) {
        String name = path.getFileName().toString();
        int dotIndex = name.lastIndexOf('.');
        return dotIndex >= 0 ? name.substring(dotIndex) : ".jpg";
    }

    private static String safePathSegment(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
