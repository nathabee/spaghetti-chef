package spaghettichef.camera;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import spaghettichef.camera.analysis.CalculationEngineName;
import spaghettichef.camera.analysis.CalculationEngineRegistry;
import spaghettichef.camera.analysis.CalculationEngineResult;
import spaghettichef.camera.analysis.CalculationEngineStatus;
import spaghettichef.camera.analysis.SpaghettiCalculationEngine;
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
    private final CalculationEngineRegistry engineRegistry;
    private final Clock clock;

    public CameraCalculationRunService() {
        this(
                new CameraDeltaSetStore(),
                new CameraDeltaFrameStore(),
                new CameraCalculationRunStore(),
                new CameraCalculationResultStore(),
                new CalculationEngineRegistry(),
                Clock.systemUTC());
    }

    public CameraCalculationRunService(
            CameraDeltaSetStore deltaSetStore,
            CameraDeltaFrameStore deltaFrameStore,
            CameraCalculationRunStore calculationRunStore,
            CameraCalculationResultStore calculationResultStore,
            Clock clock) {
        this(
                deltaSetStore,
                deltaFrameStore,
                calculationRunStore,
                calculationResultStore,
                new CalculationEngineRegistry(),
                clock);
    }

    public CameraCalculationRunService(
            CameraDeltaSetStore deltaSetStore,
            CameraDeltaFrameStore deltaFrameStore,
            CameraCalculationRunStore calculationRunStore,
            CameraCalculationResultStore calculationResultStore,
            CalculationEngineRegistry engineRegistry,
            Clock clock) {
        this.deltaSetStore = Objects.requireNonNull(deltaSetStore, "deltaSetStore");
        this.deltaFrameStore = Objects.requireNonNull(deltaFrameStore, "deltaFrameStore");
        this.calculationRunStore = Objects.requireNonNull(calculationRunStore, "calculationRunStore");
        this.calculationResultStore = Objects.requireNonNull(calculationResultStore, "calculationResultStore");
        this.engineRegistry = Objects.requireNonNull(engineRegistry, "engineRegistry");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CameraCalculationRun run(
            long deltaSetId,
            String methodName,
            Double confidenceThreshold,
            String parameterJson,
            String message) {
        return run(deltaSetId, methodName, confidenceThreshold, parameterJson, message, null);
    }

    public CameraCalculationRun run(
            long deltaSetId,
            String methodName,
            Double confidenceThreshold,
            String parameterJson,
            String message,
            String engineName) {
        if (deltaSetId <= 0L) {
            throw new IllegalArgumentException("deltaSetId must be greater than zero");
        }

        CameraDeltaSet deltaSet = deltaSetStore.findById(deltaSetId)
                .orElseThrow(() -> new IllegalArgumentException("camera delta set not found: " + deltaSetId));
        List<CameraDeltaFrame> frames = deltaFrameStore.findByDeltaSetId(deltaSetId);
        Instant createdAt = clock.instant();
        double threshold = normalizeThreshold(confidenceThreshold);
        SpaghettiCalculationEngine engine = engineRegistry.resolve(CalculationEngineName.fromWireValue(engineName));
        long startedAtNanos = System.nanoTime();

        if (engine.status() != CalculationEngineStatus.SUCCESS) {
            return calculationRunStore.save(new CameraCalculationRun(
                    null,
                    deltaSet.printerId(),
                    deltaSet.cameraJobId(),
                    deltaSet.requireId(),
                    normalizeMethodName(methodName),
                    normalizeParameterJson(parameterJson, threshold, engine.engineName().name()),
                    createdAt,
                    0,
                    engineUnavailableMessage(message, engine),
                    engine.engineName().name(),
                    engine.algorithmVariant(),
                    engine.engineVersion(),
                    elapsedMillis(startedAtNanos),
                    engine.status().name()));
        }

        CameraCalculationRun run = calculationRunStore.save(new CameraCalculationRun(
                null,
                deltaSet.printerId(),
                deltaSet.cameraJobId(),
                deltaSet.requireId(),
                normalizeMethodName(methodName),
                normalizeParameterJson(parameterJson, threshold),
                createdAt,
                frames.size(),
                message,
                engine.engineName().name(),
                engine.algorithmVariant(),
                engine.engineVersion(),
                null,
                engine.status().name()));

        int resultCount = 0;
        for (CameraDeltaFrame frame : frames) {
            CalculationEngineResult result = engine.analyze(frame, threshold);
            calculationResultStore.save(new CameraCalculationResult(
                    null,
                    run.requireId(),
                    frame.requireId(),
                    result.confidence(),
                    result.suspected(),
                    result.reasonCodesText(),
                    result.message(),
                    createdAt));
            resultCount++;
        }

        calculationRunStore.updateResultCount(run.requireId(), resultCount);
        return calculationRunStore.updateExecutionDuration(run.requireId(), elapsedMillis(startedAtNanos));
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

    private static String normalizeParameterJson(String parameterJson, double threshold, String engineName) {
        if (parameterJson != null && !parameterJson.isBlank()) {
            return parameterJson.trim();
        }

        return "{\"confidenceThreshold\":" + threshold + ",\"engineName\":\"" + engineName + "\"}";
    }

    private static long elapsedMillis(long startedAtNanos) {
        return Math.max(0L, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos));
    }

    private static String engineUnavailableMessage(String requestedMessage, SpaghettiCalculationEngine engine) {
        String defaultMessage = "Calculation engine unavailable: " + engine.engineName().name();
        if (requestedMessage == null || requestedMessage.isBlank()) {
            return defaultMessage;
        }
        return requestedMessage.trim() + " (" + defaultMessage + ")";
    }
}
