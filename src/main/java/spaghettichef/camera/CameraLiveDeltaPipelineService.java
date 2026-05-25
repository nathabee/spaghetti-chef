package spaghettichef.camera;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import spaghettichef.persistence.CameraCalculationResult;
import spaghettichef.persistence.CameraCalculationResultStore;
import spaghettichef.persistence.CameraCalculationRun;
import spaghettichef.persistence.CameraCalculationRunStore;
import spaghettichef.persistence.CameraDeltaFrame;
import spaghettichef.persistence.CameraDeltaFrameStore;
import spaghettichef.persistence.CameraDeltaSet;
import spaghettichef.persistence.CameraDeltaSetStore;
import spaghettichef.persistence.CameraSettings;
import spaghettichef.persistence.CameraSnapshotEntry;
import spaghettichef.persistence.CameraSnapshotEntryStore;

public final class CameraLiveDeltaPipelineService {

    private static final String LIVE_DELTA_METHOD = "live-image-delta";
    private static final String LIVE_CALCULATION_METHOD = "live-spaghetti-delta-threshold";
    private static final String LIVE_DELTA_MESSAGE = "live camera job delta set";
    private static final String LIVE_CALCULATION_MESSAGE = "live camera job calculation run";

    private final CameraSnapshotEntryStore snapshotEntryStore;
    private final CameraDeltaSetStore deltaSetStore;
    private final CameraDeltaFrameStore deltaFrameStore;
    private final CameraCalculationRunStore calculationRunStore;
    private final CameraCalculationResultStore calculationResultStore;
    private final FrameAnalyzer frameAnalyzer;
    private final SpaghettiDetectionService spaghettiDetectionService;
    private final Clock clock;

    public CameraLiveDeltaPipelineService() {
        this(
                new CameraSnapshotEntryStore(),
                new CameraDeltaSetStore(),
                new CameraDeltaFrameStore(),
                new CameraCalculationRunStore(),
                new CameraCalculationResultStore(),
                new ImageDeltaFrameAnalyzer(),
                new SpaghettiDetectionService(),
                Clock.systemUTC());
    }

