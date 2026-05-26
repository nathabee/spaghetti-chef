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

final class RustCliAnalyzerProcessTest {

    @Test
    void commandForBuildsSafeArgumentList() {
        RustCliAnalyzerProcess process = new RustCliAnalyzerProcess();
        RustCliAnalyzerRequest request = new RustCliAnalyzerRequest(
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
        RustCliAnalyzerProcess process = new RustCliAnalyzerProcess();

        RustCliAnalyzerResponse response = process.analyze(requestFor("fake-rust-analyzer-success.sh"));

        assertEquals(RustCliAnalyzerExitCode.SUCCESS, response.exitCode());
        assertEquals("RUST_CLI_DELTA", response.engineName());
        assertEquals("0.5.3", response.engineVersion());
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
        RustCliAnalyzerProcess process = new RustCliAnalyzerProcess();

        RustCliAnalyzerException exception = assertThrows(
                RustCliAnalyzerException.class,
                () -> process.analyze(requestFor("fake-rust-analyzer-failure.sh")));

        assertEquals(RustCliAnalyzerExitCode.IMAGE_SIZE_MISMATCH, exception.exitCode());
        assertTrue(exception.stderr().contains("fake analyzer failed"));
    }

    @Test
    void analyzeThrowsForInvalidJson() {
        RustCliAnalyzerProcess process = new RustCliAnalyzerProcess();

        RustCliAnalyzerException exception = assertThrows(
                RustCliAnalyzerException.class,
                () -> process.analyze(requestFor("fake-rust-analyzer-invalid-json.sh")));

        assertEquals(RustCliAnalyzerExitCode.SUCCESS, exception.exitCode());
        assertTrue(exception.stdout().contains("not json"));
        assertTrue(exception.getMessage().contains("invalid JSON"));
    }

    @Test
    void analyzeThrowsForTimeout() {
        RustCliAnalyzerProcess process = new RustCliAnalyzerProcess();
        RustCliAnalyzerRequest request = new RustCliAnalyzerRequest(
                scriptPath("fake-rust-analyzer-sleep.sh"),
                Path.of("from.jpg"),
                Path.of("to.jpg"),
                null,
                "delta-basic",
                0.65,
                Duration.ofMillis(100));

        RustCliAnalyzerException exception = assertThrows(
                RustCliAnalyzerException.class,
                () -> process.analyze(request));

        assertEquals(RustCliAnalyzerExitCode.UNKNOWN, exception.exitCode());
        assertTrue(exception.getMessage().contains("timed out"));
    }

    @Test
    void requestDoesNotRequireDeltaFrame() {
        RustCliAnalyzerRequest request = RustCliAnalyzerRequest.of(
                Path.of("img-analyzer"),
                Path.of("from.jpg"),
                Path.of("to.jpg"));

        assertFalse(request.deltaFramePath().isPresent());
        assertEquals(RustCliAnalyzerRequest.DEFAULT_METHOD, request.method());
        assertEquals(RustCliAnalyzerRequest.DEFAULT_THRESHOLD, request.threshold(), 0.0001);
    }

    private static RustCliAnalyzerRequest requestFor(String resourceName) {
        return new RustCliAnalyzerRequest(
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
                    RustCliAnalyzerProcessTest.class
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
