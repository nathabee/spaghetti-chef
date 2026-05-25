package spaghettichef.camera;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public final class CameraFrame {

    private final String printerId;
    private final Instant capturedAt;
    private final String contentType;
    private final byte[] bytes;
    private final Integer width;
    private final Integer height;
    private final String sourceDescription;

    public CameraFrame(
            String printerId,
            Instant capturedAt,
            String contentType,
            byte[] bytes,
            Integer width,
            Integer height,
            String sourceDescription) {
        this.printerId = requireText(printerId, "printerId");
        this.capturedAt = Objects.requireNonNull(capturedAt, "capturedAt");
        this.contentType = requireText(contentType, "contentType");
        this.bytes = copyNonEmpty(bytes);
        this.width = validateDimension(width, "width");
        this.height = validateDimension(height, "height");
        this.sourceDescription = normalizeNullableText(sourceDescription);
    }

    public static CameraFrame jpeg(
            String printerId,
            Instant capturedAt,
            byte[] bytes,
            Integer width,
            Integer height,
            String sourceDescription) {
        return new CameraFrame(
                printerId,
                capturedAt,
                "image/jpeg",
                bytes,
                width,
                height,
                sourceDescription);
    }

    public static CameraFrame png(
            String printerId,
            Instant capturedAt,
            byte[] bytes,
            Integer width,
            Integer height,
            String sourceDescription) {
        return new CameraFrame(
                printerId,
                capturedAt,
                "image/png",
                bytes,
                width,
                height,
                sourceDescription);
    }

    public String printerId() {
        return printerId;
    }

    public Instant capturedAt() {
        return capturedAt;
    }

    public String contentType() {
        return contentType;
    }

    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    public int byteCount() {
        return bytes.length;
    }

    public OptionalInt width() {
        if (width == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(width);
    }

    public OptionalInt height() {
        if (height == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(height);
    }

    public Optional<String> sourceDescription() {
        return Optional.ofNullable(sourceDescription);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static byte[] copyNonEmpty(byte[] value) {
        if (value == null || value.length == 0) {
            throw new IllegalArgumentException("bytes must not be empty");
        }
        return Arrays.copyOf(value, value.length);
    }

    private static Integer validateDimension(Integer value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero");
        }
        return value;
    }

    private static String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}