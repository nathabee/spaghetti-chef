package spaghettichef.camera;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;

import spaghettichef.config.RuntimeDefaults;

public final class SpaghettiDetectionService {

    private final double deltaScoreThreshold;
    private final double confidenceThreshold;
    private final Clock clock;

    public SpaghettiDetectionService() {
        this(
                RuntimeDefaults.DEFAULT_CAMERA_DELTA_SCORE_THRESHOLD,
                RuntimeDefaults.DEFAULT_CAMERA_SPAGHETTI_CONFIDENCE_THRESHOLD,
                Clock.systemUTC());
    }

    public SpaghettiDetectionService(
            double deltaScoreThreshold,
            double confidenceThreshold,
            Clock clock) {
        this.deltaScoreThreshold = requireRatio(deltaScoreThreshold, "deltaScoreThreshold");
        this.confidenceThreshold = requireRatio(confidenceThreshold, "confidenceThreshold");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public SpaghettiDetectionResult detect(FrameAnalysisResult analysisResult) {
        FrameAnalysisResult analysis = Objects.requireNonNull(analysisResult, "analysisResult");
        Instant detectedAt = Instant.now(clock);

        if (!analysis.completed()) {
            return SpaghettiDetectionResult.of(
                    analysis.printerId(),
                    false,
                    0.0,
                    EnumSet.of(SpaghettiDetectionReason.ANALYSIS_NOT_COMPLETED),
                    "Spaghetti detection skipped because frame analysis was not completed",
                    analysis,
                    detectedAt);
        }

        EnumSet<SpaghettiDetectionReason> reasons = EnumSet.noneOf(SpaghettiDetectionReason.class);

        if (analysis.deltaScore() >= deltaScoreThreshold) {
            reasons.add(SpaghettiDetectionReason.HIGH_VISUAL_DELTA);
        } else {
            reasons.add(SpaghettiDetectionReason.LOW_VISUAL_DELTA);
        }

        if (analysis.changedPixelRatio() >= deltaScoreThreshold) {
            reasons.add(SpaghettiDetectionReason.EXCESSIVE_CHANGED_AREA);
        }

        double confidence = calculateConfidence(analysis);
        boolean suspected = confidence >= confidenceThreshold;

        if (suspected) {
            reasons.add(SpaghettiDetectionReason.POSSIBLE_SPAGHETTI_PATTERN);
        } else {
            reasons.add(SpaghettiDetectionReason.INSUFFICIENT_EVIDENCE);
        }

        return SpaghettiDetectionResult.of(
                analysis.printerId(),
                suspected,
                confidence,
                reasons,
                suspected
                        ? "Possible spaghetti failure detected from visual frame delta"
                        : "No spaghetti suspicion from current visual frame delta",
                analysis,
                detectedAt);
    }

    private double calculateConfidence(FrameAnalysisResult analysis) {
        double scoreFactor = normalizedAgainstThreshold(analysis.deltaScore(), deltaScoreThreshold);
        double changedAreaFactor = normalizedAgainstThreshold(analysis.changedPixelRatio(), deltaScoreThreshold);
        double averageDeltaFactor = analysis.averagePixelDelta();

        return clamp((scoreFactor * 0.50) + (changedAreaFactor * 0.35) + (averageDeltaFactor * 0.15));
    }

    private static double normalizedAgainstThreshold(double value, double threshold) {
        if (threshold <= 0.0) {
            return clamp(value);
        }

        return clamp(value / threshold);
    }

    private static double requireRatio(double value, String fieldName) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }

        return value;
    }

    private static double clamp(double value) {
        if (value < 0.0) {
            return 0.0;
        }

        if (value > 1.0) {
            return 1.0;
        }

        return value;
    }
}