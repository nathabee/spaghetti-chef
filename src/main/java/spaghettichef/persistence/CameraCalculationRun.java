package spaghettichef.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record CameraCalculationRun(
        Long id,
        String printerId,
        long cameraJobId,
        long deltaSetId,
        String methodName,
        String parameterJson,
        Instant createdAt,
        int resultCount,
        String message,
        String engineName,
        String algorithmVariant,
        String engineVersion,
        Long executionDurationMs,
        String engineStatus
) {
    public CameraCalculationRun(
            Long id,
            String printerId,
            long cameraJobId,
            long deltaSetId,
            String methodName,
            String parameterJson,
            Instant createdAt,
            int resultCount,
            String message) {
        this(
                id,
                printerId,
                cameraJobId,
                deltaSetId,
                methodName,
                parameterJson,
                createdAt,
                resultCount,
                message,
                "JAVA_BASIC_DELTA",
                "DELTA_SCORE_THRESHOLD",
                null,
                null,
                "SUCCESS");
    }

    public CameraCalculationRun {
        if (id != null && id <= 0L) {
            throw new IllegalArgumentException("id must be greater than zero");
        }
        printerId = requireText(printerId, "printerId");
        cameraJobId = requirePositive(cameraJobId, "cameraJobId");
        deltaSetId = requirePositive(deltaSetId, "deltaSetId");
        methodName = requireText(methodName, "methodName");
        parameterJson = normalizeJson(parameterJson);
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        resultCount = requireNonNegative(resultCount, "resultCount");
        message = normalizeNullableText(message);
        engineName = requireText(engineName, "engineName");
        algorithmVariant = normalizeNullableText(algorithmVariant);
        engineVersion = normalizeNullableText(engineVersion);
        if (executionDurationMs != null && executionDurationMs < 0L) {
            throw new IllegalArgumentException("executionDurationMs must not be negative");
        }
        engineStatus = requireText(engineStatus, "engineStatus");
    }

    public Optional<Long> idOptional() {
        return Optional.ofNullable(id);
    }

    public long requireId() {
        if (id == null || id <= 0L) {
            throw new IllegalStateException("camera calculation run id is not assigned");
        }

        return id;
    }

    public CameraCalculationRun withId(long id) {
        return new CameraCalculationRun(
                id,
                printerId,
                cameraJobId,
                deltaSetId,
                methodName,
                parameterJson,
                createdAt,
                resultCount,
                message,
                engineName,
                algorithmVariant,
                engineVersion,
                executionDurationMs,
                engineStatus);
    }

    public Optional<String> messageOptional() {
        return Optional.ofNullable(message);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return value.trim();
    }

    private static long requirePositive(long value, String fieldName) {
        if (value <= 0L) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero");
        }

        return value;
    }

    private static int requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }

        return value;
    }

    private static String normalizeJson(String value) {
        return value == null || value.isBlank() ? "{}" : value.trim();
    }

    private static String normalizeNullableText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
