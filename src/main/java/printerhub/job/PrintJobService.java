package printerhub.job;

import printerhub.OperationMessages;
import printerhub.persistence.PrintJobStore;
import printerhub.persistence.PrinterEventStore;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PrintJobService {

    private final PrintJobStore printJobStore;
    private final PrinterEventStore eventStore;
    private final Clock clock;

    public PrintJobService(
            PrintJobStore printJobStore,
            PrinterEventStore eventStore) {
        this(
                printJobStore,
                eventStore,
                Clock.systemUTC());
    }

    public PrintJobService(
            PrintJobStore printJobStore,
            PrinterEventStore eventStore,
            Clock clock) {
        if (printJobStore == null) {
            throw new IllegalArgumentException(OperationMessages.PRINT_JOB_STORE_MUST_NOT_BE_NULL);
        }
        if (eventStore == null) {
            throw new IllegalArgumentException(OperationMessages.EVENT_STORE_MUST_NOT_BE_NULL);
        }
        if (clock == null) {
            throw new IllegalArgumentException(OperationMessages.CLOCK_MUST_NOT_BE_NULL);
        }

        this.printJobStore = printJobStore;
        this.eventStore = eventStore;
        this.clock = clock;
    }

    public PrintJob create(
            String name,
            JobType type,
            String printerId,
            Double targetTemperature,
            Integer fanSpeed) {
        return create(name, type, printerId, null, null, targetTemperature, fanSpeed);
    }

    public PrintJob create(
            String name,
            JobType type,
            String printerId,
            String printFileId,
            Double targetTemperature,
            Integer fanSpeed) {
        return create(name, type, printerId, printFileId, null, targetTemperature, fanSpeed);
    }

    public PrintJob create(
            String name,
            JobType type,
            String printerId,
            String printFileId,
            String printerSdFileId,
            Double targetTemperature,
            Integer fanSpeed) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.JOB_NAME_MUST_NOT_BE_BLANK);
        }
        if (type == null) {
            throw new IllegalArgumentException(OperationMessages.JOB_TYPE_MUST_NOT_BE_NULL);
        }
        if (type == JobType.PRINT_FILE && (printerSdFileId == null || printerSdFileId.isBlank())) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_SD_FILE_ID_MUST_NOT_BE_BLANK);
        }

        Instant now = Instant.now(clock);

        PrintJob job = PrintJob.created(
                UUID.randomUUID().toString(),
                name,
                type,
                printerId,
                printFileId,
                printerSdFileId,
                targetTemperature,
                fanSpeed,
                now);

        printJobStore.save(job);

        recordEvent(
                job.printerId(),
                job.id(),
                OperationMessages.EVENT_JOB_CREATED,
                "Job created: " + job.id());

        if (job.printerId() != null) {
            recordEvent(
                    job.printerId(),
                    job.id(),
                    OperationMessages.EVENT_JOB_ASSIGNED,
                    "Job assigned to printer: " + job.printerId());
        }

        return job;
    }

    public Optional<PrintJob> findById(String jobId) {
        return printJobStore.findById(jobId);
    }

    public List<PrintJob> findRecent(int limit) {
        return printJobStore.findRecent(limit);
    }

    public PrintJob markRunning(String jobId) {
        PrintJob job = loadRequired(jobId);
        Instant now = Instant.now(clock);

        PrintJob updated = job.withStartedAt(now, now);
        printJobStore.update(updated);

        recordEvent(
                updated.printerId(),
                updated.id(),
                OperationMessages.EVENT_JOB_STARTED,
                "Job started: " + updated.id());

        return updated;
    }

    public PrintJob markPaused(String jobId) {
        PrintJob job = loadRequired(jobId);
        Instant now = Instant.now(clock);

        PrintJob updated = job.paused(now);
        printJobStore.update(updated);

        recordEvent(
                updated.printerId(),
                updated.id(),
                OperationMessages.EVENT_JOB_PAUSED,
                "Job paused: " + updated.id());

        return updated;
    }

    public PrintJob markResumed(String jobId) {
        PrintJob job = loadRequired(jobId);
        Instant now = Instant.now(clock);

        PrintJob updated = job.withState(JobState.RUNNING, now);
        printJobStore.update(updated);

        recordEvent(
                updated.printerId(),
                updated.id(),
                OperationMessages.EVENT_JOB_RESUMED,
                "Job resumed: " + updated.id());

        return updated;
    }

    public PrintJob markCancelling(String jobId) {
        PrintJob job = loadRequired(jobId);
        Instant now = Instant.now(clock);

        PrintJob updated = job.cancelling(now);
        printJobStore.update(updated);

        recordEvent(
                updated.printerId(),
                updated.id(),
                OperationMessages.EVENT_JOB_CANCELLING,
                "Job cancelling: " + updated.id());

        return updated;
    }

    public PrintJob markCompleted(String jobId) {
        PrintJob job = loadRequired(jobId);
        Instant now = Instant.now(clock);

        PrintJob updated = job.completed(now, now);
        printJobStore.update(updated);

        recordEvent(
                updated.printerId(),
                updated.id(),
                OperationMessages.EVENT_JOB_COMPLETED,
                "Job completed: " + updated.id());

        return updated;
    }

    public PrintJob markFailed(
            String jobId,
            JobFailureReason failureReason,
            String failureDetail) {
        PrintJob job = loadRequired(jobId);
        Instant now = Instant.now(clock);

        PrintJob updated = job.failed(
                failureReason == null ? null : failureReason.name(),
                failureDetail,
                now,
                now);
        printJobStore.update(updated);

        recordEvent(
                updated.printerId(),
                updated.id(),
                OperationMessages.EVENT_JOB_FAILED,
                "Job failed: " + updated.id() + " -> "
                        + OperationMessages.safeDetail(failureDetail, JobFailureReason.UNKNOWN.name()));

        return updated;
    }

    public PrintJob cancel(String jobId) {
        PrintJob job = loadRequired(jobId);
        if (isTerminal(job.state())) {
            throw new IllegalStateException(OperationMessages.INVALID_JOB_STATE);
        }
        Instant now = Instant.now(clock);

        PrintJob updated = job.cancelled(now, now);
        printJobStore.update(updated);

        recordEvent(
                updated.printerId(),
                updated.id(),
                OperationMessages.EVENT_JOB_CANCELLED,
                "Job cancelled: " + updated.id());

        return updated;
    }

    private boolean isTerminal(JobState state) {
        return state == JobState.COMPLETED
                || state == JobState.FAILED
                || state == JobState.CANCELLED;
    }

    public void delete(String jobId) {
        loadRequired(jobId);
        printJobStore.delete(jobId);
    }

    public void recordJobAuditEvent(
            String jobId,
            String eventType,
            String message) {
        PrintJob job = loadRequired(jobId);

        recordEvent(
                job.printerId(),
                job.id(),
                eventType,
                message);
    }

    private PrintJob loadRequired(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.JOB_ID_MUST_NOT_BE_BLANK);
        }

        return printJobStore.findById(jobId.trim())
                .orElseThrow(() -> new IllegalArgumentException(OperationMessages.JOB_NOT_FOUND));
    }

    private void recordEvent(
            String printerId,
            String jobId,
            String eventType,
            String message) {
        try {
            eventStore.record(printerId, jobId, eventType, message);
        } catch (Exception exception) {
            System.err.println(OperationMessages.failedToPersistEvent(
                    printerId == null ? "unknown-printer" : printerId,
                    OperationMessages.safeDetail(
                            exception.getMessage(),
                            OperationMessages.FAILED_TO_SAVE_PRINTER_EVENT)));
        }
    }
}
