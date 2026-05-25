package spaghettichef.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

public final class CameraEvent {

    private final Long id;
    private final String printerId;
    private final String eventType;
    private final String message;
    private final Double confidence;
    private final Instant createdAt;

    public CameraEvent(
            Long id,
            String printerId,
            String eventType,
            String message,
            Double confidence,
            Instant createdAt) {
        this.id = id;
        this.printerId = requireText(printerId, "printerId");
        this.eventType = requireText(eventType, "eventType");
        this.message = requireText(message, "message");
        this.confidence = validateConfidence(confidence);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static CameraEvent newEvent(
            String printerId,
            String eventType,
            String message,
            Double confidence,
            Instant createdAt) {
        return new CameraEvent(null, printerId, eventType, message, confidence, createdAt);
    }

    public Optional<Long> id() {
        return Optional.ofNullable(id);
    }

    public String printerId() {
        return printerId;
    }

    public String eventType() {
        return eventType;
    }

    public String message() {
        return message;
    }

    public OptionalDouble confidence() {
        if (confidence == null) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(confidence);
    }

    public Instant createdAt() {
        return createdAt;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static Double validateConfidence(Double value) {
        if (value == null) {
            return null;
        }
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        return value;
    }
}