package spaghettichef.camera;

import spaghettichef.persistence.CameraAnalysisSample;
import spaghettichef.persistence.CameraAnalysisSampleStore;
import spaghettichef.persistence.CameraAnalysisSession;
import spaghettichef.persistence.CameraAnalysisSessionState;
import spaghettichef.persistence.CameraAnalysisSessionStore;
import spaghettichef.persistence.CameraDeltaFrame;
import spaghettichef.persistence.CameraDeltaFrameStore;
import spaghettichef.persistence.CameraDeltaSet;
import spaghettichef.persistence.CameraDeltaSetStore;
import spaghettichef.persistence.CameraJob;
import spaghettichef.persistence.CameraSnapshotEntry;
import spaghettichef.persistence.CameraSnapshotEntryStore;
import spaghettichef.persistence.CameraSnapshotMetadata;
import spaghettichef.persistence.CameraSnapshotMetadataStore;

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
    private final CameraJobService cameraJobService;
    private final CameraSnapshotEntryStore snapshotEntryStore;
    private final CameraDeltaSetStore deltaSetStore;
    private final CameraDeltaFrameStore deltaFrameStore;

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
                safetyDecisionService,
                new CameraJobService(),
                new CameraSnapshotEntryStore(),
                new CameraDeltaSetStore(),
                new CameraDeltaFrameStore());
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
        this(
                captureService,
                snapshotMetadataStore,
                sessionStore,
                sampleStore,
                frameAnalyzer,
                spaghettiDetectionService,
                storageDirectory,
                clock,
                safetyDecisionService,
                new CameraJobService(),
                new CameraSnapshotEntryStore(),
                new CameraDeltaSetStore(),
                new CameraDeltaFrameStore());
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
            CameraSafetyDecisionService safetyDecisionService,
            CameraJobService cameraJobService,
            CameraSnapshotEntryStore snapshotEntryStore,
            CameraDeltaSetStore deltaSetStore,
            CameraDeltaFrameStore deltaFrameStore) {
        this.captureService = Objects.requireNonNull(captureService, "captureService");
        this.snapshotMetadataStore = Objects.requireNonNull(snapshotMetadataStore, "snapshotMetadataStore");
        this.sessionStore = Objects.requireNonNull(sessionStore, "sessionStore");
        this.sampleStore = Objects.requireNonNull(sampleStore, "sampleStore");
        this.frameAnalyzer = Objects.requireNonNull(frameAnalyzer, "frameAnalyzer");
        this.spaghettiDetectionService = Objects.requireNonNull(spaghettiDetectionService, "spaghettiDetectionService");
        this.storageDirectory = Objects.requireNonNull(storageDirectory, "storageDirectory");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.safetyDecisionService = Objects.requireNonNull(safetyDecisionService, "safetyDecisionService");
        this.cameraJobService = Objects.requireNonNull(cameraJobService, "cameraJobService");
        this.snapshotEntryStore = Objects.requireNonNull(snapshotEntryStore, "snapshotEntryStore");
        this.deltaSetStore = Objects.requireNonNull(deltaSetStore, "deltaSetStore");
        this.deltaFrameStore = Objects.requireNonNull(deltaFrameStore, "deltaFrameStore");
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
        CameraAnalysisSession updated = sessionStore.update(stopped);
        cameraJobService.completeActive(session.printerId(), "Camera job completed when analysis session stopped");
        return updated;
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

    public List<CameraAnalysisSample> recentSamples(String printerId, String sessionId, int limit) {
        find(printerId, sessionId);
        return sampleStore.findRecentBySession(printerId, sessionId, limit);
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

        CameraSnapshotMetadata latestMetadata = snapshotMetadataStore.findLatestByPrinterId(printerId)
                .orElseThrow(() -> new IllegalStateException("captured camera snapshot metadata was not found"));
        CameraJob cameraJob = cameraJobService.findActive(printerId)
                .orElseThrow(() -> new IllegalStateException("active camera job was not found"));
        List<CameraSnapshotEntry> snapshots = snapshotEntryStore.findByPrinterIdAndJobId(
                printerId,
                Long.toString(cameraJob.requireId()));

        if (snapshots.isEmpty()) {
            throw new IllegalStateException("captured camera snapshot entry was not found");
        }

        CameraSnapshotEntry latestEntry = snapshots.get(snapshots.size() - 1);
        Path latestPath = Path.of(latestEntry.snapshotPath());

        if (snapshots.size() < 2) {
            CameraAnalysisSample sample = sampleStore.save(new CameraAnalysisSample(
                    null,
                    sessionId,
                    printerId,
                    latestEntry.capturedAt(),
                    Instant.now(clock),
                    latestPath.toString(),
                    null,
                    null,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    false,
                    "MISSING_PREVIOUS_FRAME",
                    "Camera analysis skipped because previous source snapshot was not available"));
            safetyDecisionService.evaluate(sample);
            return sample;
        }

        CameraSnapshotEntry previousEntry = snapshots.get(snapshots.size() - 2);
        Path previousPath = Path.of(previousEntry.snapshotPath());
        Path deltaPath = deltaPathFor(cameraJob.requireId(), previousEntry, latestEntry, latestPath);

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
                latestMetadata.capturedAt(),
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

    private Path deltaPathFor(
            long cameraJobId,
            CameraSnapshotEntry previousEntry,
            CameraSnapshotEntry latestEntry,
            Path latestPath) {
        long previousSnapshotId = requireSnapshotEntryId(previousEntry);
        long latestSnapshotId = requireSnapshotEntryId(latestEntry);

        Optional<CameraDeltaFrame> existingDelta = deltaFrameStore.findBySnapshotPair(
                cameraJobId,
                previousSnapshotId,
                latestSnapshotId);
        if (existingDelta.isPresent()) {
            return Path.of(existingDelta.get().deltaPath());
        }

        Optional<CameraDeltaSet> liveDeltaSet = deltaSetStore.findByCameraJobId(cameraJobId).stream()
                .filter(deltaSet -> "live-image-delta".equals(deltaSet.methodName()))
                .findFirst();
        if (liveDeltaSet.isPresent()) {
            Path printerDirectory = printerDirectoryFor(latestPath, latestEntry.printerId());
            Path configuredStorageDirectory = printerDirectory.getParent() == null
                    ? storageDirectory
                    : printerDirectory.getParent();
            return CameraStoragePaths.deltaFramePath(
                    configuredStorageDirectory.toString(),
                    latestEntry.printerId(),
                    cameraJobId,
                    liveDeltaSet.get().requireId(),
                    Math.toIntExact(previousSnapshotId),
                    Math.toIntExact(latestSnapshotId));
        }

        return printerDirectoryFor(latestPath, latestEntry.printerId()).resolve("delta.jpg");
    }

    private Path printerDirectoryFor(Path snapshotPath, String printerId) {
        Path parent = snapshotPath.getParent();
        if (parent != null
                && parent.getParent() != null
                && parent.getParent().getFileName() != null
                && "snapshots".equals(parent.getParent().getFileName().toString())
                && parent.getParent().getParent() != null) {
            return parent.getParent().getParent();
        }

        return storageDirectory.resolve(safePathSegment(printerId));
    }

    private static long requireSnapshotEntryId(CameraSnapshotEntry entry) {
        if (entry.id() == null || entry.id() <= 0L) {
            throw new IllegalStateException("camera snapshot entry id is not assigned");
        }

        return entry.id();
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
