package printerhub.monitoring;

import printerhub.OperationMessages;
import printerhub.PrinterSnapshot;
import printerhub.PrinterState;
import printerhub.SerialCommunicationException;
import printerhub.SerialFailureType;
import printerhub.config.PrinterProtocolDefaults;
import printerhub.job.JobState;
import printerhub.job.JobType;
import printerhub.job.PrintJob;
import printerhub.job.PrintJobService;
import printerhub.persistence.PrintJobStore;
import printerhub.persistence.MonitoringRules;
import printerhub.persistence.PrinterEventStore;
import printerhub.persistence.PrinterSnapshotStore;
import printerhub.runtime.PrinterRuntimeNode;
import printerhub.runtime.PrinterRuntimeStateCache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PrinterMonitoringTask implements Runnable {

    private static final Pattern HOTEND_PATTERN = Pattern.compile("T:([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern BED_PATTERN = Pattern.compile("B:([0-9]+(?:\\.[0-9]+)?)");

    private final PrinterRuntimeNode node;
    private final PrinterRuntimeStateCache stateCache;
    private final PrinterSnapshotStore snapshotStore;
    private final PrinterEventStore eventStore;
    private final Clock clock;
    private final String statusCommand;
    private final BooleanSupplier shutdownSignal;
    private final MonitoringEventPolicy eventPolicy;
    private final MonitoringRules monitoringRules;

    public PrinterMonitoringTask(PrinterRuntimeNode node, PrinterRuntimeStateCache stateCache) {
        this(
                node,
                stateCache,
                new PrinterSnapshotStore(MonitoringRules.defaults()),
                new PrinterEventStore(),
                Clock.systemUTC(),
                PrinterProtocolDefaults.DEFAULT_STATUS_COMMAND,
                () -> false,
                new MonitoringEventPolicy(
                        Clock.systemUTC(),
                        Duration.ofSeconds(MonitoringRules.defaults().eventDeduplicationWindowSeconds())
                ),
                MonitoringRules.defaults()
        );
    }

    public PrinterMonitoringTask(
            PrinterRuntimeNode node,
            PrinterRuntimeStateCache stateCache,
            PrinterSnapshotStore snapshotStore,
            PrinterEventStore eventStore,
            Clock clock,
            String statusCommand,
            BooleanSupplier shutdownSignal,
            MonitoringEventPolicy eventPolicy,
            MonitoringRules monitoringRules
    ) {
        if (node == null) {
            throw new IllegalArgumentException(OperationMessages.NODE_MUST_NOT_BE_NULL);
        }
        if (stateCache == null) {
            throw new IllegalArgumentException(OperationMessages.STATE_CACHE_MUST_NOT_BE_NULL);
        }
        if (snapshotStore == null) {
            throw new IllegalArgumentException(OperationMessages.SNAPSHOT_STORE_MUST_NOT_BE_NULL);
        }
        if (eventStore == null) {
            throw new IllegalArgumentException(OperationMessages.EVENT_STORE_MUST_NOT_BE_NULL);
        }
        if (clock == null) {
            throw new IllegalArgumentException(OperationMessages.CLOCK_MUST_NOT_BE_NULL);
        }
        if (statusCommand == null || statusCommand.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.STATUS_COMMAND_MUST_NOT_BE_BLANK);
        }
        if (shutdownSignal == null) {
            throw new IllegalArgumentException(OperationMessages.SHUTDOWN_SIGNAL_MUST_NOT_BE_NULL);
        }
        if (eventPolicy == null) {
            throw new IllegalArgumentException(OperationMessages.MONITORING_EVENT_POLICY_MUST_NOT_BE_NULL);
        }
        if (monitoringRules == null) {
            throw new IllegalArgumentException(OperationMessages.MONITORING_RULES_MUST_NOT_BE_NULL);
        }

        this.node = node;
        this.stateCache = stateCache;
        this.snapshotStore = snapshotStore;
        this.eventStore = eventStore;
        this.clock = clock;
        this.statusCommand = statusCommand;
        this.shutdownSignal = shutdownSignal;
        this.eventPolicy = eventPolicy;
        this.monitoringRules = monitoringRules;
    }

    @Override
    public void run() {
        PrinterSnapshot previousSnapshot = stateCache.currentOrDisconnected(node.id());

        if (!node.enabled()) {
            PrinterSnapshot snapshot = PrinterSnapshot.error(
                    PrinterState.DISCONNECTED,
                    previousSnapshot.hotendTemperature(),
                    previousSnapshot.bedTemperature(),
                    previousSnapshot.lastResponse(),
                    OperationMessages.PRINTER_NODE_DISABLED,
                    Instant.now(clock)
            );

            eventPolicy.clearPrinter(node.id());
            storeAndCache(
                    snapshot,
                    OperationMessages.EVENT_PRINTER_DISABLED,
                    OperationMessages.PRINTER_NODE_DISABLED,
                    false
            );
            return;
        }

        try {
            stateCache.update(
                    node.id(),
                    PrinterSnapshot.connecting(
                            previousSnapshot.hotendTemperature(),
                            previousSnapshot.bedTemperature(),
                            previousSnapshot.lastResponse(),
                            Instant.now(clock)
                    )
            );

            node.printerPort().connect();

            PrintJob runningAutonomousPrintJob = findRunningAutonomousPrintJobSafely();
            String response = node.printerPort().sendCommand(statusCommand);
            String sdPrintStatusResponse = pollSdPrintStatusIfNeeded(runningAutonomousPrintJob);
            String combinedResponse = combineResponses(response, sdPrintStatusResponse);

            if (response == null || response.isBlank()) {
                PrinterSnapshot snapshot = PrinterSnapshot.error(
                        PrinterState.ERROR,
                        previousSnapshot.hotendTemperature(),
                        previousSnapshot.bedTemperature(),
                        combinedResponse,
                        OperationMessages.noResponseForCommand(statusCommand),
                        Instant.now(clock)
                );

                storeAndCache(
                        snapshot,
                        OperationMessages.EVENT_PRINTER_TIMEOUT,
                        OperationMessages.noResponseForCommand(statusCommand),
                        true
                );
                return;
            }

            Double hotendTemperature = extractTemperature(HOTEND_PATTERN, response);
            Double bedTemperature = extractTemperature(BED_PATTERN, response);

            PrinterState state = resolveState(combinedResponse, hotendTemperature);

            if (state == PrinterState.ERROR) {
                PrinterSnapshot snapshot = PrinterSnapshot.error(
                        PrinterState.ERROR,
                        hotendTemperature != null ? hotendTemperature : previousSnapshot.hotendTemperature(),
                        bedTemperature != null ? bedTemperature : previousSnapshot.bedTemperature(),
                        combinedResponse,
                        combinedResponse,
                        Instant.now(clock)
                );

                storeAndCache(
                        snapshot,
                        OperationMessages.EVENT_PRINTER_ERROR,
                        OperationMessages.PRINTER_RETURNED_ERROR_RESPONSE,
                        true
                );
                return;
            }

            PrinterSnapshot snapshot = PrinterSnapshot.fromResponse(
                    state,
                    hotendTemperature != null ? hotendTemperature : previousSnapshot.hotendTemperature(),
                    bedTemperature != null ? bedTemperature : previousSnapshot.bedTemperature(),
                    combinedResponse,
                    Instant.now(clock)
            );

            eventPolicy.clearPrinter(node.id());
            storeAndCache(
                    snapshot,
                    OperationMessages.EVENT_PRINTER_POLLED,
                    OperationMessages.PRINTER_POLL_COMPLETED_SUCCESSFULLY,
                    false
            );
            try {
                reconcileAutonomousPrintJob(runningAutonomousPrintJob, previousSnapshot, snapshot);
            } catch (Exception exception) {
                System.err.println(OperationMessages.apiOperationFailed(
                        "Failed to reconcile autonomous print job for "
                                + node.id()
                                + ": "
                                + safeMessage(exception)
                ));
            }
        } catch (Exception exception) {
            if (shouldSuppressFailureDuringShutdown(exception)) {
                return;
            }

            String message = safeMessage(exception);
            SerialFailureType failureType = classifySerialFailure(exception);

            PrinterSnapshot snapshot = PrinterSnapshot.error(
                    PrinterState.ERROR,
                    previousSnapshot.hotendTemperature(),
                    previousSnapshot.bedTemperature(),
                    previousSnapshot.lastResponse(),
                    message,
                    failureType,
                    Instant.now(clock)
            );

            storeAndCache(
                    snapshot,
                    classifyException(exception),
                    message,
                    true
            );
        }
    }

    private void storeAndCache(
            PrinterSnapshot snapshot,
            String eventType,
            String eventMessage,
            boolean persistEvent
    ) {
        stateCache.update(node.id(), snapshot);

        try {
            snapshotStore.save(node.id(), snapshot);
        } catch (Exception exception) {
            System.err.println(OperationMessages.failedToPersistSnapshot(
                    node.id(),
                    safeMessage(exception)
            ));
        }

        if (!persistEvent) {
            return;
        }

        if (!shouldPersistEvent(eventType, eventMessage)) {
            return;
        }

        try {
            eventStore.record(node.id(), null, eventType, eventMessage);
            eventPolicy.rememberPersistedEvent(node.id(), eventType, eventMessage);
        } catch (Exception exception) {
            System.err.println(OperationMessages.failedToPersistEvent(
                    node.id(),
                    safeMessage(exception)
            ));
        }
    }

    private void reconcileAutonomousPrintJob(
            PrintJob runningPrintFileJob,
            PrinterSnapshot previousSnapshot,
            PrinterSnapshot currentSnapshot
    ) {
        if (runningPrintFileJob == null) {
            return;
        }

        if (!shouldCompleteAutonomousPrint(runningPrintFileJob, previousSnapshot, currentSnapshot)) {
            return;
        }

        PrintJobService printJobService = new PrintJobService(
                new PrintJobStore(),
                eventStore,
                clock
        );
        printJobService.markCompleted(runningPrintFileJob.id());
        printJobService.recordJobAuditEvent(
                runningPrintFileJob.id(),
                OperationMessages.EVENT_JOB_EXECUTION_SUCCEEDED,
                buildAutonomousCompletionMessage(previousSnapshot, currentSnapshot)
        );
    }

    private PrintJob findRunningAutonomousPrintJobSafely() {
        try {
            return new PrintJobStore().findRecent(200)
                    .stream()
                    .filter(job -> node.id().equals(job.printerId()))
                    .filter(job -> job.type() == JobType.PRINT_FILE)
                    .filter(job -> job.state() == JobState.RUNNING)
                    .findFirst()
                    .orElse(null);
        } catch (Exception exception) {
            return null;
        }
    }

    private boolean shouldCompleteAutonomousPrint(
            PrintJob runningPrintFileJob,
            PrinterSnapshot previousSnapshot,
            PrinterSnapshot currentSnapshot
    ) {
        String currentResponse = currentSnapshot.lastResponse();
        String normalizedResponse = currentResponse == null ? "" : currentResponse.toLowerCase(Locale.ROOT);
        if (normalizedResponse.contains("done printing file")) {
            return true;
        }

        if (previousSnapshot.state() == PrinterState.PRINTING
                && currentSnapshot.state() == PrinterState.IDLE) {
            return true;
        }

        return normalizedResponse.contains("not sd printing")
                && runningPrintFileJob.startedAt() != null
                && !clock.instant().isBefore(runningPrintFileJob.startedAt().plusSeconds(5));
    }

    private String buildAutonomousCompletionMessage(
            PrinterSnapshot previousSnapshot,
            PrinterSnapshot currentSnapshot
    ) {
        String currentResponse = currentSnapshot.lastResponse();
        if (currentResponse != null && currentResponse.toLowerCase(Locale.ROOT).contains("done printing file")) {
            return "Autonomous print completed according to monitoring: printer reported 'Done printing file'.";
        }

        if (previousSnapshot.state() == PrinterState.PRINTING
                && currentSnapshot.state() == PrinterState.IDLE) {
            return "Autonomous print completed according to monitoring: printer transitioned from PRINTING to IDLE.";
        }

        return "Autonomous print completed according to monitoring: latest printer state is "
                + currentSnapshot.state()
                + ".";
    }

    private String pollSdPrintStatusIfNeeded(PrintJob runningAutonomousPrintJob) {
        if (runningAutonomousPrintJob == null) {
            return null;
        }

        try {
            return node.printerPort().sendCommand(PrinterProtocolDefaults.COMMAND_READ_SD_PRINT_STATUS);
        } catch (Exception exception) {
            return null;
        }
    }

    private String combineResponses(String primaryResponse, String secondaryResponse) {
        if (secondaryResponse == null || secondaryResponse.isBlank()) {
            return primaryResponse;
        }
        if (primaryResponse == null || primaryResponse.isBlank()) {
            return secondaryResponse;
        }
        return primaryResponse + System.lineSeparator() + secondaryResponse;
    }

    private boolean shouldPersistEvent(String eventType, String eventMessage) {
        if (monitoringRules.errorPersistenceBehavior() == MonitoringRules.ErrorPersistenceBehavior.ALWAYS) {
            return true;
        }

        return eventPolicy.shouldPersistEvent(node.id(), eventType, eventMessage);
    }

    private boolean shouldSuppressFailureDuringShutdown(Exception exception) {
        if (!shutdownSignal.getAsBoolean()) {
            return false;
        }

        if (exception instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return true;
        }

        String message = safeMessage(exception).toLowerCase(Locale.ROOT);

        return Thread.currentThread().isInterrupted()
                || message.contains("interrupted")
                || message.contains("closed")
                || message.contains("shutdown");
    }

    private String classifyException(Exception exception) {
        SerialFailureType failureType = classifySerialFailure(exception);
        if (failureType == SerialFailureType.READ_TIMEOUT || failureType == SerialFailureType.CONNECT_TIMEOUT) {
            return OperationMessages.EVENT_PRINTER_TIMEOUT;
        }
        if (failureType == SerialFailureType.DEVICE_DISCONNECTED
                || failureType == SerialFailureType.DEVICE_PATH_NOT_FOUND
                || failureType == SerialFailureType.DEVICE_PERMISSION_DENIED
                || failureType == SerialFailureType.DEVICE_BUSY) {
            return OperationMessages.EVENT_PRINTER_DISCONNECTED;
        }

        String message = safeMessage(exception).toLowerCase(Locale.ROOT);

        if (message.contains("timeout")
                || message.contains("no response")) {
            return OperationMessages.EVENT_PRINTER_TIMEOUT;
        }

        if (message.contains("disconnected")
                || message.contains("not connected")
                || message.contains("not open")
                || message.contains("failed to open serial port")) {
            return OperationMessages.EVENT_PRINTER_DISCONNECTED;
        }

        return OperationMessages.EVENT_PRINTER_ERROR;
    }

    private SerialFailureType classifySerialFailure(Exception exception) {
        if (exception instanceof SerialCommunicationException serialException) {
            return serialException.failureType();
        }

        String message = safeMessage(exception).toLowerCase(Locale.ROOT);

        if (message.contains("permission denied") || message.contains("access denied")) {
            return SerialFailureType.DEVICE_PERMISSION_DENIED;
        }
        if (message.contains("no such file")
                || message.contains("path not found")
                || message.contains("does not exist")) {
            return SerialFailureType.DEVICE_PATH_NOT_FOUND;
        }
        if (message.contains("busy") || message.contains("in use")) {
            return SerialFailureType.DEVICE_BUSY;
        }
        if (message.contains("timeout") || message.contains("no response")) {
            return SerialFailureType.READ_TIMEOUT;
        }
        if (message.contains("disconnected")
                || message.contains("not connected")
                || message.contains("not open")
                || message.contains("failed to open serial port")) {
            return SerialFailureType.DEVICE_DISCONNECTED;
        }
        if (message.contains("unexpected") || message.contains("malformed") || message.contains("protocol")) {
            return SerialFailureType.PROTOCOL_ERROR;
        }

        return SerialFailureType.UNKNOWN_SERIAL_FAILURE;
    }

    private String safeMessage(Exception exception) {
        if (exception == null || exception.getMessage() == null || exception.getMessage().isBlank()) {
            return OperationMessages.UNKNOWN_PRINTER_MONITORING_ERROR;
        }

        return exception.getMessage();
    }

    private PrinterState resolveState(String response, Double hotendTemperature) {
        String normalized = response.toLowerCase(Locale.ROOT);

        if (normalized.contains("error")
                || normalized.contains("kill")
                || normalized.contains("halted")) {
            return PrinterState.ERROR;
        }

        if (normalized.contains("not sd printing")) {
            if (hotendTemperature != null
                    && hotendTemperature > PrinterProtocolDefaults.DEFAULT_HEATING_TEMPERATURE_THRESHOLD) {
                return PrinterState.HEATING;
            }
            return PrinterState.IDLE;
        }

        if (normalized.contains("busy")
                || normalized.contains("printing")) {
            return PrinterState.PRINTING;
        }

        if (hotendTemperature != null
                && hotendTemperature > PrinterProtocolDefaults.DEFAULT_HEATING_TEMPERATURE_THRESHOLD) {
            return PrinterState.HEATING;
        }

        if (normalized.contains("ok")
                || normalized.contains("t:")) {
            return PrinterState.IDLE;
        }

        return PrinterState.UNKNOWN;
    }

    private Double extractTemperature(Pattern pattern, String response) {
        Matcher matcher = pattern.matcher(response);

        if (!matcher.find()) {
            return null;
        }

        try {
            return Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
