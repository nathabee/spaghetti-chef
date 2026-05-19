package printerhub.camera;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.imageio.ImageIO;

import printerhub.config.RuntimeDefaults;

public final class ImageDeltaFrameAnalyzer implements FrameAnalyzer {

    private final int deltaPixelThreshold;
    private final double deltaScoreThreshold;
    private final boolean deltaImageEnabled;
    private final Clock clock;

    public ImageDeltaFrameAnalyzer() {
        this(
                RuntimeDefaults.DEFAULT_CAMERA_DELTA_PIXEL_THRESHOLD,
                RuntimeDefaults.DEFAULT_CAMERA_DELTA_SCORE_THRESHOLD,
                RuntimeDefaults.DEFAULT_CAMERA_DELTA_IMAGE_ENABLED,
                Clock.systemUTC());
    }

    public ImageDeltaFrameAnalyzer(
            int deltaPixelThreshold,
            double deltaScoreThreshold,
            boolean deltaImageEnabled,
            Clock clock) {
        this.deltaPixelThreshold = requirePixelThreshold(deltaPixelThreshold);
        this.deltaScoreThreshold = requireRatio(deltaScoreThreshold, "deltaScoreThreshold");
        this.deltaImageEnabled = deltaImageEnabled;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public FrameAnalysisResult analyze(
            String printerId,
            Path previousFramePath,
            Path latestFramePath,
            Optional<Path> deltaOutputPath) {
        String normalizedPrinterId = requirePrinterId(printerId);
        Path previousPath = Objects.requireNonNull(previousFramePath, "previousFramePath");
        Path latestPath = Objects.requireNonNull(latestFramePath, "latestFramePath");
        Optional<Path> safeDeltaOutputPath = Objects.requireNonNull(deltaOutputPath, "deltaOutputPath");
        Instant analyzedAt = Instant.now(clock);

        if (!Files.isRegularFile(previousPath)) {
            return FrameAnalysisResult.skipped(
                    normalizedPrinterId,
                    previousPath,
                    latestPath,
                    Set.of(FrameAnalysisReason.MISSING_PREVIOUS_FRAME),
                    "Previous camera frame is not available",
                    analyzedAt);
        }

        if (!Files.isRegularFile(latestPath)) {
            return FrameAnalysisResult.skipped(
                    normalizedPrinterId,
                    previousPath,
                    latestPath,
                    Set.of(FrameAnalysisReason.MISSING_LATEST_FRAME),
                    "Latest camera frame is not available",
                    analyzedAt);
        }

        BufferedImage previousImage;
        BufferedImage latestImage;

        try {
            previousImage = ImageIO.read(previousPath.toFile());
            latestImage = ImageIO.read(latestPath.toFile());
        } catch (IOException exception) {
            return FrameAnalysisResult.failed(
                    normalizedPrinterId,
                    previousPath,
                    latestPath,
                    Set.of(FrameAnalysisReason.IMAGE_READ_FAILED),
                    "Failed to read camera frames: " + exception.getMessage(),
                    analyzedAt);
        }

        if (previousImage == null || latestImage == null) {
            return FrameAnalysisResult.failed(
                    normalizedPrinterId,
                    previousPath,
                    latestPath,
                    Set.of(FrameAnalysisReason.IMAGE_READ_FAILED),
                    "Failed to decode camera frames",
                    analyzedAt);
        }

        int width = Math.min(previousImage.getWidth(), latestImage.getWidth());
        int height = Math.min(previousImage.getHeight(), latestImage.getHeight());

        if (width <= 0 || height <= 0) {
            return FrameAnalysisResult.failed(
                    normalizedPrinterId,
                    previousPath,
                    latestPath,
                    Set.of(FrameAnalysisReason.NO_COMPARABLE_PIXELS),
                    "No comparable pixels between camera frames",
                    analyzedAt);
        }

        EnumSet<FrameAnalysisReason> reasons = EnumSet.noneOf(FrameAnalysisReason.class);

        if (previousImage.getWidth() != latestImage.getWidth()
                || previousImage.getHeight() != latestImage.getHeight()) {
            reasons.add(FrameAnalysisReason.IMAGE_DIMENSION_MISMATCH);
        }

        AnalysisMetrics metrics = compareImages(previousImage, latestImage, width, height);
        double changedPixelRatio = ratio(metrics.changedPixelCount(), metrics.comparedPixelCount());
        double averagePixelDelta = metrics.totalNormalizedDelta() / metrics.comparedPixelCount();
        double deltaScore = clamp((changedPixelRatio * 0.75) + (averagePixelDelta * 0.25));

        if (deltaScore >= deltaScoreThreshold) {
            reasons.add(FrameAnalysisReason.HIGH_VISUAL_DELTA);
        } else {
            reasons.add(FrameAnalysisReason.LOW_VISUAL_DELTA);
        }

        Path writtenDeltaPath = null;

        if (deltaImageEnabled && safeDeltaOutputPath.isPresent()) {
            try {
                writeDeltaImage(previousImage, latestImage, width, height, safeDeltaOutputPath.get());
                writtenDeltaPath = safeDeltaOutputPath.get();
                reasons.add(FrameAnalysisReason.DELTA_IMAGE_WRITTEN);
            } catch (IOException exception) {
                reasons.add(FrameAnalysisReason.DELTA_IMAGE_WRITE_FAILED);
            }
        }

        return FrameAnalysisResult.completed(
                normalizedPrinterId,
                deltaScore,
                changedPixelRatio,
                averagePixelDelta,
                metrics.changedPixelCount(),
                metrics.comparedPixelCount(),
                previousPath,
                latestPath,
                writtenDeltaPath,
                reasons,
                "Camera frame analysis completed",
                analyzedAt);
    }

    private AnalysisMetrics compareImages(
            BufferedImage previousImage,
            BufferedImage latestImage,
            int width,
            int height) {
        int changedPixelCount = 0;
        int comparedPixelCount = width * height;
        double totalNormalizedDelta = 0.0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int previousRgb = previousImage.getRGB(x, y);
                int latestRgb = latestImage.getRGB(x, y);

                int redDelta = Math.abs(red(previousRgb) - red(latestRgb));
                int greenDelta = Math.abs(green(previousRgb) - green(latestRgb));
                int blueDelta = Math.abs(blue(previousRgb) - blue(latestRgb));

                int averageDelta = (redDelta + greenDelta + blueDelta) / 3;
                totalNormalizedDelta += averageDelta / 255.0;

                if (averageDelta >= deltaPixelThreshold) {
                    changedPixelCount++;
                }
            }
        }

