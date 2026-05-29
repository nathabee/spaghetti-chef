package spaghettichef.camera.analysis;

public final class CalculationEngineRegistry {

    public SpaghettiCalculationEngine resolve(CalculationEngineName engineName) {
        CalculationEngineAdapterType adapterType = engineName == CalculationEngineName.RUST_CLI_DELTA
                ? CalculationEngineAdapterType.EXTERNAL_CLI
                : CalculationEngineAdapterType.JAVA_BASIC_DELTA;
        return resolve(new CalculationEngineConfiguration(adapterType, null, null, null));
    }

    public SpaghettiCalculationEngine resolve(CalculationEngineConfiguration configuration) {
        CalculationEngineConfiguration resolved = configuration == null
                ? new CalculationEngineConfiguration(CalculationEngineAdapterType.JAVA_BASIC_DELTA, null, null, null)
                : configuration;
        return switch (resolved.adapterType()) {
            case JAVA_BASIC_DELTA -> new JavaBasicDeltaCalculationEngine();
            case EXTERNAL_CLI -> new RustCliCalculationEngine(
                    resolved.executablePath(),
                    resolved.cliMethod(),
                    resolved.timeout());
        };
    }
}
