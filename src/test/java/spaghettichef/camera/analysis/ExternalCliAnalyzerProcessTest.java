package spaghettichef.camera.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

final class ExternalCliAnalyzerProcessTest {

    @Test
    void commandForBuildsSafeArgumentList() {
        ExternalCliAnalyzerProcess process = new ExternalCliAnalyzerProcess();
        ExternalCliAnalyzerRequest request = new ExternalCliAnalyzerRequest(
                Path.of("/opt/spaghetti chef/img-analyzer"),
                Path.of("/tmp/from snapshot.jpg"),
                Path.of("/tmp/to snapshot.jpg"),
                Path.of("/tmp/delta frame.jpg"),
                "delta-basic",
                0.65,
                Duration.ofSeconds(5));

        assertEquals(
                List.of(
                        "/opt/spaghetti chef/img-analyzer",
                        "--from-snapshot",
                        "/tmp/from snapshot.jpg",
                        "--to-snapshot",
                        "/tmp/to snapshot.jpg",
                        "--delta-frame",
                        "/tmp/delta frame.jpg",
                        "--method",
                        "delta-basic",
                        "--threshold",
                        "0.65"),
                process.commandFor(request));
    }

    @Test
    void analyzeParsesSuccessfulJsonAndCapturesStderr() {
        ExternalCliAnalyzerProcess process = new ExternalCliAnalyzerProcess();

        ExternalCliAnalyzerResponse response = process.analyze(requestFor("fake-rust-analyzer-success.sh"));

        assertEquals(ExternalCliAnalyzerExitCode.SUCCESS, response.exitCode());
        assertEquals("RUST_CLI_DELTA", response.engineName());
        assertEquals("0.5.6", response.engineVersion());
        assertEquals("FRAME_DELTA", response.algorithmVariant());
        assertEquals(0.78, response.confidence(), 0.0001);
        assertTrue(response.suspected());
        assertEquals(List.of("large_delta_area", "high_average_pixel_delta"), response.reasonCodes());
        assertEquals(0.34, response.changedPixelRatio(), 0.0001);
        assertEquals(0.27, response.averagePixelDelta(), 0.0001);
        assertTrue(response.stderr().contains("fake analyzer diagnostic"));
        assertTrue(response.stdout().contains("\"engineName\""));
    }

    @Test
    void analyzeThrowsForNonZeroExitAndKeepsStderr() {
        ExternalCliAnalyzerProcess process = new ExternalCliAnalyzerProcess();

        ExternalCliAnalyzerException exception = assertThrows(
                ExternalCliAnalyzerException.class,
                () -> process.analyze(requestFor("fake-rust-analyzer-failure.sh")));

        assertEquals(ExternalCliAnalyzerExitCode.IMAGE_SIZE_MISMATCH, exception.exitCode());
        assertTrue(exception.stderr().contains("fake analyzer failed"));
    }

    @Test
    void analyzeThrowsForInvalidJson() {
        ExternalCliAnalyzerProcess process = new ExternalCliAnalyzerProcess();

        ExternalCliAnalyzerException exception = assertThrows(
                ExternalCliAnalyzerException.class,
                () -> process.analyze(requestFor("fake-rust-analyzer-invalid-json.sh")));

        assertEquals(ExternalCliAnalyzerExitCode.SUCCESS, exception.exitCode());
        assertTrue(exception.stdout().contains("not json"));
        assertTrue(exception.getMessage().contains("invalid JSON"));
    }

    @Test
    void analyzeThrowsForTimeout() {
        ExternalCliAnalyzerProcess process = new ExternalCliAnalyzerProcess();
        ExternalCliAnalyzerRequest request = new ExternalCliAnalyzerRequest(
                scriptPath("fake-rust-analyzer-sleep.sh"),
                Path.of("from.jpg"),
                Path.of("to.jpg"),
                null,
                "delta-basic",
                0.65,
                Duration.ofMillis(100));

        ExternalCliAnalyzerException exception = assertThrows(
                ExternalCliAnalyzerException.class,
                () -> process.analyze(request));

        assertEquals(ExternalCliAnalyzerExitCode.UNKNOWN, exception.exitCode());
        assertTrue(exception.getMessage().contains("timed out"));
    }

    @Test
    void requestDoesNotRequireDeltaFrame() {
        ExternalCliAnalyzerRequest request = ExternalCliAnalyzerRequest.of(
                Path.of("img-analyzer"),
                Path.of("from.jpg"),
                Path.of("to.jpg"));

        assertFalse(request.deltaFramePath().isPresent());
        assertEquals(ExternalCliAnalyzerRequest.DEFAULT_METHOD, request.method());
        assertEquals(ExternalCliAnalyzerRequest.DEFAULT_THRESHOLD, request.threshold(), 0.0001);
    }

    private static ExternalCliAnalyzerRequest requestFor(String resourceName) {
        return new ExternalCliAnalyzerRequest(
                scriptPath(resourceName),
                Path.of("from.jpg"),
                Path.of("to.jpg"),
                Path.of("delta.jpg"),
                "delta-basic",
                0.65,
                Duration.ofSeconds(5));
    }

    private static Path scriptPath(String resourceName) {
        try {
            Path path = Path.of(
                    ExternalCliAnalyzerProcessTest.class
                            .getClassLoader()
                            .getResource(resourceName)
                            .toURI());
            path.toFile().setExecutable(true);
            return path;
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Invalid test resource URI", exception);
        }
    }
}
