package spaghettichef.camera.analysis;

import spaghettichef.persistence.CameraDeltaFrame;

public final class RustCliCalculationEngine implements SpaghettiCalculationEngine {

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
        return CalculationEngineStatus.UNAVAILABLE;
    }

    @Override
    public CalculationEngineResult analyze(CameraDeltaFrame frame, double confidenceThreshold) {
        throw new IllegalStateException("Rust CLI calculation engine is not configured yet");
    }
}
