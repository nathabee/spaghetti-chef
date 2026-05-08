package printerhub.job;

import printerhub.OperationMessages;

import java.time.Instant;

public final class PrintJob {

    private final String id;
    private final String name;
    private final JobType type;
    private final JobState state;
    private final String printerId;
    private final String printFileId;
    private final String printerSdFileId;
    private final Double targetTemperature;
    private final Integer fanSpeed;
    private final String failureReason;
    private final String failureDetail;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant startedAt;
    private final Instant finishedAt;

    public PrintJob(
            String id,
            String name,
            JobType type,
            JobState state,
            String printerId,
            Double targetTemperature,
            Integer fanSpeed,
            String failureReason,
            String failureDetail,
            Instant createdAt,
            Instant updatedAt,
            Instant startedAt,
            Instant finishedAt
    ) {
        this(
                id,
                name,
                type,
                state,
                printerId,
                null,
                null,
                targetTemperature,
                fanSpeed,
                failureReason,
                failureDetail,
                createdAt,
                updatedAt,
                startedAt,
                finishedAt
        );
    }

    public PrintJob(
            String id,
            String name,
            JobType type,
            JobState state,
            String printerId,
            String printFileId,
            String printerSdFileId,
            Double targetTemperature,
            Integer fanSpeed,
            String failureReason,
            String failureDetail,
            Instant createdAt,
            Instant updatedAt,
            Instant startedAt,
            Instant finishedAt
    ) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.JOB_ID_MUST_NOT_BE_BLANK);
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.JOB_NAME_MUST_NOT_BE_BLANK);
        }
        if (type == null) {
            throw new IllegalArgumentException(OperationMessages.JOB_TYPE_MUST_NOT_BE_NULL);
        }
        if (state == null) {
            throw new IllegalArgumentException(OperationMessages.JOB_STATE_MUST_NOT_BE_NULL);
        }
        if (createdAt == null) {
            throw new IllegalArgumentException(OperationMessages.CREATED_AT_MUST_NOT_BE_NULL);
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException(OperationMessages.UPDATED_AT_MUST_NOT_BE_NULL);
        }

        this.id = id.trim();
        this.name = name.trim();
        this.type = type;
        this.state = state;
        this.printerId = printerId == null || printerId.isBlank() ? null : printerId.trim();
        this.printFileId = printFileId == null || printFileId.isBlank() ? null : printFileId.trim();
        this.printerSdFileId = printerSdFileId == null || printerSdFileId.isBlank() ? null : printerSdFileId.trim();
        this.targetTemperature = targetTemperature;
        this.fanSpeed = fanSpeed;
        this.failureReason = failureReason == null || failureReason.isBlank() ? null : failureReason.trim();
        this.failureDetail = failureDetail == null || failureDetail.isBlank() ? null : failureDetail.trim();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    public static PrintJob created(
            String id,
            String name,
            JobType type,
            String printerId,
            Double targetTemperature,
            Integer fanSpeed,
            Instant now
    ) {
        return created(id, name, type, printerId, null, null, targetTemperature, fanSpeed, now);
    }

    public static PrintJob created(
            String id,
            String name,
            JobType type,
            String printerId,
            String printFileId,
            Double targetTemperature,
            Integer fanSpeed,
            Instant now
    ) {
        return created(id, name, type, printerId, printFileId, null, targetTemperature, fanSpeed, now);
    }

    public static PrintJob created(
            String id,
            String name,
            JobType type,
            String printerId,
            String printFileId,
            String printerSdFileId,
            Double targetTemperature,
            Integer fanSpeed,
            Instant now
    ) {
        if (now == null) {
            throw new IllegalArgumentException(OperationMessages.CREATED_AT_MUST_NOT_BE_NULL);
        }

        return new PrintJob(
                id,
                name,
                type,
                printerId == null || printerId.isBlank() ? JobState.CREATED : JobState.ASSIGNED,
                printerId,
                printFileId,
                printerSdFileId,
                targetTemperature,
                fanSpeed,
                null,
                null,
                now,
                now,
                null,
                null
        );
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public JobType type() {
        return type;
    }

    public JobState state() {
        return state;
    }

    public String printerId() {
        return printerId;
    }

    public String printFileId() {
        return printFileId;
    }

    public String printerSdFileId() {
        return printerSdFileId;
    }

    public Double targetTemperature() {
        return targetTemperature;
    }

    public Integer fanSpeed() {
        return fanSpeed;
    }

    public String failureReason() {
        return failureReason;
    }

    public String failureDetail() {
        return failureDetail;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    public PrintJob withState(JobState newState, Instant updatedAt) {
        if (newState == null) {
            throw new IllegalArgumentException(OperationMessages.JOB_STATE_MUST_NOT_BE_NULL);
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException(OperationMessages.UPDATED_AT_MUST_NOT_BE_NULL);
        }

        return new PrintJob(
                id,
                name,
                type,
                newState,
                printerId,
                printFileId,
                printerSdFileId,
                targetTemperature,
                fanSpeed,
                failureReason,
                failureDetail,
                createdAt,
                updatedAt,
                startedAt,
                finishedAt
        );
    }

    public PrintJob withStartedAt(Instant startedAt, Instant updatedAt) {
        if (startedAt == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("startedAt"));
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException(OperationMessages.UPDATED_AT_MUST_NOT_BE_NULL);
        }

        return new PrintJob(
                id,
                name,
                type,
                JobState.RUNNING,
                printerId,
                printFileId,
                printerSdFileId,
                targetTemperature,
                fanSpeed,
                null,
                null,
                createdAt,
                updatedAt,
                startedAt,
                finishedAt
        );
    }

    public PrintJob completed(Instant finishedAt, Instant updatedAt) {
        if (finishedAt == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("finishedAt"));
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException(OperationMessages.UPDATED_AT_MUST_NOT_BE_NULL);
        }

        return new PrintJob(
                id,
                name,
                type,
                JobState.COMPLETED,
                printerId,
                printFileId,
                printerSdFileId,
                targetTemperature,
                fanSpeed,
                null,
                null,
                createdAt,
                updatedAt,
                startedAt,
                finishedAt
        );
    }

    public PrintJob failed(
            String failureReason,
            String failureDetail,
            Instant finishedAt,
            Instant updatedAt
    ) {
        if (updatedAt == null) {
            throw new IllegalArgumentException(OperationMessages.UPDATED_AT_MUST_NOT_BE_NULL);
        }

        return new PrintJob(
                id,
                name,
                type,
                JobState.FAILED,
                printerId,
                printFileId,
                printerSdFileId,
                targetTemperature,
                fanSpeed,
                failureReason,
                failureDetail,
                createdAt,
                updatedAt,
                startedAt,
                finishedAt
        );
    }

    public PrintJob cancelled(Instant finishedAt, Instant updatedAt) {
        if (updatedAt == null) {
            throw new IllegalArgumentException(OperationMessages.UPDATED_AT_MUST_NOT_BE_NULL);
        }

        return new PrintJob(
                id,
                name,
                type,
                JobState.CANCELLED,
                printerId,
                printFileId,
                printerSdFileId,
                targetTemperature,
                fanSpeed,
                failureReason,
                failureDetail,
                createdAt,
                updatedAt,
                startedAt,
                finishedAt
        );
    }

    public PrintJob paused(Instant updatedAt) {
        if (updatedAt == null) {
            throw new IllegalArgumentException(OperationMessages.UPDATED_AT_MUST_NOT_BE_NULL);
        }

        return new PrintJob(
                id,
                name,
                type,
                JobState.PAUSED,
                printerId,
                printFileId,
                printerSdFileId,
                targetTemperature,
                fanSpeed,
                failureReason,
                failureDetail,
                createdAt,
                updatedAt,
                startedAt,
                finishedAt
        );
    }

    public PrintJob cancelling(Instant updatedAt) {
        if (updatedAt == null) {
            throw new IllegalArgumentException(OperationMessages.UPDATED_AT_MUST_NOT_BE_NULL);
        }

        return new PrintJob(
                id,
                name,
                type,
                JobState.CANCELLING,
                printerId,
                printFileId,
                printerSdFileId,
                targetTemperature,
                fanSpeed,
                failureReason,
                failureDetail,
                createdAt,
                updatedAt,
                startedAt,
                finishedAt
        );
    }
}
