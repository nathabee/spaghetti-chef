package spaghettichef.camera.analysis;

public final class CalculationEngineRegistry {

    public SpaghettiCalculationEngine resolve(CalculationEngineConfiguration configuration) {
        CalculationEngineConfiguration resolved = configuration == null
                ? new CalculationEngineConfiguration(CalculationEngineAdapterType.JAVA_BASIC_DELTA, null, null, null)
                : configuration;
        return switch (resolved.adapterType()) {
            case JAVA_BASIC_DELTA -> new JavaBasicDeltaCalculationEngine();
            case EXTERNAL_CLI -> new ExternalCliCalculationEngine(
                    resolved.executablePath(),
                    resolved.cliMethod(),
                    resolved.timeout());
        };
    }
}
