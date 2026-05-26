package spaghettichef.camera.analysis;

public final class CalculationEngineRegistry {

    public SpaghettiCalculationEngine resolve(CalculationEngineName engineName) {
        return switch (engineName == null ? CalculationEngineName.JAVA_BASIC_DELTA : engineName) {
            case JAVA_BASIC_DELTA -> new JavaBasicDeltaCalculationEngine();
            case RUST_CLI_DELTA -> new RustCliCalculationEngine();
            case DISABLED -> throw new IllegalArgumentException("calculation engine is disabled");
        };
    }
}
