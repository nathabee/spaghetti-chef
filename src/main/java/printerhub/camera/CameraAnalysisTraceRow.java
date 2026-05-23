package printerhub.camera;

import java.time.Instant;
import java.util.Objects;

public record CameraAnalysisTraceRow(
        long cameraJobId,
        long deltaSetId,
        long deltaFrameId,
        long calculationRunId,
        long calculationResultId,
        String fromSnapshotPath,
        String toSnapshotPath,
        String deltaPath,
        double confidence,
        boolean suspected,
        String reasonCodes,
        String message,
        Instant createdAt
) {
    public CameraAnalysisTraceRow {
        cameraJobId = requirePositive(cameraJobId, "cameraJobId");
        deltaSetId = requirePositive(deltaSetId, "deltaSetId");
        deltaFrameId = requirePositive(deltaFrameId, "deltaFrameId");
        calculationRunId = requirePositive(calculationRunId, "calculationRunId");
        calculationResultId = requirePositive(calculationResultId, "calculationResultId");
        fromSnapshotPath = requireText(fromSnapshotPath, "fromSnapshotPath");
        toSnapshotPath = requireText(toSnapshotPath, "toSnapshotPath");
        deltaPath = requireText(deltaPath, "deltaPath");
        confidence = requireRatio(confidence, "confidence");
        reasonCodes = reasonCodes == null || reasonCodes.isBlank() ? null : reasonCodes.trim();
        message = message == null || message.isBlank() ? null : message.trim();
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    private static long requirePositive(long value, String fieldName) {
        if (value <= 0L) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero");
        }

        return value;
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
