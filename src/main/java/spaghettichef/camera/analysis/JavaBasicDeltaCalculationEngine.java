package spaghettichef.camera.analysis;

import java.util.List;

import spaghettichef.AppVersion;
import spaghettichef.persistence.CameraDeltaFrame;

public final class JavaBasicDeltaCalculationEngine implements SpaghettiCalculationEngine {

    public static final String ALGORITHM_VARIANT = "DELTA_SCORE_THRESHOLD";

    @Override
    public CalculationEngineName engineName() {
        return CalculationEngineName.JAVA_BASIC_DELTA;
    }

    @Override
    public String algorithmVariant() {
        return ALGORITHM_VARIANT;
    }

    @Override
    public String engineVersion() {
        return AppVersion.current();
    }

    @Override
    public CalculationEngineStatus status() {
        return CalculationEngineStatus.SUCCESS;
    }

    @Override
    public CalculationEngineResult analyze(CameraDeltaFrame frame, double confidenceThreshold) {
        double confidence = frame.deltaScore();
        boolean suspected = confidence >= confidenceThreshold;
        return new CalculationEngineResult(
                confidence,
                suspected,
                List.of(suspected ? "HIGH_VISUAL_DELTA" : "LOW_VISUAL_DELTA"),
                suspected ? "Delta score meets threshold" : "Delta score below threshold");
    }
}
