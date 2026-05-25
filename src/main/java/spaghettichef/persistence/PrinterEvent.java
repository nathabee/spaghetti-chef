package spaghettichef.persistence;

import spaghettichef.OperationMessages;

import java.time.Instant;

public final class PrinterEvent {

    private final long id;
    private final String printerId;
    private final String jobId;
    private final String eventType;
    private final String message;
    private final Instant createdAt;

    public PrinterEvent(
            long id,
            String printerId,
            String jobId,
            String eventType,
            String message,
            Instant createdAt
    ) {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.EVENT_TYPE_MUST_NOT_BE_BLANK);
        }
        if (createdAt == null) {
            throw new IllegalArgumentException(OperationMessages.CREATED_AT_MUST_NOT_BE_NULL);
        }

        this.id = id;
        this.printerId = normalizeOptional(printerId);
        this.jobId = normalizeOptional(jobId);
        this.eventType = eventType.trim();
        this.message = normalizeOptional(message);
        this.createdAt = createdAt;
    }

    public static PrinterEvent create(
            String printerId,
            String jobId,
            String eventType,
            String message
    ) {
        return new PrinterEvent(
                0L,
                printerId,
                jobId,
                eventType,
                message,
                Instant.now()
        );
    }

    public long id() {
        return id;
    }

    public String printerId() {
        return printerId;
    }

    public String jobId() {
        return jobId;
    }

    public String eventType() {
        return eventType;
    }

    public String message() {
        return message;
    }

    public Instant createdAt() {
        return createdAt;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}