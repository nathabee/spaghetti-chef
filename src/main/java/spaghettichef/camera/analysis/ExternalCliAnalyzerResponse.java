package spaghettichef.camera.analysis;

import java.util.List;
import java.util.Objects;

public final class ExternalCliAnalyzerResponse {

    private final String engineName;
    private final String engineVersion;
    private final String algorithmVariant;
    private final double confidence;
    private final boolean suspected;
    private final List<String> reasonCodes;
    private final String message;
    private final double changedPixelRatio;
    private final double averagePixelDelta;
    private final ExternalCliAnalyzerExitCode exitCode;
    private final String stdout;
    private final String stderr;

    public ExternalCliAnalyzerResponse(
            String engineName,
            String engineVersion,
            String algorithmVariant,
            double confidence,
            boolean suspected,
            List<String> reasonCodes,
            String message,
            double changedPixelRatio,
            double averagePixelDelta,
            ExternalCliAnalyzerExitCode exitCode,
            String stdout,
            String stderr) {
        this.engineName = requireText(engineName, "engineName");
        this.engineVersion = requireText(engineVersion, "engineVersion");
        this.algorithmVariant = requireText(algorithmVariant, "algorithmVariant");
        this.confidence = requireRatio(confidence, "confidence");
        this.suspected = suspected;
        this.reasonCodes = List.copyOf(reasonCodes == null ? List.of() : reasonCodes);
        this.message = requireText(message, "message");
        this.changedPixelRatio = requireRatio(changedPixelRatio, "changedPixelRatio");
        this.averagePixelDelta = requireRatio(averagePixelDelta, "averagePixelDelta");
        this.exitCode = Objects.requireNonNull(exitCode, "exitCode");
        this.stdout = stdout == null ? "" : stdout;
        this.stderr = stderr == null ? "" : stderr;
    }

    public String engineName() {
        return engineName;
    }

    public String engineVersion() {
        return engineVersion;
    }

    public String algorithmVariant() {
        return algorithmVariant;
    }

    public double confidence() {
        return confidence;
    }

    public boolean suspected() {
        return suspected;
    }

    public List<String> reasonCodes() {
        return reasonCodes;
    }

    public String message() {
        return message;
    }

    public double changedPixelRatio() {
        return changedPixelRatio;
    }

    public double averagePixelDelta() {
        return averagePixelDelta;
    }

    public ExternalCliAnalyzerExitCode exitCode() {
        return exitCode;
    }

    public String stdout() {
        return stdout;
    }

    public String stderr() {
        return stderr;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static double requireRatio(double value, String fieldName) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
        return value;
    }
}
