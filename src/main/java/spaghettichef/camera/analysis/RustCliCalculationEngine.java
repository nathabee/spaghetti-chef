package spaghettichef.camera.analysis;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

import spaghettichef.persistence.CameraSnapshotEntry;
import spaghettichef.persistence.CameraSnapshotEntryStore;
import spaghettichef.persistence.CameraDeltaFrame;

public final class RustCliCalculationEngine implements SpaghettiCalculationEngine {

    private final Path executablePath;
    private final String cliMethod;
    private final Duration timeout;
    private final CameraSnapshotEntryStore snapshotEntryStore;
    private final RustCliAnalyzerProcess analyzerProcess;

    public RustCliCalculationEngine(Path executablePath) {
        this(executablePath, null, Duration.ofSeconds(10));
    }

    public RustCliCalculationEngine(Path executablePath, String cliMethod, Duration timeout) {
        this(
                executablePath,
                cliMethod,
                timeout,
                new CameraSnapshotEntryStore(),
                new RustCliAnalyzerProcess());
    }

    public RustCliCalculationEngine(
            Path executablePath,
            String cliMethod,
            Duration timeout,
            CameraSnapshotEntryStore snapshotEntryStore,
            RustCliAnalyzerProcess analyzerProcess) {
        this.executablePath = executablePath;
        this.cliMethod = cliMethod == null || cliMethod.isBlank()
                ? RustCliAnalyzerRequest.DEFAULT_METHOD
                : cliMethod.trim();
        this.timeout = timeout == null || timeout.isZero() || timeout.isNegative()
                ? Duration.ofSeconds(10)
                : timeout;
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
                cliMethod,
                confidenceThreshold,
                timeout));

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
}
