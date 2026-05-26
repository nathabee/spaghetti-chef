package spaghettichef.camera.analysis;

import java.nio.file.Path;

public final class CalculationEngineRegistry {

    public SpaghettiCalculationEngine resolve(CalculationEngineName engineName) {
        return resolve(engineName, null);
    }

    public SpaghettiCalculationEngine resolve(CalculationEngineName engineName, Path rustExecutablePath) {
        return switch (engineName == null ? CalculationEngineName.JAVA_BASIC_DELTA : engineName) {
            case JAVA_BASIC_DELTA -> new JavaBasicDeltaCalculationEngine();
            case RUST_CLI_DELTA -> new RustCliCalculationEngine(rustExecutablePath);
            case DISABLED -> throw new IllegalArgumentException("calculation engine is disabled");
        };
    }
}
