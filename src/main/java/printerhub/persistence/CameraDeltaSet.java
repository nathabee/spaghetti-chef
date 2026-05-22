package printerhub.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record CameraDeltaSet(
        Long id,
        String printerId,
        long cameraJobId,
        String methodName,
        int deltaSnapshotStep,
        int sourceSnapshotCount,
        int generatedDeltaCount,
        Instant createdAt,
        String message
) {
    public CameraDeltaSet {
        if (id != null && id <= 0L) {
            throw new IllegalArgumentException("id must be greater than zero");
        }
        printerId = requireText(printerId, "printerId");
        if (cameraJobId <= 0L) {
            throw new IllegalArgumentException("cameraJobId must be greater than zero");
        }
        methodName = requireText(methodName, "methodName");
        deltaSnapshotStep = requirePositive(deltaSnapshotStep, "deltaSnapshotStep");
        sourceSnapshotCount = requireNonNegative(sourceSnapshotCount, "sourceSnapshotCount");
        generatedDeltaCount = requireNonNegative(generatedDeltaCount, "generatedDeltaCount");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        message = normalizeNullableText(message);
    }

    public Optional<Long> idOptional() {
        return Optional.ofNullable(id);
    }

    public long requireId() {
        if (id == null || id <= 0L) {
            throw new IllegalStateException("camera delta set id is not assigned");
        }

        return id;
    }

    public CameraDeltaSet withId(long id) {
        return new CameraDeltaSet(
                id,
                printerId,
                cameraJobId,
                methodName,
                deltaSnapshotStep,
                sourceSnapshotCount,
                generatedDeltaCount,
                createdAt,
                message);
    }

    public CameraDeltaSet withGeneratedDeltaCount(int generatedDeltaCount) {
        return new CameraDeltaSet(
                id,
                printerId,
                cameraJobId,
                methodName,
                deltaSnapshotStep,
                sourceSnapshotCount,
                generatedDeltaCount,
                createdAt,
                message);
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

    private static int requirePositive(int value, String fieldName) {
        if (value <= 0) {
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

    private static String normalizeNullableText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
