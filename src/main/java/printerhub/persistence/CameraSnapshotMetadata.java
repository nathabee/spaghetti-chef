package printerhub.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public final class CameraSnapshotMetadata {

    private final Long id;
    private final String printerId;
    private final Instant capturedAt;
    private final String contentType;
    private final String filePath;
    private final Integer width;
    private final Integer height;
    private final String sourceDescription;

    public CameraSnapshotMetadata(
            Long id,
            String printerId,
            Instant capturedAt,
            String contentType,
            String filePath,
            Integer width,
            Integer height,
            String sourceDescription) {
        this.id = id;
        this.printerId = requireText(printerId, "printerId");
        this.capturedAt = Objects.requireNonNull(capturedAt, "capturedAt");
        this.contentType = requireText(contentType, "contentType");
        this.filePath = requireText(filePath, "filePath");
        this.width = validateDimension(width, "width");
        this.height = validateDimension(height, "height");
        this.sourceDescription = normalizeNullableText(sourceDescription);
    }

    public static CameraSnapshotMetadata newSnapshot(
            String printerId,
            Instant capturedAt,
            String contentType,
            String filePath,
            Integer width,
            Integer height,
            String sourceDescription) {
        return new CameraSnapshotMetadata(
                null,
                printerId,
                capturedAt,
                contentType,
                filePath,
                width,
                height,
                sourceDescription);
    }

    public Optional<Long> id() {
        return Optional.ofNullable(id);
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

    public String filePath() {
        return filePath;
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