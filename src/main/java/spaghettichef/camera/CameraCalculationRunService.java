package spaghettichef.camera;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import spaghettichef.persistence.CameraCalculationResult;
import spaghettichef.persistence.CameraCalculationResultStore;
import spaghettichef.persistence.CameraCalculationRun;
import spaghettichef.persistence.CameraCalculationRunStore;
import spaghettichef.persistence.CameraDeltaFrame;
import spaghettichef.persistence.CameraDeltaFrameStore;
import spaghettichef.persistence.CameraDeltaSet;
import spaghettichef.persistence.CameraDeltaSetStore;

public final class CameraCalculationRunService {

    private static final String DEFAULT_METHOD_NAME = "spaghetti-delta-threshold";
    private static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.85;

    private final CameraDeltaSetStore deltaSetStore;
    private final CameraDeltaFrameStore deltaFrameStore;
    private final CameraCalculationRunStore calculationRunStore;
    private final CameraCalculationResultStore calculationResultStore;
    private final Clock clock;

    public CameraCalculationRunService() {
        this(
                new CameraDeltaSetStore(),
                new CameraDeltaFrameStore(),
                new CameraCalculationRunStore(),
                new CameraCalculationResultStore(),
                Clock.systemUTC());
    }

    public CameraCalculationRunService(
            CameraDeltaSetStore deltaSetStore,
            CameraDeltaFrameStore deltaFrameStore,
            CameraCalculationRunStore calculationRunStore,
            CameraCalculationResultStore calculationResultStore,
            Clock clock) {
        this.deltaSetStore = Objects.requireNonNull(deltaSetStore, "deltaSetStore");
        this.deltaFrameStore = Objects.requireNonNull(deltaFrameStore, "deltaFrameStore");
        this.calculationRunStore = Objects.requireNonNull(calculationRunStore, "calculationRunStore");
        this.calculationResultStore = Objects.requireNonNull(calculationResultStore, "calculationResultStore");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CameraCalculationRun run(
            long deltaSetId,
            String methodName,
            Double confidenceThreshold,
            String parameterJson,
            String message) {
        if (deltaSetId <= 0L) {
            throw new IllegalArgumentException("deltaSetId must be greater than zero");
        }

        CameraDeltaSet deltaSet = deltaSetStore.findById(deltaSetId)
                .orElseThrow(() -> new IllegalArgumentException("camera delta set not found: " + deltaSetId));
        List<CameraDeltaFrame> frames = deltaFrameStore.findByDeltaSetId(deltaSetId);
        Instant createdAt = clock.instant();
        double threshold = normalizeThreshold(confidenceThreshold);

        CameraCalculationRun run = calculationRunStore.save(new CameraCalculationRun(
                null,
                deltaSet.printerId(),
                deltaSet.cameraJobId(),
                deltaSet.requireId(),
                normalizeMethodName(methodName),
                normalizeParameterJson(parameterJson, threshold),
                createdAt,
                frames.size(),
                message));

        for (CameraDeltaFrame frame : frames) {
            double confidence = frame.deltaScore();
            boolean suspected = confidence >= threshold;
            calculationResultStore.save(new CameraCalculationResult(
                    null,
                    run.requireId(),
                    frame.requireId(),
                    confidence,
                    suspected,
                    suspected ? "HIGH_VISUAL_DELTA" : "LOW_VISUAL_DELTA",
                    suspected ? "Delta score meets threshold" : "Delta score below threshold",
                    createdAt));
        }

        return run;
    }

    private static String normalizeMethodName(String methodName) {
        if (methodName == null || methodName.isBlank()) {
            return DEFAULT_METHOD_NAME;
        }

        return methodName.trim();
    }

    private static double normalizeThreshold(Double confidenceThreshold) {
        double threshold = confidenceThreshold == null ? DEFAULT_CONFIDENCE_THRESHOLD : confidenceThreshold;
        if (Double.isNaN(threshold) || Double.isInfinite(threshold) || threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("confidenceThreshold must be between 0.0 and 1.0");
        }

        return threshold;
    }

    private static String normalizeParameterJson(String parameterJson, double threshold) {
        if (parameterJson != null && !parameterJson.isBlank()) {
            return parameterJson.trim();
        }

        return "{\"confidenceThreshold\":" + threshold + "}";
    }
}
