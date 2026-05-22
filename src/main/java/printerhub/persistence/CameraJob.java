package printerhub.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class CameraJob {

    private final Long id;
    private final String printerId;
    private final String linkedPrintJobId;
    private final String analysisSessionId;
    private final CameraJobState state;
    private final Instant startedAt;
    private final Instant stoppedAt;
    private final int captureIntervalSeconds;
    private final int retainedSnapshots;
    private final String sourceType;
    private final String sourceDescription;
    private final String snapshotDirectory;
    private final String message;
    private final Instant createdAt;
    private final Instant updatedAt;

    public CameraJob(
            Long id,
            String printerId,
            String linkedPrintJobId,
            String analysisSessionId,
            CameraJobState state,
            Instant startedAt,
            Instant stoppedAt,
            int captureIntervalSeconds,
            int retainedSnapshots,
            String sourceType,
            String sourceDescription,
            String snapshotDirectory,
            String message,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.printerId = requireText(printerId, "printerId");
        this.linkedPrintJobId = normalizeNullableText(linkedPrintJobId);
        this.analysisSessionId = normalizeNullableText(analysisSessionId);
        this.state = Objects.requireNonNull(state, "state");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.stoppedAt = stoppedAt;
        this.captureIntervalSeconds = requirePositive(captureIntervalSeconds, "captureIntervalSeconds");
        this.retainedSnapshots = requirePositive(retainedSnapshots, "retainedSnapshots");
        this.sourceType = requireText(sourceType, "sourceType");
        this.sourceDescription = normalizeNullableText(sourceDescription);
        this.snapshotDirectory = requireText(snapshotDirectory, "snapshotDirectory");
        this.message = normalizeNullableText(message);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static CameraJob running(
            String printerId,
            String linkedPrintJobId,
            String analysisSessionId,
            Instant startedAt,
            int captureIntervalSeconds,
            int retainedSnapshots,
            String sourceType,
            String sourceDescription,
            String snapshotDirectory,
            String message) {
        Instant now = Objects.requireNonNull(startedAt, "startedAt");

        return new CameraJob(
                null,
                printerId,
                linkedPrintJobId,
                analysisSessionId,
                CameraJobState.RUNNING,
                now,
                null,
                captureIntervalSeconds,
                retainedSnapshots,
                sourceType,
                sourceDescription,
                snapshotDirectory,
                message,
                now,
                now);
    }

    public CameraJob withId(long id) {
        if (id <= 0L) {
            throw new IllegalArgumentException("id must be greater than zero");
        }

        return new CameraJob(
                id,
                printerId,
                linkedPrintJobId,
                analysisSessionId,
                state,
                startedAt,
                stoppedAt,
                captureIntervalSeconds,
                retainedSnapshots,
                sourceType,
                sourceDescription,
                snapshotDirectory,
                message,
                createdAt,
                updatedAt);
    }

    public CameraJob withSnapshotDirectory(String newSnapshotDirectory, Instant updatedAt) {
        return new CameraJob(
                id,
                printerId,
                linkedPrintJobId,
                analysisSessionId,
                state,
                startedAt,
                stoppedAt,
                captureIntervalSeconds,
                retainedSnapshots,
                sourceType,
                sourceDescription,
                newSnapshotDirectory,
                message,
                createdAt,
                Objects.requireNonNull(updatedAt, "updatedAt"));
    }

    public CameraJob stopped(CameraJobState terminalState, Instant stoppedAt, String message) {
        if (terminalState == CameraJobState.RUNNING) {
            throw new IllegalArgumentException("terminalState must not be RUNNING");
        }

        Instant normalizedStoppedAt = Objects.requireNonNull(stoppedAt, "stoppedAt");

        return new CameraJob(
                id,
                printerId,
                linkedPrintJobId,
                analysisSessionId,
                terminalState,
                startedAt,
                normalizedStoppedAt,
                captureIntervalSeconds,
                retainedSnapshots,
                sourceType,
                sourceDescription,
                snapshotDirectory,
                message,
                createdAt,
                normalizedStoppedAt);
    }

    public Optional<Long> id() {
        return Optional.ofNullable(id);
    }

    public long requireId() {
        if (id == null || id <= 0L) {
            throw new IllegalStateException("camera job id is not assigned");
        }

        return id;
    }

    public String printerId() {
        return printerId;
    }

    public Optional<String> linkedPrintJobId() {
        return Optional.ofNullable(linkedPrintJobId);
    }

    public Optional<String> analysisSessionId() {
        return Optional.ofNullable(analysisSessionId);
    }

    public CameraJobState state() {
        return state;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Optional<Instant> stoppedAt() {
        return Optional.ofNullable(stoppedAt);
    }

    public int captureIntervalSeconds() {
        return captureIntervalSeconds;
    }

    public int retainedSnapshots() {
        return retainedSnapshots;
    }

    public String sourceType() {
        return sourceType;
    }

    public Optional<String> sourceDescription() {
        return Optional.ofNullable(sourceDescription);
    }

    public String snapshotDirectory() {
        return snapshotDirectory;
    }

    public Optional<String> message() {
        return Optional.ofNullable(message);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
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

    private static int requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero");
        }

        return value;
    }
}