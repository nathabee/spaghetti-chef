package printerhub.camera;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class FrameAnalysisResult {

    private final String printerId;
    private final boolean completed;
    private final double deltaScore;
    private final double changedPixelRatio;
    private final double averagePixelDelta;
    private final int changedPixelCount;
    private final int comparedPixelCount;
    private final Path previousFramePath;
    private final Path latestFramePath;
    private final Path deltaOutputPath;
    private final Set<FrameAnalysisReason> reasons;
    private final String message;
    private final Instant analyzedAt;

    private FrameAnalysisResult(
            String printerId,
            boolean completed,
            double deltaScore,
            double changedPixelRatio,
            double averagePixelDelta,
            int changedPixelCount,
            int comparedPixelCount,
            Path previousFramePath,
            Path latestFramePath,
            Path deltaOutputPath,
            Set<FrameAnalysisReason> reasons,
            String message,
            Instant analyzedAt) {
        this.printerId = requirePrinterId(printerId);
        this.completed = completed;
        this.deltaScore = requireRatio(deltaScore, "deltaScore");
        this.changedPixelRatio = requireRatio(changedPixelRatio, "changedPixelRatio");
        this.averagePixelDelta = requireRatio(averagePixelDelta, "averagePixelDelta");
        this.changedPixelCount = requireNonNegative(changedPixelCount, "changedPixelCount");
        this.comparedPixelCount = requireNonNegative(comparedPixelCount, "comparedPixelCount");
        this.previousFramePath = previousFramePath;
        this.latestFramePath = latestFramePath;
        this.deltaOutputPath = deltaOutputPath;
        this.reasons = immutableReasons(reasons);
        this.message = normalizeNullableText(message);
        this.analyzedAt = Objects.requireNonNull(analyzedAt, "analyzedAt");
    }

    public static FrameAnalysisResult completed(
            String printerId,
            double deltaScore,
            double changedPixelRatio,
            double averagePixelDelta,
            int changedPixelCount,
            int comparedPixelCount,
            Path previousFramePath,
            Path latestFramePath,
            Path deltaOutputPath,
            Set<FrameAnalysisReason> reasons,
            String message,
            Instant analyzedAt) {
        EnumSet<FrameAnalysisReason> completedReasons = copyReasons(reasons);
        completedReasons.add(FrameAnalysisReason.ANALYSIS_COMPLETED);

        return new FrameAnalysisResult(
                printerId,
                true,
                deltaScore,
                changedPixelRatio,
                averagePixelDelta,
                changedPixelCount,
                comparedPixelCount,
                previousFramePath,
                latestFramePath,
                deltaOutputPath,
                completedReasons,
                message,
                analyzedAt);
    }

    public static FrameAnalysisResult skipped(
            String printerId,
            Path previousFramePath,
            Path latestFramePath,
            Set<FrameAnalysisReason> reasons,
            String message,
            Instant analyzedAt) {
        EnumSet<FrameAnalysisReason> skippedReasons = copyReasons(reasons);
        skippedReasons.add(FrameAnalysisReason.ANALYSIS_SKIPPED);

        return new FrameAnalysisResult(
                printerId,
                false,
                0.0,
                0.0,
                0.0,
                0,
                0,
                previousFramePath,
                latestFramePath,
                null,
                skippedReasons,
                message,
                analyzedAt);
    }

    public static FrameAnalysisResult failed(
            String printerId,
            Path previousFramePath,
            Path latestFramePath,
            Set<FrameAnalysisReason> reasons,
            String message,
            Instant analyzedAt) {
        return new FrameAnalysisResult(
                printerId,
                false,
                0.0,
                0.0,
                0.0,
                0,
                0,
                previousFramePath,
                latestFramePath,
                null,
                reasons,
                message,
                analyzedAt);
    }

    public String printerId() {
        return printerId;
    }

    public boolean completed() {
        return completed;
    }

    public double deltaScore() {
        return deltaScore;
    }

    public double changedPixelRatio() {
        return changedPixelRatio;
    }

    public double averagePixelDelta() {
        return averagePixelDelta;
    }

    public int changedPixelCount() {
        return changedPixelCount;
    }

    public int comparedPixelCount() {
        return comparedPixelCount;
    }

    public Optional<Path> previousFramePath() {
        return Optional.ofNullable(previousFramePath);
    }

    public Optional<Path> latestFramePath() {
        return Optional.ofNullable(latestFramePath);
    }

    public Optional<Path> deltaOutputPath() {
        return Optional.ofNullable(deltaOutputPath);
    }

    public Set<FrameAnalysisReason> reasons() {
        return reasons;
    }

    public Optional<String> message() {
        return Optional.ofNullable(message);
    }

    public Instant analyzedAt() {
        return analyzedAt;
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

    private static int requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }

        return value;
    }

    private static Set<FrameAnalysisReason> immutableReasons(Set<FrameAnalysisReason> reasons) {
        return Collections.unmodifiableSet(copyReasons(reasons));
    }

    private static EnumSet<FrameAnalysisReason> copyReasons(Set<FrameAnalysisReason> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return EnumSet.noneOf(FrameAnalysisReason.class);
        }

        return EnumSet.copyOf(reasons);
    }

    private static String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}