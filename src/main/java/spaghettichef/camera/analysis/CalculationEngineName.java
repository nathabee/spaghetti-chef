package spaghettichef.camera.analysis;

public enum CalculationEngineName {
    DISABLED,
    JAVA_BASIC_DELTA,
    RUST_CLI_DELTA;

    public static CalculationEngineName fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            return JAVA_BASIC_DELTA;
        }

        String normalized = value.trim().replace('-', '_').toUpperCase(java.util.Locale.ROOT);
        for (CalculationEngineName engineName : values()) {
            if (engineName.name().equals(normalized)) {
                return engineName;
            }
        }

        throw new IllegalArgumentException("unsupported calculation engine: " + value);
    }
}
