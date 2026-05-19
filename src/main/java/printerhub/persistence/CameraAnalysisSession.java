package printerhub.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class CameraAnalysisSession {

    private final String id;
    private final String printerId;
    private final CameraAnalysisSessionState state;
    private final Instant startedAt;
    private final Instant stoppedAt;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String message;

    public CameraAnalysisSession(
            String id,
            String printerId,
            CameraAnalysisSessionState state,
            Instant startedAt,
            Instant stoppedAt,
            Instant createdAt,
            Instant updatedAt,
            String message) {
        this.id = requireText(id, "id");
        this.printerId = requireText(printerId, "printerId");
        this.state = Objects.requireNonNull(state, "state");
        this.startedAt = startedAt;
        this.stoppedAt = stoppedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.message = normalize(message);
    }

    public String id() {
        return id;
    }

    public String printerId() {
        return printerId;
    }

    public CameraAnalysisSessionState state() {
        return state;
    }

    public Optional<Instant> startedAt() {
        return Optional.ofNullable(startedAt);
    }

    public Optional<Instant> stoppedAt() {
        return Optional.ofNullable(stoppedAt);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Optional<String> message() {
        return Optional.ofNullable(message);
    }

    public boolean running() {
        return state == CameraAnalysisSessionState.RUNNING;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