        return new AnalysisMetrics(changedPixelCount, comparedPixelCount, totalNormalizedDelta);
    }

    private void writeDeltaImage(
            BufferedImage previousImage,
            BufferedImage latestImage,
            int width,
            int height,
            Path outputPath) throws IOException {
        BufferedImage deltaImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int previousRgb = previousImage.getRGB(x, y);
                int latestRgb = latestImage.getRGB(x, y);

                int redDelta = Math.abs(red(previousRgb) - red(latestRgb));
                int greenDelta = Math.abs(green(previousRgb) - green(latestRgb));
                int blueDelta = Math.abs(blue(previousRgb) - blue(latestRgb));

                int averageDelta = (redDelta + greenDelta + blueDelta) / 3;
                int gray = Math.min(255, averageDelta);
                int rgb = (gray << 16) | (gray << 8) | gray;

                deltaImage.setRGB(x, y, rgb);
            }
        }

        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        ImageIO.write(deltaImage, "jpg", outputPath.toFile());
    }

    private static int red(int rgb) {
        return (rgb >> 16) & 0xFF;
    }

    private static int green(int rgb) {
        return (rgb >> 8) & 0xFF;
    }

    private static int blue(int rgb) {
        return rgb & 0xFF;
    }

    private static double ratio(int value, int total) {
        if (total <= 0) {
            return 0.0;
        }

        return clamp((double) value / (double) total);
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

    private static int requirePixelThreshold(int value) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException("deltaPixelThreshold must be between 0 and 255");
        }

        return value;
    }

    private static double requireRatio(double value, String fieldName) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }

        return value;
    }

    private static String requirePrinterId(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException("printerId must not be blank");
        }

        return printerId.trim();
    }

    private record AnalysisMetrics(
            int changedPixelCount,
            int comparedPixelCount,
            double totalNormalizedDelta) {
    }
}