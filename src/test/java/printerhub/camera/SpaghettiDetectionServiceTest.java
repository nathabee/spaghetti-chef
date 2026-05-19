package printerhub.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.Test;

final class SpaghettiDetectionServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-05-19T10:00:00Z"),
            ZoneOffset.UTC);

    @Test
    void detectSkipsWhenFrameAnalysisWasNotCompleted() {
        FrameAnalysisResult analysis = FrameAnalysisResult.skipped(
                "p1",
                Path.of("previous.jpg"),
                Path.of("latest.jpg"),
                Set.of(FrameAnalysisReason.MISSING_PREVIOUS_FRAME),
                "Previous frame missing",
                Instant.parse("2026-05-19T09:59:00Z"));

        SpaghettiDetectionService service = new SpaghettiDetectionService(
                0.08,
                0.65,
                FIXED_CLOCK);

        SpaghettiDetectionResult result = service.detect(analysis);

        assertEquals("p1", result.printerId());
        assertFalse(result.suspected());
        assertEquals(0.0, result.confidence(), 0.0001);
        assertTrue(result.reasons().contains(SpaghettiDetectionReason.ANALYSIS_NOT_COMPLETED));
        assertEquals(analysis, result.frameAnalysis());
        assertEquals(Instant.parse("2026-05-19T10:00:00Z"), result.detectedAt());
    }

    @Test
    void detectReturnsNoSuspicionForLowDelta() {
        FrameAnalysisResult analysis = FrameAnalysisResult.completed(
                "p1",
                0.01,
                0.01,
                0.01,
                1,
                100,
                Path.of("previous.jpg"),
                Path.of("latest.jpg"),
                null,
                Set.of(FrameAnalysisReason.LOW_VISUAL_DELTA),
                "Low delta",
                Instant.parse("2026-05-19T09:59:00Z"));

        SpaghettiDetectionService service = new SpaghettiDetectionService(
                0.08,
                0.65,
                FIXED_CLOCK);

        SpaghettiDetectionResult result = service.detect(analysis);

        assertEquals("p1", result.printerId());
        assertFalse(result.suspected());
        assertTrue(result.confidence() < 0.65);
        assertTrue(result.reasons().contains(SpaghettiDetectionReason.LOW_VISUAL_DELTA));
        assertTrue(result.reasons().contains(SpaghettiDetectionReason.INSUFFICIENT_EVIDENCE));
    }

    @Test
    void detectReturnsSuspicionForHighDelta() {
        FrameAnalysisResult analysis = FrameAnalysisResult.completed(
                "p1",
                0.90,
                0.90,
                0.90,
                90,
                100,
                Path.of("previous.jpg"),
                Path.of("latest.jpg"),
                Path.of("delta.jpg"),
                Set.of(FrameAnalysisReason.HIGH_VISUAL_DELTA),
                "High delta",
                Instant.parse("2026-05-19T09:59:00Z"));

        SpaghettiDetectionService service = new SpaghettiDetectionService(
                0.08,
                0.65,
                FIXED_CLOCK);

        SpaghettiDetectionResult result = service.detect(analysis);

        assertEquals("p1", result.printerId());
        assertTrue(result.suspected());
        assertTrue(result.confidence() >= 0.65);
        assertTrue(result.reasons().contains(SpaghettiDetectionReason.HIGH_VISUAL_DELTA));
        assertTrue(result.reasons().contains(SpaghettiDetectionReason.EXCESSIVE_CHANGED_AREA));
        assertTrue(result.reasons().contains(SpaghettiDetectionReason.POSSIBLE_SPAGHETTI_PATTERN));
        assertEquals(analysis, result.frameAnalysis());
    }

    @Test
    void constructorRejectsInvalidThresholds() {
        assertThrowsInvalidThreshold(-0.01, 0.65);
        assertThrowsInvalidThreshold(1.01, 0.65);
        assertThrowsInvalidThreshold(0.08, -0.01);
        assertThrowsInvalidThreshold(0.08, 1.01);
    }

    private static void assertThrowsInvalidThreshold(double deltaScoreThreshold, double confidenceThreshold) {
        try {
            new SpaghettiDetectionService(deltaScoreThreshold, confidenceThreshold, FIXED_CLOCK);
        } catch (IllegalArgumentException expected) {
            return;
        }

        throw new AssertionError("Expected IllegalArgumentException");
    }
}