    public CameraLiveDeltaPipelineService(
            CameraSnapshotEntryStore snapshotEntryStore,
            CameraDeltaSetStore deltaSetStore,
            CameraDeltaFrameStore deltaFrameStore,
            CameraCalculationRunStore calculationRunStore,
            CameraCalculationResultStore calculationResultStore,
            FrameAnalyzer frameAnalyzer,
            SpaghettiDetectionService spaghettiDetectionService,
            Clock clock) {
        this.snapshotEntryStore = Objects.requireNonNull(snapshotEntryStore, "snapshotEntryStore");
        this.deltaSetStore = Objects.requireNonNull(deltaSetStore, "deltaSetStore");
        this.deltaFrameStore = Objects.requireNonNull(deltaFrameStore, "deltaFrameStore");
        this.calculationRunStore = Objects.requireNonNull(calculationRunStore, "calculationRunStore");
        this.calculationResultStore = Objects.requireNonNull(calculationResultStore, "calculationResultStore");
        this.frameAnalyzer = Objects.requireNonNull(frameAnalyzer, "frameAnalyzer");
        this.spaghettiDetectionService = Objects.requireNonNull(spaghettiDetectionService, "spaghettiDetectionService");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Optional<CameraCalculationResult> processLatestSnapshot(CameraSettings settings, long cameraJobId) {
        Objects.requireNonNull(settings, "settings");
        if (cameraJobId <= 0L) {
            throw new IllegalArgumentException("cameraJobId must be greater than zero");
        }

        List<CameraSnapshotEntry> snapshots = snapshotEntryStore.findByPrinterIdAndJobId(
                settings.printerId(),
                Long.toString(cameraJobId));
        if (snapshots.size() < 2) {
            return Optional.empty();
        }

        CameraSnapshotEntry from = snapshots.get(snapshots.size() - 2);
        CameraSnapshotEntry to = snapshots.get(snapshots.size() - 1);
        long fromSnapshotId = requireSnapshotId(from);
        long toSnapshotId = requireSnapshotId(to);

        CameraDeltaSet deltaSet = liveDeltaSet(settings, cameraJobId, snapshots.size());
        if (deltaFrameAlreadyExists(deltaSet.requireId(), toSnapshotId)) {
            return Optional.empty();
        }

        Path deltaPath = CameraStoragePaths.deltaFramePath(
                settings.storageDirectory(),
                settings.printerId(),
                cameraJobId,
                deltaSet.requireId(),
                Math.toIntExact(fromSnapshotId),
                Math.toIntExact(toSnapshotId));

        FrameAnalysisResult analysis = frameAnalyzer.analyze(
                settings.printerId(),
                Path.of(from.snapshotPath()),
                Path.of(to.snapshotPath()),
                Optional.of(deltaPath));

        if (!analysis.completed() || !Files.isRegularFile(deltaPath)) {
            return Optional.empty();
        }

        Instant createdAt = clock.instant();
        CameraDeltaFrame frame = deltaFrameStore.save(new CameraDeltaFrame(
                null,
                deltaSet.requireId(),
                settings.printerId(),
                cameraJobId,
                fromSnapshotId,
                toSnapshotId,
                from.capturedAt(),
                to.capturedAt(),
                deltaPath.toString(),
                analysis.deltaScore(),
                analysis.changedPixelRatio(),
                analysis.averagePixelDelta(),
                createdAt));

        List<CameraDeltaFrame> frames = deltaFrameStore.findByDeltaSetId(deltaSet.requireId());
        deltaSetStore.updateCounts(deltaSet.requireId(), snapshots.size(), frames.size());

        CameraCalculationRun run = liveCalculationRun(settings, cameraJobId, deltaSet.requireId());
        SpaghettiDetectionResult detection = spaghettiDetectionService.detect(analysis);
        CameraCalculationResult result = calculationResultStore.save(new CameraCalculationResult(
                null,
                run.requireId(),
                frame.requireId(),
                detection.confidence(),
                detection.suspected(),
                detection.reasons().toString(),
                detection.message().orElse(null),
                detection.detectedAt()));

        calculationRunStore.updateResultCount(
                run.requireId(),
                calculationResultStore.findByCalculationRunId(run.requireId()).size());
        return Optional.of(result);
    }

    private CameraDeltaSet liveDeltaSet(CameraSettings settings, long cameraJobId, int sourceSnapshotCount) {
        return deltaSetStore.findByCameraJobId(cameraJobId).stream()
                .filter(deltaSet -> LIVE_DELTA_METHOD.equals(deltaSet.methodName()))
                .findFirst()
                .orElseGet(() -> deltaSetStore.save(new CameraDeltaSet(
                        null,
                        settings.printerId(),
                        cameraJobId,
                        LIVE_DELTA_METHOD,
                        1,
                        sourceSnapshotCount,
                        0,
                        clock.instant(),
                        LIVE_DELTA_MESSAGE)));
    }

    private CameraCalculationRun liveCalculationRun(CameraSettings settings, long cameraJobId, long deltaSetId) {
        return calculationRunStore.findByDeltaSetId(deltaSetId).stream()
                .filter(run -> LIVE_CALCULATION_METHOD.equals(run.methodName()))
                .findFirst()
                .orElseGet(() -> calculationRunStore.save(new CameraCalculationRun(
                        null,
                        settings.printerId(),
                        cameraJobId,
                        deltaSetId,
                        LIVE_CALCULATION_METHOD,
                        "{}",
                        clock.instant(),
                        0,
                        LIVE_CALCULATION_MESSAGE)));
    }

    private boolean deltaFrameAlreadyExists(long deltaSetId, long toSnapshotId) {
        return deltaFrameStore.findByDeltaSetId(deltaSetId).stream()
                .anyMatch(frame -> frame.toSnapshotId() == toSnapshotId);
    }

    private static long requireSnapshotId(CameraSnapshotEntry entry) {
        if (entry.id() == null || entry.id() <= 0L) {
            throw new IllegalStateException("camera snapshot entry id is not assigned");
        }

        return entry.id();
    }
}
