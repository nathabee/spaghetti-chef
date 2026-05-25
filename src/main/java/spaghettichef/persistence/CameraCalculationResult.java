package spaghettichef.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record CameraCalculationResult(
        Long id,
        long calculationRunId,
        long deltaFrameId,
        double confidence,
        boolean suspected,
        String reasonCodes,
        String message,
        Instant createdAt
) {
    public CameraCalculationResult {
        if (id != null && id <= 0L) {
            throw new IllegalArgumentException("id must be greater than zero");
        }
        calculationRunId = requirePositive(calculationRunId, "calculationRunId");
        deltaFrameId = requirePositive(deltaFrameId, "deltaFrameId");
        confidence = requireRatio(confidence, "confidence");
        reasonCodes = normalizeNullableText(reasonCodes);
        message = normalizeNullableText(message);
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public Optional<Long> idOptional() {
        return Optional.ofNullable(id);
    }

    public long requireId() {
        if (id == null || id <= 0L) {
            throw new IllegalStateException("camera calculation result id is not assigned");
        }

        return id;
    }

    public CameraCalculationResult withId(long id) {
        return new CameraCalculationResult(
                id,
                calculationRunId,
                deltaFrameId,
                confidence,
                suspected,
                reasonCodes,
                message,
                createdAt);
    }

    public Optional<String> reasonCodesOptional() {
        return Optional.ofNullable(reasonCodes);
    }

    public Optional<String> messageOptional() {
        return Optional.ofNullable(message);
    }

    private static long requirePositive(long value, String fieldName) {
        if (value <= 0L) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero");
        }

        return value;
    }

    private static double requireRatio(double value, String fieldName) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }

        return value;
    }

    private static String normalizeNullableText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
