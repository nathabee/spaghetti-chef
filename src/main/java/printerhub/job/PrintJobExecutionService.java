package printerhub.job;

import printerhub.OperationMessages;
import printerhub.config.PrinterProtocolDefaults;
import printerhub.monitoring.PrinterMonitoringScheduler;
import printerhub.persistence.MonitoringRulesStore;
import printerhub.persistence.PrintJobExecutionStepStore;
import printerhub.persistence.PrinterSdFileStore;
import printerhub.runtime.PrinterRegistry;
import printerhub.runtime.PrinterRuntimeNode;

import java.util.function.BooleanSupplier;

public final class PrintJobExecutionService {

    private final PrintJobService printJobService;
    private final PrinterRegistry printerRegistry;
    private final PrinterMonitoringScheduler monitoringScheduler;
    private final PrinterActionGuard printerActionGuard;
    private final PrinterActionMapper printerActionMapper;
    private final PrinterWorkflowPlanner printerWorkflowPlanner;
    private final PrinterResponseClassifier printerResponseClassifier;
    private final PrintJobExecutionStepStore printJobExecutionStepStore;
    private final PrinterSdFileStore printerSdFileStore;
    private final BooleanSupplier debugWireTracingEnabledSupplier;

    public PrintJobExecutionService(
            PrintJobService printJobService,
            PrinterRegistry printerRegistry,
            PrinterMonitoringScheduler monitoringScheduler,
            PrinterActionGuard printerActionGuard,
            PrinterActionMapper printerActionMapper,
            PrintJobExecutionStepStore printJobExecutionStepStore
    ) {
        this(
                printJobService,
                printerRegistry,
                monitoringScheduler,
                printerActionGuard,
                printerActionMapper,
                printJobExecutionStepStore,
                () -> new MonitoringRulesStore().load().debugWireTracingEnabled()
        );
    }

