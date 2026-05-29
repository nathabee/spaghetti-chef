package spaghettichef.camera;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import spaghettichef.OperationMessages;
import spaghettichef.camera.analysis.CalculationEngineAdapterType;
import spaghettichef.camera.analysis.CalculationEngineConfiguration;
import spaghettichef.camera.analysis.CalculationEngineRegistry;
import spaghettichef.camera.analysis.CalculationEngineResult;
import spaghettichef.camera.analysis.CalculationEngineStatus;
import spaghettichef.camera.analysis.RustCliAnalyzerException;
import spaghettichef.camera.analysis.SpaghettiCalculationEngine;
import spaghettichef.persistence.CameraCalculationResult;
import spaghettichef.persistence.CameraCalculationResultStore;
import spaghettichef.persistence.CameraCalculationRun;
import spaghettichef.persistence.CameraCalculationRunStore;
import spaghettichef.persistence.CameraDeltaFrame;
import spaghettichef.persistence.CameraDeltaFrameStore;
import spaghettichef.persistence.CameraDeltaSet;
import spaghettichef.persistence.CameraDeltaSetStore;
import spaghettichef.persistence.CameraCalculationEngineSettings;

public final class CameraCalculationRunService {

    private final CameraDeltaSetStore deltaSetStore;
    private final CameraDeltaFrameStore deltaFrameStore;
    private final CameraCalculationRunStore calculationRunStore;
    private final CameraCalculationResultStore calculationResultStore;
    private final CameraCalculationEngineSettingsService engineSettingsService;
    private final CalculationEngineRegistry engineRegistry;
    private final Clock clock;

    public CameraCalculationRunService() {
        this(
                new CameraDeltaSetStore(),
                new CameraDeltaFrameStore(),
                new CameraCalculationRunStore(),
                new CameraCalculationResultStore(),
                new CameraCalculationEngineSettingsService(),
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
                new CameraCalculationEngineSettingsService(),
                new CalculationEngineRegistry(),
                clock);
    }

    public CameraCalculationRunService(
            CameraDeltaSetStore deltaSetStore,
            CameraDeltaFrameStore deltaFrameStore,
            CameraCalculationRunStore calculationRunStore,
            CameraCalculationResultStore calculationResultStore,
            CameraCalculationEngineSettingsService engineSettingsService,
            CalculationEngineRegistry engineRegistry,
            Clock clock) {
        this.deltaSetStore = Objects.requireNonNull(deltaSetStore, "deltaSetStore");
        this.deltaFrameStore = Objects.requireNonNull(deltaFrameStore, "deltaFrameStore");
        this.calculationRunStore = Objects.requireNonNull(calculationRunStore, "calculationRunStore");
        this.calculationResultStore = Objects.requireNonNull(calculationResultStore, "calculationResultStore");
        this.engineSettingsService = Objects.requireNonNull(engineSettingsService, "engineSettingsService");
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
        return run(deltaSetId, methodName, confidenceThreshold, parameterJson, message, engineName, null);
    }

    public CameraCalculationRun run(
            long deltaSetId,
            String methodName,
            Double confidenceThreshold,
            String parameterJson,
            String message,
            String engineName,
            String cliMethod) {
        if (deltaSetId <= 0L) {
            throw new IllegalArgumentException("deltaSetId must be greater than zero");
        }

        CameraDeltaSet deltaSet = deltaSetStore.findById(deltaSetId)
                .orElseThrow(() -> new IllegalArgumentException("camera delta set not found: " + deltaSetId));
        List<CameraDeltaFrame> frames = deltaFrameStore.findByDeltaSetId(deltaSetId);
        Instant createdAt = clock.instant();
        CameraCalculationEngineSettings settings = resolveEngineSettings(engineName);
        double threshold = normalizeThreshold(confidenceThreshold, settings.defaultConfidenceThreshold());
        String resolvedMethodName = normalizeMethodName(methodName, settings.defaultMethodName());
        String resolvedCliMethod = normalizeCliMethod(cliMethod, settings.defaultCliMethod());
        String resolvedParameterJson = resolvedParameterJson(
                parameterJson,
                settings.defaultParameterJson(),
                threshold,
                settings.engineName(),
                resolvedCliMethod);
        SpaghettiCalculationEngine engine = engineRegistry.resolve(new CalculationEngineConfiguration(
                CalculationEngineAdapterType.fromWireValue(settings.adapterType()),
                normalizeExecutablePath(settings.executablePath()),
                resolvedCliMethod,
                Duration.ofMillis(settings.timeoutMs())));
        long startedAtNanos = System.nanoTime();

        if (engine.status() != CalculationEngineStatus.SUCCESS) {
            return calculationRunStore.save(new CameraCalculationRun(
                    null,
                    deltaSet.printerId(),
                    deltaSet.cameraJobId(),
                    deltaSet.requireId(),
                    resolvedMethodName,
                    resolvedParameterJson,
                    createdAt,
                    0,
                    engineUnavailableMessage(message, settings),
                    settings.engineName(),
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
                resolvedMethodName,
                resolvedParameterJson,
                createdAt,
                frames.size(),
                message,
                settings.engineName(),
                engine.algorithmVariant(),
                engine.engineVersion(),
                null,
                engine.status().name()));

        int resultCount = 0;
        String engineVersion = engine.engineVersion();
        try {
            for (CameraDeltaFrame frame : frames) {
                CalculationEngineResult result = engine.analyze(frame, threshold);
                if (result.engineVersion() != null) {
                    engineVersion = result.engineVersion();
                }
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
        } catch (RuntimeException exception) {
            calculationRunStore.updateResultCount(run.requireId(), resultCount);
            return calculationRunStore.updateEngineOutcome(
                    run.requireId(),
                    statusFor(exception).name(),
                    engine.engineVersion(),
                    elapsedMillis(startedAtNanos),
                    OperationMessages.safeDetail(exception.getMessage(), "Rust calculation failed"));
        }

        calculationRunStore.updateResultCount(run.requireId(), resultCount);
        return calculationRunStore.updateEngineOutcome(
                run.requireId(),
                CalculationEngineStatus.SUCCESS.name(),
                engineVersion,
                elapsedMillis(startedAtNanos),
                message);
    }

    private CameraCalculationEngineSettings resolveEngineSettings(String engineName) {
        CameraCalculationEngineSettings settings = engineName == null || engineName.isBlank()
                ? defaultEngineSettings()
                : engineSettingsService.findByEngineName(engineName.trim())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "camera calculation engine settings not found: " + engineName.trim()));
        if (!settings.enabled()) {
            throw new IllegalArgumentException("camera calculation engine is disabled: " + settings.engineName());
        }
        return settings;
    }

