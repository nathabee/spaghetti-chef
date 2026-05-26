package spaghettichef.camera.analysis;

import java.util.List;

public record CalculationEngineResult(
        double confidence,
        boolean suspected,
        List<String> reasonCodes,
        String message,
        String engineVersion
) {
    public CalculationEngineResult(
            double confidence,
            boolean suspected,
            List<String> reasonCodes,
            String message) {
        this(confidence, suspected, reasonCodes, message, null);
    }

    public CalculationEngineResult {
        confidence = requireRatio(confidence, "confidence");
        reasonCodes = List.copyOf(reasonCodes == null ? List.of() : reasonCodes);
        message = message == null || message.isBlank() ? null : message.trim();
        engineVersion = engineVersion == null || engineVersion.isBlank() ? null : engineVersion.trim();
    }

    public String reasonCodesText() {
        if (reasonCodes.isEmpty()) {
            return null;
        }
        return String.join(",", reasonCodes);
    }

    private static double requireRatio(double value, String fieldName) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
        return value;
    }
}
