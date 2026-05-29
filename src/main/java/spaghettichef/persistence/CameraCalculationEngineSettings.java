package spaghettichef.persistence;

import java.time.Instant;

public record CameraCalculationEngineSettings(
        String engineName,
        String engineLabel,
        boolean enabled,
        String defaultMethodName,
        double defaultConfidenceThreshold,
        String defaultParameterJson,
        String defaultCliMethod,
        String executablePath,
        int timeoutMs,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt) {

    public CameraCalculationEngineSettings {
        engineName = requireText(engineName, "engineName");
        engineLabel = requireText(engineLabel, "engineLabel");
        defaultMethodName = requireText(defaultMethodName, "defaultMethodName");
        defaultParameterJson = normalizeJson(defaultParameterJson);
        defaultCliMethod = normalizeNullableText(defaultCliMethod);
        executablePath = normalizeNullableText(executablePath);
        if (Double.isNaN(defaultConfidenceThreshold)
                || Double.isInfinite(defaultConfidenceThreshold)
                || defaultConfidenceThreshold < 0.0
                || defaultConfidenceThreshold > 1.0) {
            throw new IllegalArgumentException("defaultConfidenceThreshold must be between 0.0 and 1.0");
        }
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be greater than zero");
        }
        if (sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder must not be negative");
        }
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    public CameraCalculationEngineSettings withUpdatedAt(Instant updatedAt) {
        return new CameraCalculationEngineSettings(
                engineName,
                engineLabel,
                enabled,
                defaultMethodName,
                defaultConfidenceThreshold,
                defaultParameterJson,
                defaultCliMethod,
                executablePath,
                timeoutMs,
                sortOrder,
                createdAt,
                updatedAt);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeJson(String value) {
        if (value == null || value.isBlank()) {
            return "{}";
        }
        return value.trim();
    }

    private static String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
