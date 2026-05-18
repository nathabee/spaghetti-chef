package printerhub.camera;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class CameraStatus {

    private final String printerId;
    private final boolean enabled;
    private final boolean available;
    private final CameraSourceType sourceType;
    private final String sourceValue;
    private final String sourceDescription;
    private final Instant lastCaptureAt;
    private final String lastError;

    public CameraStatus(
            String printerId,
            boolean enabled,
            boolean available,
            CameraSourceType sourceType,
            String sourceValue,
            String sourceDescription,
            Instant lastCaptureAt,
            String lastError) {
        this.printerId = requireText(printerId, "printerId");
        this.enabled = enabled;
        this.available = available;
        this.sourceType = Objects.requireNonNull(sourceType, "sourceType");
        this.sourceValue = normalizeNullableText(sourceValue);
        this.sourceDescription = normalizeNullableText(sourceDescription);
        this.lastCaptureAt = lastCaptureAt;
        this.lastError = normalizeNullableText(lastError);
    }

    public static CameraStatus disabled(String printerId) {
        return new CameraStatus(
                printerId,
                false,
                false,
                CameraSourceType.DISABLED,
                null,
                "camera-disabled",
                null,
                null);
    }

    public static CameraStatus unavailable(
            String printerId,
            CameraSourceType sourceType,
            String sourceValue,
            String sourceDescription,
            String lastError) {
        return new CameraStatus(
                printerId,
                true,
                false,
                sourceType,
                sourceValue,
                sourceDescription,
                null,
                lastError);
    }

    public static CameraStatus available(
            String printerId,
            CameraSourceType sourceType,
            String sourceValue,
            String sourceDescription,
            Instant lastCaptureAt) {
        return new CameraStatus(
                printerId,
                true,
                true,
                sourceType,
                sourceValue,
                sourceDescription,
                lastCaptureAt,
                null);
    }

    public String printerId() {
        return printerId;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean available() {
        return available;
    }

    public CameraSourceType sourceType() {
        return sourceType;
    }

    public Optional<String> sourceValue() {
        return Optional.ofNullable(sourceValue);
    }

    public Optional<String> sourceDescription() {
        return Optional.ofNullable(sourceDescription);
    }

    public Optional<Instant> lastCaptureAt() {
        return Optional.ofNullable(lastCaptureAt);
    }

    public Optional<String> lastError() {
        return Optional.ofNullable(lastError);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}