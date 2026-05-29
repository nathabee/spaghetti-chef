package spaghettichef.camera.analysis;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public final class ExternalCliAnalyzerRequest {

    public static final String DEFAULT_METHOD = "delta-basic";
    public static final double DEFAULT_THRESHOLD = 0.65;
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final Path executablePath;
    private final Path fromSnapshotPath;
    private final Path toSnapshotPath;
    private final Path deltaFramePath;
    private final String method;
    private final double threshold;
    private final Duration timeout;

    public ExternalCliAnalyzerRequest(
            Path executablePath,
            Path fromSnapshotPath,
            Path toSnapshotPath,
            Path deltaFramePath,
            String method,
            double threshold,
            Duration timeout) {
        this.executablePath = Objects.requireNonNull(executablePath, "executablePath");
        this.fromSnapshotPath = Objects.requireNonNull(fromSnapshotPath, "fromSnapshotPath");
        this.toSnapshotPath = Objects.requireNonNull(toSnapshotPath, "toSnapshotPath");
        this.deltaFramePath = deltaFramePath;
        this.method = normalizeMethod(method);
        this.threshold = requireThreshold(threshold);
        this.timeout = requireTimeout(timeout);
    }

    public static ExternalCliAnalyzerRequest of(
            Path executablePath,
            Path fromSnapshotPath,
            Path toSnapshotPath) {
        return new ExternalCliAnalyzerRequest(
                executablePath,
                fromSnapshotPath,
                toSnapshotPath,
                null,
                DEFAULT_METHOD,
                DEFAULT_THRESHOLD,
                DEFAULT_TIMEOUT);
    }

    public Path executablePath() {
        return executablePath;
    }

    public Path fromSnapshotPath() {
        return fromSnapshotPath;
    }

    public Path toSnapshotPath() {
        return toSnapshotPath;
    }

    public Optional<Path> deltaFramePath() {
        return Optional.ofNullable(deltaFramePath);
    }

    public String method() {
        return method;
    }

    public double threshold() {
        return threshold;
    }

    public Duration timeout() {
        return timeout;
    }

    private static String normalizeMethod(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_METHOD;
        }
        return value.trim();
    }

    private static double requireThreshold(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("threshold must be between 0.0 and 1.0");
        }
        return value;
    }

    private static Duration requireTimeout(Duration value) {
        Duration selected = value == null ? DEFAULT_TIMEOUT : value;
        if (selected.isZero() || selected.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        return selected;
    }
}
