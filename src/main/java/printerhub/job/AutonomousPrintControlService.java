package printerhub.job;

import printerhub.OperationMessages;
import printerhub.config.PrinterProtocolDefaults;
import printerhub.monitoring.PrinterMonitoringScheduler;
import printerhub.persistence.PrintJobExecutionStepStore;
import printerhub.runtime.PrinterRegistry;
import printerhub.runtime.PrinterRuntimeNode;

public final class AutonomousPrintControlService {

    private final PrintJobService printJobService;
    private final PrinterRegistry printerRegistry;
    private final PrinterMonitoringScheduler monitoringScheduler;
    private final PrintJobExecutionStepStore stepStore;
    private final PrinterResponseClassifier printerResponseClassifier;

    public AutonomousPrintControlService(
            PrintJobService printJobService,
            PrinterRegistry printerRegistry,
            PrinterMonitoringScheduler monitoringScheduler,
            PrintJobExecutionStepStore stepStore
    ) {
        if (printJobService == null) {
            throw new IllegalArgumentException(OperationMessages.PRINT_JOB_SERVICE_MUST_NOT_BE_NULL);
        }
        if (printerRegistry == null) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_REGISTRY_MUST_NOT_BE_NULL);
        }
        if (monitoringScheduler == null) {
            throw new IllegalArgumentException(OperationMessages.MONITORING_SCHEDULER_MUST_NOT_BE_NULL);
        }
        if (stepStore == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("printJobExecutionStepStore"));
        }

        this.printJobService = printJobService;
        this.printerRegistry = printerRegistry;
        this.monitoringScheduler = monitoringScheduler;
        this.stepStore = stepStore;
        this.printerResponseClassifier = new PrinterResponseClassifier();
    }

    public ControlResult pause(String jobId) {
        PrintJob job = requirePrintFileJob(jobId);
        if (job.state() != JobState.RUNNING) {
            throw new IllegalStateException(OperationMessages.INVALID_JOB_STATE);
        }

        return executeControl(job, "pause-printer-sd-print", PrinterProtocolDefaults.COMMAND_PAUSE_SD_PRINT, ControlAction.PAUSE);
    }

    public ControlResult resume(String jobId) {
        PrintJob job = requirePrintFileJob(jobId);
        if (job.state() != JobState.PAUSED) {
            throw new IllegalStateException(OperationMessages.INVALID_JOB_STATE);
        }

        return executeControl(job, "resume-printer-sd-print", PrinterProtocolDefaults.COMMAND_START_SD_PRINT, ControlAction.RESUME);
    }

    public ControlResult cancel(String jobId) {
        PrintJob job = requirePrintFileJob(jobId);
        if (job.state() != JobState.RUNNING && job.state() != JobState.PAUSED) {
            throw new IllegalStateException(OperationMessages.INVALID_JOB_STATE);
        }

        return executeControl(job, "abort-printer-sd-print", PrinterProtocolDefaults.COMMAND_ABORT_SD_PRINT, ControlAction.CANCEL);
    }

    private ControlResult executeControl(
            PrintJob job,
            String stepName,
            String command,
            ControlAction action
    ) {
        PrinterRuntimeNode node = printerRegistry.findById(job.printerId())
                .orElseThrow(() -> new IllegalStateException(OperationMessages.PRINTER_NOT_FOUND));

        int stepIndex = stepStore.findByJobId(job.id()).size();
        monitoringScheduler.stopMonitoring(node.id());
        node.beginJobExecution(job.id());

        try {
            if (action == ControlAction.CANCEL) {
                printJobService.markCancelling(job.id());
            }

            printJobService.recordJobAuditEvent(
                    job.id(),
                    OperationMessages.EVENT_JOB_EXECUTION_STARTED,
                    "Workflow step started: " + stepName + " -> " + command
            );

            node.printerPort().connect();
            String response = node.printerPort().sendCommand(command);

            PrinterResponseClassifier.ResponseClassification classification =
                    printerResponseClassifier.classifyResponse(command, response);

            if (!classification.success()) {
                restoreStateAfterFailedControl(job);
                persistStepFailure(job.id(), stepIndex, stepName, command,
                        classification.response(), classification.failureReason(), classification.detail());
                printJobService.recordJobAuditEvent(
                        job.id(),
                        OperationMessages.EVENT_JOB_EXECUTION_FAILED,
                        "Workflow step failed: "
                                + stepName
                                + " -> "
                                + command
                                + " | outcome="
                                + classification.failureReason().name()
                                + " | response="
                                + OperationMessages.safeDetail(classification.response(), "no response")
                );

                return ControlResult.failure(
                        printJobService.findById(job.id()).orElseThrow(() -> new IllegalStateException(OperationMessages.JOB_NOT_FOUND)),
                        command,
                        classification.response(),
                        classification.failureReason(),
                        classification.detail()
                );
            }

            persistStepSuccess(job.id(), stepIndex, stepName, command, classification.response());
            printJobService.recordJobAuditEvent(
                    job.id(),
                    OperationMessages.EVENT_JOB_EXECUTION_SUCCEEDED,
                    "Workflow step succeeded: "
                            + stepName
                            + " -> "
                            + command
                            + " | response="
                            + OperationMessages.safeDetail(classification.response(), "no response")
            );

            PrintJob updated = transitionSuccessfulControl(job.id(), action);
            return ControlResult.success(updated, command, classification.response());
        } catch (Exception exception) {
            restoreStateAfterFailedControl(job);
            PrinterResponseClassifier.ResponseClassification classification =
                    printerResponseClassifier.classifyException(command, exception);
            persistStepFailure(job.id(), stepIndex, stepName, command,
                    classification.response(), classification.failureReason(), classification.detail());
            printJobService.recordJobAuditEvent(
                    job.id(),
                    OperationMessages.EVENT_JOB_EXECUTION_FAILED,
                    "Workflow step failed: "
                            + stepName
                            + " -> "
                            + command
                            + " | outcome="
                            + classification.failureReason().name()
                            + " | detail="
                            + OperationMessages.safeDetail(classification.detail(), JobFailureReason.UNKNOWN.name())
            );

            return ControlResult.failure(
                    printJobService.findById(job.id()).orElseThrow(() -> new IllegalStateException(OperationMessages.JOB_NOT_FOUND)),
                    command,
                    classification.response(),
                    classification.failureReason(),
                    classification.detail()
            );
        } finally {
            try {
                node.printerPort().disconnect();
            } catch (Exception exception) {
                System.err.println(OperationMessages.failedToDisconnectPrinterNode(
                        node.id(),
                        OperationMessages.safeDetail(
                                exception.getMessage(),
                                OperationMessages.UNKNOWN_RUNTIME_CLOSE_ERROR
                        )
                ));
            }
            node.endJobExecution();

            try {
                if (node.enabled()) {
                    monitoringScheduler.startMonitoring(node);
                }
            } catch (Exception exception) {
                System.err.println(OperationMessages.apiOperationFailed(
                        OperationMessages.safeDetail(
                                exception.getMessage(),
                                OperationMessages.UNKNOWN_API_ERROR
                        )
                ));
            }
        }
    }

    private void restoreStateAfterFailedControl(PrintJob job) {
        if (job.state() == JobState.RUNNING) {
            printJobService.markResumed(job.id());
            return;
        }
        if (job.state() == JobState.PAUSED) {
            printJobService.markPaused(job.id());
        }
    }

    private PrintJob transitionSuccessfulControl(String jobId, ControlAction action) {
        return switch (action) {
            case PAUSE -> printJobService.markPaused(jobId);
            case RESUME -> printJobService.markResumed(jobId);
            case CANCEL -> printJobService.cancel(jobId);
        };
    }

    private PrintJob requirePrintFileJob(String jobId) {
        PrintJob job = printJobService.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException(OperationMessages.JOB_NOT_FOUND));
        if (job.type() != JobType.PRINT_FILE) {
            throw new IllegalStateException(OperationMessages.INVALID_JOB_STATE);
        }
        return job;
    }

    private void persistStepSuccess(
            String jobId,
            int stepIndex,
            String stepName,
            String wireCommand,
            String response
    ) {
        stepStore.save(
                PrintJobExecutionStep.success(
                        jobId,
                        stepIndex,
                        stepName,
                        wireCommand,
                        response,
                        "SUCCESS"
                )
        );
    }

    private void persistStepFailure(
            String jobId,
            int stepIndex,
            String stepName,
            String wireCommand,
            String response,
            JobFailureReason failureReason,
            String failureDetail
    ) {
        stepStore.save(
                PrintJobExecutionStep.failure(
                        jobId,
                        stepIndex,
                        stepName,
                        wireCommand,
                        response,
                        failureReason == null ? "FAILED" : failureReason.name(),
                        failureReason,
                        failureDetail
                )
        );
    }

    private enum ControlAction {
        PAUSE,
        RESUME,
        CANCEL
    }

    public record ControlResult(
            PrintJob job,
            boolean success,
            String wireCommand,
            String response,
            JobFailureReason failureReason,
            String failureDetail
    ) {
        public static ControlResult success(PrintJob job, String wireCommand, String response) {
            return new ControlResult(job, true, wireCommand, response, null, null);
        }

        public static ControlResult failure(
                PrintJob job,
                String wireCommand,
                String response,
                JobFailureReason failureReason,
                String failureDetail
        ) {
            return new ControlResult(job, false, wireCommand, response, failureReason, failureDetail);
        }
    }
}
