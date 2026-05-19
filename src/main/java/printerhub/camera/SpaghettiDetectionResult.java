package printerhub.camera;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class SpaghettiDetectionResult {

    private final String printerId;
    private final boolean suspected;
    private final double confidence;
    private final Set<SpaghettiDetectionReason> reasons;
    private final String message;
    private final FrameAnalysisResult frameAnalysis;
    private final Instant detectedAt;

    private SpaghettiDetectionResult(
            String printerId,
            boolean suspected,
            double confidence,
            Set<SpaghettiDetectionReason> reasons,
            String message,
            FrameAnalysisResult frameAnalysis,
            Instant detectedAt) {
        this.printerId = requirePrinterId(printerId);
        this.suspected = suspected;
        this.confidence = requireRatio(confidence, "confidence");
        this.reasons = immutableReasons(reasons);
        this.message = normalizeNullableText(message);
        this.frameAnalysis = Objects.requireNonNull(frameAnalysis, "frameAnalysis");
        this.detectedAt = Objects.requireNonNull(detectedAt, "detectedAt");
    }

    public static SpaghettiDetectionResult of(
            String printerId,
            boolean suspected,
            double confidence,
            Set<SpaghettiDetectionReason> reasons,
            String message,
            FrameAnalysisResult frameAnalysis,
            Instant detectedAt) {
        return new SpaghettiDetectionResult(
                printerId,
                suspected,
                confidence,
                reasons,
                message,
                frameAnalysis,
                detectedAt);
    }

    public String printerId() {
        return printerId;
    }

    public boolean suspected() {
        return suspected;
    }

    public double confidence() {
        return confidence;
    }

    public Set<SpaghettiDetectionReason> reasons() {
        return reasons;
    }

    public Optional<String> message() {
        return Optional.ofNullable(message);
    }

    public FrameAnalysisResult frameAnalysis() {
        return frameAnalysis;
    }

    public Instant detectedAt() {
        return detectedAt;
    }

    private static String requirePrinterId(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException("printerId must not be blank");
        }

        return printerId.trim();
    }

    private static double requireRatio(double value, String fieldName) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }

        return value;
    }

    private static Set<SpaghettiDetectionReason> immutableReasons(Set<SpaghettiDetectionReason> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return Collections.emptySet();
        }

        return Collections.unmodifiableSet(EnumSet.copyOf(reasons));
    }

    private static String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}