    public PrintJobExecutionService(
            PrintJobService printJobService,
            PrinterRegistry printerRegistry,
            PrinterMonitoringScheduler monitoringScheduler,
            PrinterActionGuard printerActionGuard,
            PrinterActionMapper printerActionMapper,
            PrintJobExecutionStepStore printJobExecutionStepStore,
            BooleanSupplier debugWireTracingEnabledSupplier
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
        if (printerActionGuard == null) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_ACTION_GUARD_MUST_NOT_BE_NULL);
        }
        if (printerActionMapper == null) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_ACTION_MAPPER_MUST_NOT_BE_NULL);
        }
        if (printJobExecutionStepStore == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("printJobExecutionStepStore"));
        }
        if (debugWireTracingEnabledSupplier == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("debugWireTracingEnabledSupplier"));
        }

        this.printJobService = printJobService;
        this.printerRegistry = printerRegistry;
        this.monitoringScheduler = monitoringScheduler;
        this.printerActionGuard = printerActionGuard;
        this.printerActionMapper = printerActionMapper;
        this.printerWorkflowPlanner = new PrinterWorkflowPlanner();
        this.printerResponseClassifier = new PrinterResponseClassifier();
        this.printJobExecutionStepStore = printJobExecutionStepStore;
        this.printerSdFileStore = new PrinterSdFileStore();
        this.debugWireTracingEnabledSupplier = debugWireTracingEnabledSupplier;
    }

    public PrinterActionExecutionResult execute(String jobId) {
        return execute(jobId, true);
    }

    public PrinterActionExecutionResult executeStartedJob(String jobId) {
        return execute(jobId, false);
    }

    private PrinterActionExecutionResult execute(String jobId, boolean markJobRunning) {
        PrintJob job = printJobService.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException(OperationMessages.JOB_NOT_FOUND));

        JobState expectedState = markJobRunning ? JobState.ASSIGNED : JobState.RUNNING;

        if (job.state() != expectedState) {
            throw new IllegalStateException(OperationMessages.INVALID_JOB_STATE);
        }

        PrinterRuntimeNode node = printerRegistry.findById(job.printerId())
                .orElseThrow(() -> new IllegalStateException(OperationMessages.PRINTER_NOT_FOUND));

        PrinterActionGuard.GuardDecision decision =
                printerActionGuard.validateForExecution(job, node);

        if (!decision.allowed()) {
            printJobService.recordJobAuditEvent(
                    job.id(),
                    OperationMessages.EVENT_JOB_EXECUTION_FAILED,
                    "Job execution rejected before start: "
                            + OperationMessages.safeDetail(decision.detail(), decision.failureReason().name())
            );

            printJobService.markFailed(job.id(), decision.failureReason(), decision.detail());

            return PrinterActionExecutionResult.failure(
                    null,
                    null,
                    decision.failureReason(),
                    decision.detail()
            );
        }

        if (job.type() == JobType.PRINT_FILE) {
            return executePrintFileJob(job, node, markJobRunning);
        }

        PrinterActionRequest request = PrinterActionRequest.fromJob(job);
        PrinterWorkflowPlan workflowPlan = printerWorkflowPlanner.plan(request, printerActionMapper);

        node.beginJobExecution(job.id());
        monitoringScheduler.stopMonitoring(node.id());

        String currentCommand = null;
        String lastResponse = null;
        int stepIndex = 0;

        try {
            if (markJobRunning) {
                printJobService.markRunning(job.id());
            }

            printJobService.recordJobAuditEvent(
                    job.id(),
                    OperationMessages.EVENT_JOB_EXECUTION_STARTED,
                    "Job execution started: " + describePlan(workflowPlan)
            );

            node.printerPort().connect();

            for (PrinterWorkflowStep step : workflowPlan.steps()) {
                currentCommand = step.wireCommand();

                printJobService.recordJobAuditEvent(
                        job.id(),
                        OperationMessages.EVENT_JOB_EXECUTION_STARTED,
                        "Workflow step started: " + step.name() + " -> " + currentCommand
                );

                String response = sendTracedCommand(node, currentCommand);
                lastResponse = response;

                PrinterResponseClassifier.ResponseClassification classification =
                        printerResponseClassifier.classifyResponse(currentCommand, response);

                if (responseContainsBusy(response)) {
                    printJobService.recordJobAuditEvent(
                            job.id(),
                            OperationMessages.EVENT_JOB_EXECUTION_IN_PROGRESS,
                            "Workflow step reported in progress before completion: "
                                    + step.name()
                                    + " -> "
                                    + currentCommand
                                    + " | response="
                                    + OperationMessages.safeDetail(response, "no response")
                    );
                }

                if (!classification.success()) {
                    String failureDetail = classification.detail();

                    persistStepFailure(
                            job.id(),
                            stepIndex,
                            step.name(),
                            currentCommand,
                            classification.response(),
                            classification.failureReason(),
                            failureDetail
                    );

                    printJobService.recordJobAuditEvent(
                            job.id(),
                            OperationMessages.EVENT_JOB_EXECUTION_FAILED,
                            "Workflow step failed: "
                                    + step.name()
                                    + " -> "
                                    + currentCommand
                                    + " | outcome="
                                    + classification.failureReason().name()
                                    + " | response="
                                    + OperationMessages.safeDetail(classification.response(), "no response")
                    );

                    printJobService.markFailed(
                            job.id(),
                            classification.failureReason(),
                            failureDetail
                    );

                    return PrinterActionExecutionResult.failure(
                            currentCommand,
                            classification.response(),
                            classification.failureReason(),
                            failureDetail
                    );
                }

                persistStepSuccess(
                        job.id(),
                        stepIndex,
                        step.name(),
                        currentCommand,
                        classification.response()
                );

                printJobService.recordJobAuditEvent(
                        job.id(),
                        OperationMessages.EVENT_JOB_EXECUTION_SUCCEEDED,
                        "Workflow step succeeded: "
                                + step.name()
                                + " -> "
                                + currentCommand
                                + " | response="
                                + OperationMessages.safeDetail(classification.response(), "no response")
                );

                stepIndex++;
            }

            printJobService.markCompleted(job.id());

            printJobService.recordJobAuditEvent(
                    job.id(),
                    OperationMessages.EVENT_JOB_EXECUTION_SUCCEEDED,
                    "Job execution completed: "
                            + OperationMessages.safeDetail(currentCommand, "n/a")
                            + " -> "
                            + OperationMessages.safeDetail(lastResponse, "no response")
            );

            return PrinterActionExecutionResult.success(currentCommand, lastResponse);
        } catch (Exception exception) {
            PrinterResponseClassifier.ResponseClassification classification =
                    printerResponseClassifier.classifyException(currentCommand, exception);

            persistStepFailure(
                    job.id(),
                    stepIndex,
                    "workflow-exception",
                    currentCommand,
                    classification.response(),
                    classification.failureReason(),
                    classification.detail()
            );

            printJobService.recordJobAuditEvent(
                    job.id(),
                    OperationMessages.EVENT_JOB_EXECUTION_FAILED,
                    "Job execution failed: "
                            + OperationMessages.safeDetail(currentCommand, "n/a")
                            + " | outcome="
                            + classification.failureReason().name()
                            + " | detail="
                            + OperationMessages.safeDetail(classification.detail(), JobFailureReason.UNKNOWN.name())
            );

            printJobService.markFailed(
                    job.id(),
                    classification.failureReason(),
                    classification.detail()
            );

            return PrinterActionExecutionResult.failure(
                    currentCommand,
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

    private PrinterActionExecutionResult executePrintFileJob(
            PrintJob job,
            PrinterRuntimeNode node,
            boolean markJobRunning
    ) {
        node.beginJobExecution(job.id());
        monitoringScheduler.stopMonitoring(node.id());

        try {
            if (markJobRunning) {
                printJobService.markRunning(job.id());
            }

            PrinterSdFile targetFile = printerSdFileStore.findById(job.printerSdFileId())
                    .orElseThrow(() -> new IllegalStateException(OperationMessages.PRINTER_SD_FILE_NOT_FOUND));

            if (targetFile.deleted()) {
                throw new IllegalStateException(OperationMessages.PRINTER_SD_FILE_DELETED);
            }

            if (!targetFile.enabled()) {
                throw new IllegalStateException(OperationMessages.PRINTER_SD_FILE_DISABLED);
            }

            printJobService.recordJobAuditEvent(
                    job.id(),
                    OperationMessages.EVENT_JOB_EXECUTION_STARTED,
                    "Autonomous print-start workflow started for "
                            + OperationMessages.safeDetail(targetFile.firmwarePath(), "no printer SD file"));

            persistStepSuccess(
                    job.id(),
                    0,
                    "validate-printer-sd-target",
                    null,
                    "Validated registered printer-side target: "
                            + OperationMessages.safeDetail(targetFile.firmwarePath(), "no printer SD file")
            );

            node.printerPort().connect();

            PrinterActionExecutionResult selectResult = executeAutonomousPrintWorkflowStep(
                    job,
                    1,
                    "select-printer-sd-file",
                    PrinterProtocolDefaults.COMMAND_SELECT_SD_FILE + " " + targetFile.firmwarePath(),
                    null
            );
            if (!selectResult.success()) {
                return selectResult;
            }

            PrinterActionExecutionResult startResult = executeAutonomousPrintWorkflowStep(
                    job,
                    2,
                    "start-printer-sd-print",
                    PrinterProtocolDefaults.COMMAND_START_SD_PRINT,
                    "Printer accepted autonomous SD print start for "
                            + OperationMessages.safeDetail(targetFile.firmwarePath(), "no printer SD file")
            );
            if (!startResult.success()) {
                return startResult;
            }

            if (responseIndicatesCompletedAutonomousPrint(startResult.response())) {
                printJobService.markCompleted(job.id());
                printJobService.recordJobAuditEvent(
                        job.id(),
                        OperationMessages.EVENT_JOB_EXECUTION_SUCCEEDED,
                        "Autonomous print completed immediately for "
                                + OperationMessages.safeDetail(targetFile.firmwarePath(), "no printer SD file")
                                + " according to printer response.");
                return startResult;
            }

            printJobService.recordJobAuditEvent(
                    job.id(),
                    OperationMessages.EVENT_JOB_EXECUTION_SUCCEEDED,
                    "Autonomous print started for "
                            + OperationMessages.safeDetail(targetFile.firmwarePath(), "no printer SD file")
                            + ". Job remains RUNNING while the printer executes it.");

            return startResult;
        } catch (Exception exception) {
            PrinterResponseClassifier.ResponseClassification classification =
                    printerResponseClassifier.classifyException(
                            PrinterProtocolDefaults.COMMAND_START_SD_PRINT,
                            exception);

            persistStepFailure(
                    job.id(),
                    0,
                    "autonomous-print-start",
                    null,
                    classification.response(),
                    classification.failureReason(),
                    classification.detail());

            printJobService.markFailed(
                    job.id(),
                    classification.failureReason(),
                    classification.detail());

            return PrinterActionExecutionResult.failure(
                    null,
                    classification.response(),
                    classification.failureReason(),
                    classification.detail());
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

    private PrinterActionExecutionResult executeAutonomousPrintWorkflowStep(
            PrintJob job,
            int stepIndex,
            String stepName,
            String wireCommand,
            String transitionResponse
    ) {
        printJobService.recordJobAuditEvent(
                job.id(),
                OperationMessages.EVENT_JOB_EXECUTION_STARTED,
                "Workflow step started: " + stepName + " -> " + wireCommand
        );

        PrinterRuntimeNode node = printerRegistry.findById(job.printerId())
                .orElseThrow(() -> new IllegalStateException(OperationMessages.PRINTER_NOT_FOUND));
        String response = sendTracedCommand(node, wireCommand);

        PrinterResponseClassifier.ResponseClassification classification =
                printerResponseClassifier.classifyResponse(wireCommand, response);

        if (!classification.success()) {
            persistStepFailure(
                    job.id(),
                    stepIndex,
                    stepName,
                    wireCommand,
                    classification.response(),
                    classification.failureReason(),
                    classification.detail()
            );

            printJobService.recordJobAuditEvent(
                    job.id(),
                    OperationMessages.EVENT_JOB_EXECUTION_FAILED,
                    "Workflow step failed: "
                            + stepName
                            + " -> "
                            + wireCommand
                            + " | outcome="
                            + classification.failureReason().name()
                            + " | response="
                            + OperationMessages.safeDetail(classification.response(), "no response")
            );

            printJobService.markFailed(
                    job.id(),
                    classification.failureReason(),
                    classification.detail()
            );

            return PrinterActionExecutionResult.failure(
                    wireCommand,
                    classification.response(),
                    classification.failureReason(),
                    classification.detail()
            );
        }

        persistStepSuccess(
                job.id(),
                stepIndex,
                stepName,
                wireCommand,
                classification.response()
        );

        printJobService.recordJobAuditEvent(
                job.id(),
                OperationMessages.EVENT_JOB_EXECUTION_SUCCEEDED,
                "Workflow step succeeded: "
                        + stepName
                        + " -> "
                        + wireCommand
                        + " | response="
                        + OperationMessages.safeDetail(classification.response(), "no response")
        );

        if (transitionResponse != null) {
            persistStepSuccess(
                    job.id(),
                    stepIndex + 1,
                    "transition-job-running",
                    null,
                    transitionResponse
            );
        }

        return PrinterActionExecutionResult.success(wireCommand, classification.response());
    }

    private boolean responseIndicatesCompletedAutonomousPrint(String response) {
        if (response == null || response.isBlank()) {
            return false;
        }

        String normalized = response.toLowerCase();
        return normalized.contains("done printing file");
    }

    private void persistStepSuccess(
            String jobId,
            int stepIndex,
            String stepName,
            String wireCommand,
            String response
    ) {
        printJobExecutionStepStore.save(
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

    private boolean responseContainsBusy(String response) {
        return response != null && response.toLowerCase(java.util.Locale.ROOT).contains("busy");
    }

    private String sendTracedCommand(PrinterRuntimeNode node, String wireCommand) {
        traceWire(node.id(), "SEND", wireCommand);
        String response = node.printerPort().sendCommand(wireCommand);
        traceWire(node.id(), "RECV", response);
        return response;
    }

    private void traceWire(String printerId, String direction, String payload) {
        if (!debugWireTracingEnabledSupplier.getAsBoolean()) {
            return;
        }
        System.out.println("[PrinterHub] printer wire " + printerId + " " + direction + " "
                + OperationMessages.safeDetail(payload, "no response"));
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
        printJobExecutionStepStore.save(
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

    private String describePlan(PrinterWorkflowPlan workflowPlan) {
        StringBuilder builder = new StringBuilder();
        builder.append(workflowPlan.actionType().name()).append(" [");

        boolean first = true;

        for (PrinterWorkflowStep step : workflowPlan.steps()) {
            if (!first) {
                builder.append(" | ");
            }

            builder.append(step.name()).append(": ").append(step.wireCommand());
            first = false;
        }

        builder.append("]");
        return builder.toString();
    }
}
