package spaghettichef.camera.analysis;

public final class CalculationEngineRegistry {

    public SpaghettiCalculationEngine resolve(CalculationEngineName engineName) {
        return resolve(new CalculationEngineConfiguration(engineName, null, null, null));
    }

    public SpaghettiCalculationEngine resolve(CalculationEngineConfiguration configuration) {
        CalculationEngineConfiguration resolved = configuration == null
                ? new CalculationEngineConfiguration(CalculationEngineName.JAVA_BASIC_DELTA, null, null, null)
                : configuration;
        return switch (resolved.engineName()) {
            case JAVA_BASIC_DELTA -> new JavaBasicDeltaCalculationEngine();
            case RUST_CLI_DELTA -> new RustCliCalculationEngine(
                    resolved.executablePath(),
                    resolved.cliMethod(),
                    resolved.timeout());
            case DISABLED -> throw new IllegalArgumentException("calculation engine is disabled");
        };
    }
}
