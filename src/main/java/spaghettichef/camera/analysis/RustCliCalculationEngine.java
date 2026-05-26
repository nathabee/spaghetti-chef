package spaghettichef.camera.analysis;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

import spaghettichef.config.RuntimeDefaults;
import spaghettichef.persistence.CameraSnapshotEntry;
import spaghettichef.persistence.CameraSnapshotEntryStore;
import spaghettichef.persistence.CameraDeltaFrame;

public final class RustCliCalculationEngine implements SpaghettiCalculationEngine {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final Path executablePath;
    private final CameraSnapshotEntryStore snapshotEntryStore;
    private final RustCliAnalyzerProcess analyzerProcess;

    public RustCliCalculationEngine(Path executablePath) {
        this(
                executablePath == null ? configuredExecutablePath() : executablePath,
                new CameraSnapshotEntryStore(),
                new RustCliAnalyzerProcess());
    }

    public RustCliCalculationEngine(
            Path executablePath,
            CameraSnapshotEntryStore snapshotEntryStore,
            RustCliAnalyzerProcess analyzerProcess) {
        this.executablePath = executablePath;
        this.snapshotEntryStore = Objects.requireNonNull(snapshotEntryStore, "snapshotEntryStore");
        this.analyzerProcess = Objects.requireNonNull(analyzerProcess, "analyzerProcess");
    }

    @Override
    public CalculationEngineName engineName() {
        return CalculationEngineName.RUST_CLI_DELTA;
    }

    @Override
    public String algorithmVariant() {
        return "FRAME_DELTA";
    }

    @Override
    public String engineVersion() {
        return null;
    }

    @Override
    public CalculationEngineStatus status() {
        if (executablePath == null || !Files.isRegularFile(executablePath) || !Files.isExecutable(executablePath)) {
            return CalculationEngineStatus.UNAVAILABLE;
        }
        return CalculationEngineStatus.SUCCESS;
    }

    @Override
    public CalculationEngineResult analyze(CameraDeltaFrame frame, double confidenceThreshold) {
        if (status() != CalculationEngineStatus.SUCCESS) {
            throw new IllegalStateException("Rust CLI calculation engine is not configured yet");
        }

        CameraSnapshotEntry fromSnapshot = snapshotEntryStore.findById(frame.fromSnapshotId())
                .orElseThrow(() -> new IllegalStateException("source snapshot not found: " + frame.fromSnapshotId()));
        CameraSnapshotEntry toSnapshot = snapshotEntryStore.findById(frame.toSnapshotId())
                .orElseThrow(() -> new IllegalStateException("target snapshot not found: " + frame.toSnapshotId()));

        RustCliAnalyzerResponse response = analyzerProcess.analyze(new RustCliAnalyzerRequest(
                executablePath,
                Path.of(fromSnapshot.snapshotPath()),
                Path.of(toSnapshot.snapshotPath()),
                Path.of(frame.deltaPath()),
                "delta-basic",
                confidenceThreshold,
                DEFAULT_TIMEOUT));

        return new CalculationEngineResult(
                response.confidence(),
                response.suspected(),
                response.reasonCodes(),
                response.message(),
                response.engineVersion());
    }

    public Path executablePath() {
        return executablePath;
    }

    private static Path configuredExecutablePath() {
        String configured = System.getProperty(RuntimeDefaults.RUST_ANALYZER_EXECUTABLE_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return null;
        }
        return Path.of(configured.trim());
    }
}
