package spaghettichef.job;

import spaghettichef.OperationMessages;

import java.time.Instant;

public final class PrintJobExecutionStep {

    private final long id;
    private final String jobId;
    private final int stepIndex;
    private final String stepName;
    private final String wireCommand;
    private final String response;
    private final String outcome;
    private final boolean success;
    private final String failureReason;
    private final String failureDetail;
    private final Instant createdAt;

    public PrintJobExecutionStep(
            long id,
            String jobId,
            int stepIndex,
            String stepName,
            String wireCommand,
            String response,
            String outcome,
            boolean success,
            String failureReason,
            String failureDetail,
            Instant createdAt
    ) {
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.JOB_ID_MUST_NOT_BE_BLANK);
        }
        if (stepIndex < 0) {
            throw new IllegalArgumentException(OperationMessages.invalidEnumField("stepIndex", String.valueOf(stepIndex)));
        }
        if (stepName == null || stepName.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("stepName"));
        }
        if (outcome == null || outcome.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("outcome"));
        }
        if (createdAt == null) {
            throw new IllegalArgumentException(OperationMessages.CREATED_AT_MUST_NOT_BE_NULL);
        }

        this.id = id;
        this.jobId = jobId.trim();
        this.stepIndex = stepIndex;
        this.stepName = stepName.trim();
        this.wireCommand = normalizeOptional(wireCommand);
        this.response = normalizeOptional(response);
        this.outcome = outcome.trim();
        this.success = success;
        this.failureReason = normalizeOptional(failureReason);
        this.failureDetail = normalizeOptional(failureDetail);
        this.createdAt = createdAt;
    }

    public static PrintJobExecutionStep create(
            String jobId,
            int stepIndex,
            String stepName,
            String wireCommand,
            String response,
            String outcome,
            boolean success,
            String failureReason,
            String failureDetail
    ) {
        return new PrintJobExecutionStep(
                0L,
                jobId,
                stepIndex,
                stepName,
                wireCommand,
                response,
                outcome,
                success,
                failureReason,
                failureDetail,
                Instant.now()
        );
    }

    public static PrintJobExecutionStep success(
            String jobId,
            int stepIndex,
            String stepName,
            String wireCommand,
            String response,
            String outcome
    ) {
        return create(
                jobId,
                stepIndex,
                stepName,
                wireCommand,
                response,
                outcome,
                true,
                null,
                null
        );
    }

    public static PrintJobExecutionStep failure(
            String jobId,
            int stepIndex,
            String stepName,
            String wireCommand,
            String response,
            String outcome,
            JobFailureReason failureReason,
            String failureDetail
    ) {
        return create(
                jobId,
                stepIndex,
                stepName,
                wireCommand,
                response,
                outcome,
                false,
                failureReason == null ? null : failureReason.name(),
                failureDetail
        );
    }

    public long id() {
        return id;
    }

    public String jobId() {
        return jobId;
    }

    public int stepIndex() {
        return stepIndex;
    }

    public String stepName() {
        return stepName;
    }

    public String wireCommand() {
        return wireCommand;
    }

    public String response() {
        return response;
    }

    public String outcome() {
        return outcome;
    }

    public boolean success() {
        return success;
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

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}