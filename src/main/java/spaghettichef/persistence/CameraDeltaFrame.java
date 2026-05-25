package spaghettichef.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record CameraDeltaFrame(
        Long id,
        long deltaSetId,
        String printerId,
        long cameraJobId,
        long fromSnapshotId,
        long toSnapshotId,
        Instant fromCapturedAt,
        Instant toCapturedAt,
        String deltaPath,
        double deltaScore,
        double changedPixelRatio,
        double averagePixelDelta,
        Instant createdAt
) {
    public CameraDeltaFrame {
        if (id != null && id <= 0L) {
            throw new IllegalArgumentException("id must be greater than zero");
        }
        deltaSetId = requirePositive(deltaSetId, "deltaSetId");
        printerId = requireText(printerId, "printerId");
        cameraJobId = requirePositive(cameraJobId, "cameraJobId");
        fromSnapshotId = requirePositive(fromSnapshotId, "fromSnapshotId");
        toSnapshotId = requirePositive(toSnapshotId, "toSnapshotId");
        fromCapturedAt = Objects.requireNonNull(fromCapturedAt, "fromCapturedAt");
        toCapturedAt = Objects.requireNonNull(toCapturedAt, "toCapturedAt");
        deltaPath = requireText(deltaPath, "deltaPath");
        deltaScore = requireRatio(deltaScore, "deltaScore");
        changedPixelRatio = requireRatio(changedPixelRatio, "changedPixelRatio");
        averagePixelDelta = requireRatio(averagePixelDelta, "averagePixelDelta");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public Optional<Long> idOptional() {
        return Optional.ofNullable(id);
    }

    public long requireId() {
        if (id == null || id <= 0L) {
            throw new IllegalStateException("camera delta frame id is not assigned");
        }

        return id;
    }

    public CameraDeltaFrame withId(long id) {
        return new CameraDeltaFrame(
                id,
                deltaSetId,
                printerId,
                cameraJobId,
                fromSnapshotId,
                toSnapshotId,
                fromCapturedAt,
                toCapturedAt,
                deltaPath,
                deltaScore,
                changedPixelRatio,
                averagePixelDelta,
                createdAt);
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

    private static double requireRatio(double value, String fieldName) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }

        return value;
    }
}