    private CameraCalculationEngineSettings defaultEngineSettings() {
        return engineSettingsService.list().stream()
                .filter(CameraCalculationEngineSettings::enabled)
                .findFirst()
                .or(() -> engineSettingsService.findByEngineName("JAVA_BASIC_DELTA"))
                .orElseThrow(() -> new IllegalArgumentException("no camera calculation engine is configured"));
    }

    private static String normalizeMethodName(String methodName, String defaultMethodName) {
        if (methodName == null || methodName.isBlank()) {
            return defaultMethodName;
        }

        return methodName.trim();
    }

    private static double normalizeThreshold(Double confidenceThreshold, double defaultConfidenceThreshold) {
        double threshold = confidenceThreshold == null ? defaultConfidenceThreshold : confidenceThreshold;
        if (Double.isNaN(threshold) || Double.isInfinite(threshold) || threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("confidenceThreshold must be between 0.0 and 1.0");
        }

        return threshold;
    }

    private static String normalizeCliMethod(String cliMethod, String defaultCliMethod) {
        if (cliMethod != null && !cliMethod.isBlank()) {
            return cliMethod.trim();
        }
        if (defaultCliMethod != null && !defaultCliMethod.isBlank()) {
            return defaultCliMethod.trim();
        }
        return null;
    }

    private static String resolvedParameterJson(
            String parameterJson,
            String defaultParameterJson,
            double threshold,
            String engineName,
            String cliMethod) {
        String parameters = parameterJson != null && !parameterJson.isBlank()
                ? parameterJson.trim()
                : defaultParameterJson;
        return "{"
                + "\"confidenceThreshold\":" + threshold + ","
                + "\"engineName\":\"" + engineName + "\","
                + "\"cliMethod\":" + nullableJsonString(cliMethod) + ","
                + "\"parameters\":" + normalizeJsonObject(parameters)
                + "}";
    }

    private static long elapsedMillis(long startedAtNanos) {
        return Math.max(0L, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos));
    }

    private static String engineUnavailableMessage(String requestedMessage, CameraCalculationEngineSettings settings) {
        String defaultMessage = "Calculation engine unavailable: " + settings.engineName();
        if (requestedMessage == null || requestedMessage.isBlank()) {
            return defaultMessage;
        }
        return requestedMessage.trim() + " (" + defaultMessage + ")";
    }

    private static Path normalizeExecutablePath(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Path.of(value.trim());
    }

    private static String normalizeJsonObject(String value) {
        if (value == null || value.isBlank()) {
            return "{}";
        }
        return value.trim();
    }

    private static String nullableJsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static CalculationEngineStatus statusFor(RuntimeException exception) {
        if (exception instanceof RustCliAnalyzerException analyzerException) {
            String message = analyzerException.getMessage() == null ? "" : analyzerException.getMessage();
            if (message.contains("timed out")) {
                return CalculationEngineStatus.TIMEOUT;
            }
            if (message.contains("invalid JSON")) {
                return CalculationEngineStatus.INVALID_RESPONSE;
            }
            return CalculationEngineStatus.FAILED;
        }
        return CalculationEngineStatus.FAILED;
    }
}
