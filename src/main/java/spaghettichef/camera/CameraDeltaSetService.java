package spaghettichef.camera;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import spaghettichef.persistence.CameraDeltaFrame;
import spaghettichef.persistence.CameraDeltaFrameStore;
import spaghettichef.persistence.CameraDeltaSet;
import spaghettichef.persistence.CameraDeltaSetStore;
import spaghettichef.persistence.CameraJob;
import spaghettichef.persistence.CameraJobStore;
import spaghettichef.persistence.CameraSettings;
import spaghettichef.persistence.CameraSettingsStore;
import spaghettichef.persistence.CameraSnapshotEntry;
import spaghettichef.persistence.CameraSnapshotEntryStore;

public final class CameraDeltaSetService {

    private static final String DEFAULT_METHOD_NAME = "image-delta";

    private final CameraJobStore cameraJobStore;
    private final CameraSettingsStore cameraSettingsStore;
    private final CameraSnapshotEntryStore snapshotEntryStore;
    private final CameraDeltaSetStore deltaSetStore;
    private final CameraDeltaFrameStore deltaFrameStore;
    private final FrameAnalyzer frameAnalyzer;
    private final Clock clock;

    public CameraDeltaSetService() {
        this(
                new CameraJobStore(),
                new CameraSettingsStore(),
                new CameraSnapshotEntryStore(),
                new CameraDeltaSetStore(),
                new CameraDeltaFrameStore(),
                new ImageDeltaFrameAnalyzer(),
                Clock.systemUTC());
    }

    public CameraDeltaSetService(
            CameraJobStore cameraJobStore,
            CameraSettingsStore cameraSettingsStore,
            CameraSnapshotEntryStore snapshotEntryStore,
            CameraDeltaSetStore deltaSetStore,
            CameraDeltaFrameStore deltaFrameStore,
            FrameAnalyzer frameAnalyzer,
            Clock clock) {
        this.cameraJobStore = Objects.requireNonNull(cameraJobStore, "cameraJobStore");
        this.cameraSettingsStore = Objects.requireNonNull(cameraSettingsStore, "cameraSettingsStore");
        this.snapshotEntryStore = Objects.requireNonNull(snapshotEntryStore, "snapshotEntryStore");
        this.deltaSetStore = Objects.requireNonNull(deltaSetStore, "deltaSetStore");
        this.deltaFrameStore = Objects.requireNonNull(deltaFrameStore, "deltaFrameStore");
        this.frameAnalyzer = Objects.requireNonNull(frameAnalyzer, "frameAnalyzer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CameraDeltaSetGenerationResult generate(
            String printerId,
            long cameraJobId,
            int deltaSnapshotStep,
            String methodName,
            String message) {
        String normalizedPrinterId = requireText(printerId, "printerId");
        if (cameraJobId <= 0L) {
            throw new IllegalArgumentException("cameraJobId must be greater than zero");
        }
        if (deltaSnapshotStep <= 0) {
            throw new IllegalArgumentException("deltaSnapshotStep must be greater than zero");
        }

        CameraJob cameraJob = cameraJobStore.findById(cameraJobId)
                .orElseThrow(() -> new IllegalArgumentException("camera job not found: " + cameraJobId));
        if (!cameraJob.printerId().equals(normalizedPrinterId)) {
            throw new IllegalArgumentException("camera job does not belong to printer: " + normalizedPrinterId);
        }

        List<CameraSnapshotEntry> snapshots = snapshotEntryStore.findByPrinterIdAndJobId(
                normalizedPrinterId,
                Long.toString(cameraJobId));
        Instant createdAt = clock.instant();

        CameraDeltaSet deltaSet = deltaSetStore.save(new CameraDeltaSet(
                null,
                normalizedPrinterId,
                cameraJobId,
                normalizeMethodName(methodName),
                deltaSnapshotStep,
                snapshots.size(),
                0,
                createdAt,
                message));

        CameraSettings settings = cameraSettingsStore.loadOrDefault(normalizedPrinterId);
        Path deltaDirectory = CameraStoragePaths.deltasDirectory(
                settings.storageDirectory(),
                normalizedPrinterId,
                cameraJobId,
                deltaSet.requireId());

        int generatedCount = 0;
        try {
            Files.createDirectories(deltaDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create camera delta directory", exception);
        }

        for (int fromIndex = 0; fromIndex + deltaSnapshotStep < snapshots.size(); fromIndex += deltaSnapshotStep) {
            int toIndex = fromIndex + deltaSnapshotStep;
            CameraSnapshotEntry from = snapshots.get(fromIndex);
            CameraSnapshotEntry to = snapshots.get(toIndex);
            Path deltaPath = CameraStoragePaths.deltaFramePath(
                    settings.storageDirectory(),
                    normalizedPrinterId,
                    cameraJobId,
                    deltaSet.requireId(),
                    fromIndex + 1,
                    toIndex + 1);

            FrameAnalysisResult analysis = frameAnalyzer.analyze(
                    normalizedPrinterId,
                    Path.of(from.snapshotPath()),
                    Path.of(to.snapshotPath()),
                    Optional.of(deltaPath));

            if (!analysis.completed()) {
                continue;
            }

            deltaFrameStore.save(new CameraDeltaFrame(
                    null,
                    deltaSet.requireId(),
                    normalizedPrinterId,
                    cameraJobId,
                    from.id(),
                    to.id(),
                    from.capturedAt(),
                    to.capturedAt(),
                    deltaPath.toString(),
                    analysis.deltaScore(),
                    analysis.changedPixelRatio(),
                    analysis.averagePixelDelta(),
                    createdAt));
            generatedCount++;
        }

        CameraDeltaSet updatedDeltaSet = deltaSetStore.updateGeneratedDeltaCount(deltaSet.requireId(), generatedCount);
        return new CameraDeltaSetGenerationResult(
                updatedDeltaSet,
                snapshots.size(),
                generatedCount,
                skippedIntermediateSnapshotCount(snapshots.size(), deltaSnapshotStep, generatedCount));
    }

    private static int skippedIntermediateSnapshotCount(int sourceSnapshotCount, int step, int generatedCount) {
        if (sourceSnapshotCount <= 1 || generatedCount <= 0) {
            return 0;
        }

        return Math.max(0, sourceSnapshotCount - ((generatedCount * step) + 1));
    }

    private static String normalizeMethodName(String methodName) {
        if (methodName == null || methodName.isBlank()) {
            return DEFAULT_METHOD_NAME;
        }

        return methodName.trim();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return value.trim();
    }
}
