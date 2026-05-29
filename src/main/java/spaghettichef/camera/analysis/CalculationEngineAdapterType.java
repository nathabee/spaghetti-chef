package spaghettichef.camera.analysis;

public enum CalculationEngineAdapterType {
    JAVA_BASIC_DELTA,
    EXTERNAL_CLI;

    public static CalculationEngineAdapterType fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            return JAVA_BASIC_DELTA;
        }

        String normalized = value.trim().replace('-', '_').toUpperCase(java.util.Locale.ROOT);
        for (CalculationEngineAdapterType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }

        throw new IllegalArgumentException("unsupported calculation engine adapter type: " + value);
    }
}
