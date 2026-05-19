package printerhub.persistence;

import java.time.Instant;
import java.util.Optional;

public final class CameraAnalysisSample {

    private final Long id;
    private final String sessionId;
    private final String printerId;
    private final Instant capturedAt;
    private final Instant analyzedAt;
    private final String latestSnapshotPath;
    private final String previousSnapshotPath;
    private final String deltaSnapshotPath;
    private final double deltaScore;
    private final double changedPixelRatio;
    private final double averagePixelDelta;
    private final double confidence;
    private final boolean suspected;
    private final String reasonCodes;
    private final String message;

    public CameraAnalysisSample(
            Long id,
            String sessionId,
            String printerId,
            Instant capturedAt,
            Instant analyzedAt,
            String latestSnapshotPath,
            String previousSnapshotPath,
            String deltaSnapshotPath,
            double deltaScore,
            double changedPixelRatio,
            double averagePixelDelta,
            double confidence,
            boolean suspected,
            String reasonCodes,
            String message) {
        this.id = id;
        this.sessionId = requireText(sessionId, "sessionId");
        this.printerId = requireText(printerId, "printerId");
        this.capturedAt = requireInstant(capturedAt, "capturedAt");
        this.analyzedAt = requireInstant(analyzedAt, "analyzedAt");
        this.latestSnapshotPath = normalize(latestSnapshotPath);
        this.previousSnapshotPath = normalize(previousSnapshotPath);
        this.deltaSnapshotPath = normalize(deltaSnapshotPath);
        this.deltaScore = requireRatio(deltaScore, "deltaScore");
        this.changedPixelRatio = requireRatio(changedPixelRatio, "changedPixelRatio");
        this.averagePixelDelta = requireRatio(averagePixelDelta, "averagePixelDelta");
        this.confidence = requireRatio(confidence, "confidence");
        this.suspected = suspected;
        this.reasonCodes = normalize(reasonCodes);
        this.message = normalize(message);
    }

    public Optional<Long> id() { return Optional.ofNullable(id); }
    public String sessionId() { return sessionId; }
    public String printerId() { return printerId; }
    public Instant capturedAt() { return capturedAt; }
    public Instant analyzedAt() { return analyzedAt; }
    public Optional<String> latestSnapshotPath() { return Optional.ofNullable(latestSnapshotPath); }
    public Optional<String> previousSnapshotPath() { return Optional.ofNullable(previousSnapshotPath); }
    public Optional<String> deltaSnapshotPath() { return Optional.ofNullable(deltaSnapshotPath); }
    public double deltaScore() { return deltaScore; }
    public double changedPixelRatio() { return changedPixelRatio; }
    public double averagePixelDelta() { return averagePixelDelta; }
    public double confidence() { return confidence; }
    public boolean suspected() { return suspected; }
    public Optional<String> reasonCodes() { return Optional.ofNullable(reasonCodes); }
    public Optional<String> message() { return Optional.ofNullable(message); }

    private static Instant requireInstant(Instant value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
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
