package spaghettichef.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ImageDeltaFrameAnalyzerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-05-19T10:00:00Z"),
            ZoneOffset.UTC);

    @TempDir
    Path tempDirectory;

    @Test
    void analyzeSkipsWhenPreviousFrameIsMissing() throws IOException {
        Path previous = tempDirectory.resolve("previous.jpg");
        Path latest = tempDirectory.resolve("latest.jpg");

        writeSolidImage(latest, Color.BLACK);

        ImageDeltaFrameAnalyzer analyzer = new ImageDeltaFrameAnalyzer(
                35,
                0.08,
                true,
                FIXED_CLOCK);

        FrameAnalysisResult result = analyzer.analyze(
                "p1",
                previous,
                latest,
                Optional.of(tempDirectory.resolve("delta.jpg")));

        assertFalse(result.completed());
        assertEquals("p1", result.printerId());
        assertTrue(result.reasons().contains(FrameAnalysisReason.ANALYSIS_SKIPPED));
        assertTrue(result.reasons().contains(FrameAnalysisReason.MISSING_PREVIOUS_FRAME));
        assertEquals(Instant.parse("2026-05-19T10:00:00Z"), result.analyzedAt());
    }

    @Test
    void analyzeSkipsWhenLatestFrameIsMissing() throws IOException {
        Path previous = tempDirectory.resolve("previous.jpg");
        Path latest = tempDirectory.resolve("latest.jpg");

        writeSolidImage(previous, Color.BLACK);

        ImageDeltaFrameAnalyzer analyzer = new ImageDeltaFrameAnalyzer(
                35,
                0.08,
                true,
                FIXED_CLOCK);

        FrameAnalysisResult result = analyzer.analyze(
                "p1",
                previous,
                latest,
                Optional.of(tempDirectory.resolve("delta.jpg")));

        assertFalse(result.completed());
        assertEquals("p1", result.printerId());
        assertTrue(result.reasons().contains(FrameAnalysisReason.ANALYSIS_SKIPPED));
        assertTrue(result.reasons().contains(FrameAnalysisReason.MISSING_LATEST_FRAME));
    }

    @Test
    void analyzeReturnsLowDeltaForIdenticalImages() throws IOException {
        Path previous = tempDirectory.resolve("previous.jpg");
        Path latest = tempDirectory.resolve("latest.jpg");
        Path delta = tempDirectory.resolve("delta.jpg");

        writeSolidImage(previous, Color.BLACK);
        writeSolidImage(latest, Color.BLACK);

        ImageDeltaFrameAnalyzer analyzer = new ImageDeltaFrameAnalyzer(
                35,
                0.08,
                true,
                FIXED_CLOCK);

        FrameAnalysisResult result = analyzer.analyze(
                "p1",
                previous,
                latest,
                Optional.of(delta));

        assertTrue(result.completed());
        assertEquals("p1", result.printerId());
        assertEquals(0.0, result.deltaScore(), 0.0001);
        assertEquals(0.0, result.changedPixelRatio(), 0.0001);
        assertEquals(0.0, result.averagePixelDelta(), 0.0001);
        assertEquals(0, result.changedPixelCount());
        assertEquals(100, result.comparedPixelCount());
        assertTrue(result.reasons().contains(FrameAnalysisReason.ANALYSIS_COMPLETED));
        assertTrue(result.reasons().contains(FrameAnalysisReason.LOW_VISUAL_DELTA));
        assertTrue(result.reasons().contains(FrameAnalysisReason.DELTA_IMAGE_WRITTEN));
        assertTrue(delta.toFile().isFile());
    }

    @Test
    void analyzeReturnsHighDeltaForDifferentImages() throws IOException {
        Path previous = tempDirectory.resolve("previous.jpg");
        Path latest = tempDirectory.resolve("latest.jpg");
        Path delta = tempDirectory.resolve("delta.jpg");

        writeSolidImage(previous, Color.BLACK);
        writeSolidImage(latest, Color.WHITE);

        ImageDeltaFrameAnalyzer analyzer = new ImageDeltaFrameAnalyzer(
                35,
                0.08,
                true,
                FIXED_CLOCK);

        FrameAnalysisResult result = analyzer.analyze(
                "p1",
                previous,
                latest,
                Optional.of(delta));

        assertTrue(result.completed());
        assertEquals("p1", result.printerId());
        assertTrue(result.deltaScore() >= 0.99);
        assertTrue(result.changedPixelRatio() >= 0.99);
        assertTrue(result.averagePixelDelta() >= 0.99);
        assertEquals(100, result.changedPixelCount());
        assertEquals(100, result.comparedPixelCount());
        assertTrue(result.reasons().contains(FrameAnalysisReason.ANALYSIS_COMPLETED));
        assertTrue(result.reasons().contains(FrameAnalysisReason.HIGH_VISUAL_DELTA));
        assertTrue(result.reasons().contains(FrameAnalysisReason.DELTA_IMAGE_WRITTEN));
        assertTrue(delta.toFile().isFile());
    }

    @Test
    void analyzeWorksWithoutDeltaImageOutput() throws IOException {
        Path previous = tempDirectory.resolve("previous.jpg");
        Path latest = tempDirectory.resolve("latest.jpg");

        writeSolidImage(previous, Color.BLACK);
        writeSolidImage(latest, Color.WHITE);

        ImageDeltaFrameAnalyzer analyzer = new ImageDeltaFrameAnalyzer(
                35,
                0.08,
                false,
                FIXED_CLOCK);

        FrameAnalysisResult result = analyzer.analyze(
                "p1",
                previous,
                latest,
                Optional.empty());

        assertTrue(result.completed());
        assertTrue(result.reasons().contains(FrameAnalysisReason.HIGH_VISUAL_DELTA));
        assertFalse(result.reasons().contains(FrameAnalysisReason.DELTA_IMAGE_WRITTEN));
        assertTrue(result.deltaOutputPath().isEmpty());
    }

    private static void writeSolidImage(Path path, Color color) throws IOException {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, color.getRGB());
            }
        }

        ImageIO.write(image, "jpg", path.toFile());
    }
}