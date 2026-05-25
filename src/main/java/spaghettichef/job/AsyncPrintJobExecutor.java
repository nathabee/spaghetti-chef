package spaghettichef.job;

import spaghettichef.OperationMessages;
import spaghettichef.config.RuntimeDefaults;
import spaghettichef.runtime.PrinterRegistry;
import spaghettichef.runtime.PrinterRuntimeNode;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class AsyncPrintJobExecutor implements AutoCloseable {

    private final PrintJobService printJobService;
    private final PrinterRegistry printerRegistry;
    private final PrinterActionGuard printerActionGuard;
    private final PrintJobExecutionService printJobExecutionService;
    private final ExecutorService executorService;
    private final Set<String> activePrinterIds = ConcurrentHashMap.newKeySet();

    public AsyncPrintJobExecutor(
            PrintJobService printJobService,
            PrinterRegistry printerRegistry,
            PrinterActionGuard printerActionGuard,
            PrintJobExecutionService printJobExecutionService
    ) {
        this(
                printJobService,
                printerRegistry,
                printerActionGuard,
                printJobExecutionService,
                RuntimeDefaults.DEFAULT_JOB_EXECUTOR_POOL_SIZE);
    }

    public AsyncPrintJobExecutor(
            PrintJobService printJobService,
            PrinterRegistry printerRegistry,
            PrinterActionGuard printerActionGuard,
            PrintJobExecutionService printJobExecutionService,
            int workerThreads
    ) {
        if (printJobService == null) {
            throw new IllegalArgumentException(OperationMessages.PRINT_JOB_SERVICE_MUST_NOT_BE_NULL);
        }
        if (printerRegistry == null) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_REGISTRY_MUST_NOT_BE_NULL);
        }
        if (printerActionGuard == null) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_ACTION_GUARD_MUST_NOT_BE_NULL);
        }
        if (printJobExecutionService == null) {
            throw new IllegalArgumentException(OperationMessages.PRINT_JOB_EXECUTION_SERVICE_MUST_NOT_BE_NULL);
        }
        if (workerThreads <= 0) {
            throw new IllegalArgumentException(OperationMessages.INTERVAL_SECONDS_MUST_BE_GREATER_THAN_ZERO);
        }

        this.printJobService = printJobService;
        this.printerRegistry = printerRegistry;
        this.printerActionGuard = printerActionGuard;
        this.printJobExecutionService = printJobExecutionService;
        this.executorService = Executors.newFixedThreadPool(workerThreads);
    }

    public StartResult start(String jobId) {
        PrintJob job = printJobService.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException(OperationMessages.JOB_NOT_FOUND));

        if (job.state() != JobState.ASSIGNED) {
            throw new IllegalStateException(OperationMessages.INVALID_JOB_STATE);
        }

        PrinterRuntimeNode node = printerRegistry.findById(job.printerId())
                .orElseThrow(() -> new IllegalStateException(OperationMessages.PRINTER_NOT_FOUND));

        PrinterActionGuard.GuardDecision decision = printerActionGuard.validateForExecution(job, node);

        if (!decision.allowed()) {
            PrintJob failed = failBeforeSubmission(job, decision.failureReason(), decision.detail());
            return StartResult.rejected(failed, decision.failureReason(), decision.detail());
        }

        if (hasConflictingRunningJob(job)) {
            PrintJob failed = failBeforeSubmission(
                    job,
                    JobFailureReason.PRINTER_BUSY,
                    "Another RUNNING job is already associated with printer " + job.printerId() + ".");
            return StartResult.rejected(failed, JobFailureReason.PRINTER_BUSY, OperationMessages.PRINTER_BUSY);
        }

        if (!activePrinterIds.add(node.id())) {
            PrintJob failed = failBeforeSubmission(
                    job,
                    JobFailureReason.PRINTER_BUSY,
                    OperationMessages.PRINTER_BUSY);
            return StartResult.rejected(failed, JobFailureReason.PRINTER_BUSY, OperationMessages.PRINTER_BUSY);
        }

        PrintJob running = printJobService.markRunning(job.id());
        printJobService.recordJobAuditEvent(
                job.id(),
                OperationMessages.EVENT_JOB_EXECUTION_QUEUED,
                "Job execution queued for background processing: " + job.id());

        executorService.submit(() -> {
            try {
                printJobExecutionService.executeStartedJob(job.id());
            } catch (Exception exception) {
                System.err.println(OperationMessages.apiOperationFailed(
                        OperationMessages.safeDetail(
                                exception.getMessage(),
                                OperationMessages.UNKNOWN_API_ERROR)));
            } finally {
                activePrinterIds.remove(node.id());
            }
        });

        return StartResult.accepted(running);
    }

    private boolean hasConflictingRunningJob(PrintJob job) {
        return printJobService.findRecent(200)
                .stream()
                .anyMatch(existing ->
                        !existing.id().equals(job.id())
                                && existing.printerId() != null
                                && existing.printerId().equals(job.printerId())
                                && existing.state() == JobState.RUNNING);
    }

    private PrintJob failBeforeSubmission(
            PrintJob job,
            JobFailureReason failureReason,
            String detail
    ) {
        printJobService.recordJobAuditEvent(
                job.id(),
                OperationMessages.EVENT_JOB_EXECUTION_FAILED,
                "Job execution rejected before background submission: "
                        + OperationMessages.safeDetail(detail, failureReason.name()));

        return printJobService.markFailed(job.id(), failureReason, detail);
    }

    @Override
    public void close() {
        executorService.shutdownNow();

        try {
            if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                System.err.println(OperationMessages.apiOperationFailed(
                        "Job executor did not terminate cleanly within timeout."));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.err.println(OperationMessages.apiOperationFailed(
                    OperationMessages.safeDetail(
                            exception.getMessage(),
                            OperationMessages.UNKNOWN_API_ERROR)));
        }
    }

    public record StartResult(
            boolean accepted,
            PrintJob job,
            JobFailureReason failureReason,
            String detail
    ) {
        public static StartResult accepted(PrintJob job) {
            return new StartResult(true, job, null, null);
        }

        public static StartResult rejected(
                PrintJob job,
                JobFailureReason failureReason,
                String detail
        ) {
            return new StartResult(false, job, failureReason, detail);
        }
    }
}
