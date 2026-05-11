package printerhub.command;

import printerhub.OperationMessages;
import printerhub.SerialIOMode;
import printerhub.command.SdCardUploadService.UploadProgress;
import printerhub.SerialConnection;
import printerhub.job.JobFailureReason;
import printerhub.job.PrintFile;
import printerhub.job.PrintFileService;
import printerhub.job.PrinterActionGuard;
import printerhub.job.PrinterSdFile;
import printerhub.job.PrinterSdFileService;
import printerhub.monitoring.PrinterMonitoringScheduler;
import printerhub.persistence.MonitoringRulesStore;
import printerhub.persistence.PrinterEventStore;
import printerhub.runtime.PrinterRegistry;
import printerhub.runtime.PrinterRuntimeNode;
import printerhub.config.SerialDefaults;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import printerhub.config.PrinterProtocolDefaults;

public final class SdCardUploadService {

    private final PrinterRegistry printerRegistry;
    private final PrinterMonitoringScheduler monitoringScheduler;
    private final PrinterActionGuard printerActionGuard;
    private final PrintFileService printFileService;
    private final SdCardService sdCardService;
    private final PrinterSdFileService printerSdFileService;
    private final PrinterEventStore printerEventStore;
    private final SdCardFileParser sdCardFileParser;

    private final BooleanSupplier debugWireTracingEnabledSupplier;
    private final IntSupplier sdUploadBatchSizeSupplier;
    private final IntSupplier sdUploadRecoveryWindowMultiplierSupplier;
    private final IntSupplier sdUploadMaxErrorsSupplier;
    private final IntSupplier sdUploadMaxConsecutiveIdenticalResendsSupplier;
    private final IntSupplier sdUploadMinPerformancePercentSupplier;
    private final ConcurrentMap<String, UploadProgress> uploadProgressByPrinterId = new ConcurrentHashMap<>();

    private static final String RESEND_OUTSIDE_RECOVERY_WINDOW_DETAIL = "Printer requested resend for line %d but that line was outside the recoverable resend window.";

    public SdCardUploadService(
            PrinterRegistry printerRegistry,
            PrinterMonitoringScheduler monitoringScheduler,
            PrinterActionGuard printerActionGuard,
            PrintFileService printFileService,
            SdCardService sdCardService,
            PrinterSdFileService printerSdFileService,
            PrinterEventStore printerEventStore) {
        this(
                printerRegistry,
                monitoringScheduler,
                printerActionGuard,
                printFileService,
                sdCardService,
                printerSdFileService,
                printerEventStore,
                () -> new MonitoringRulesStore().load().debugWireTracingEnabled(),
                () -> new MonitoringRulesStore().load().sdUploadBatchSize(),
                () -> new MonitoringRulesStore().load().sdUploadRecoveryWindowMultiplier(),
                () -> new MonitoringRulesStore().load().sdUploadMaxErrors(),
                () -> new MonitoringRulesStore().load().sdUploadMaxConsecutiveIdenticalResends(),
                () -> new MonitoringRulesStore().load().sdUploadMinPerformancePercent());
    }

    SdCardUploadService(
            PrinterRegistry printerRegistry,
            PrinterMonitoringScheduler monitoringScheduler,
            PrinterActionGuard printerActionGuard,
            PrintFileService printFileService,
            SdCardService sdCardService,
            PrinterSdFileService printerSdFileService,
            PrinterEventStore printerEventStore,
            BooleanSupplier debugWireTracingEnabledSupplier,
            IntSupplier sdUploadBatchSizeSupplier,
            IntSupplier sdUploadRecoveryWindowMultiplierSupplier,
            IntSupplier sdUploadMaxErrorsSupplier,
            IntSupplier sdUploadMaxConsecutiveIdenticalResendsSupplier,
            IntSupplier sdUploadMinPerformancePercentSupplier) {
        if (printerRegistry == null) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_REGISTRY_MUST_NOT_BE_NULL);
        }
        if (monitoringScheduler == null) {
            throw new IllegalArgumentException(OperationMessages.MONITORING_SCHEDULER_MUST_NOT_BE_NULL);
        }
        if (printerActionGuard == null) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_ACTION_GUARD_MUST_NOT_BE_NULL);
        }
        if (printFileService == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("printFileService"));
        }
        if (sdCardService == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("sdCardService"));
        }
        if (printerSdFileService == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("printerSdFileService"));
        }
        if (printerEventStore == null) {
            throw new IllegalArgumentException(OperationMessages.EVENT_STORE_MUST_NOT_BE_NULL);
        }
        if (debugWireTracingEnabledSupplier == null) {
            throw new IllegalArgumentException(
                    OperationMessages.fieldMustNotBeBlank("debugWireTracingEnabledSupplier"));
        }
        if (sdUploadBatchSizeSupplier == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("sdUploadBatchSizeSupplier"));
        }
        if (sdUploadRecoveryWindowMultiplierSupplier == null) {
            throw new IllegalArgumentException(
                    OperationMessages.fieldMustNotBeBlank("sdUploadRecoveryWindowMultiplierSupplier"));
        }
        if (sdUploadMaxErrorsSupplier == null) {
            throw new IllegalArgumentException(
                    OperationMessages.fieldMustNotBeBlank("sdUploadMaxErrorsSupplier"));
        }
        if (sdUploadMaxConsecutiveIdenticalResendsSupplier == null) {
            throw new IllegalArgumentException(
                    OperationMessages.fieldMustNotBeBlank("sdUploadMaxConsecutiveIdenticalResendsSupplier"));
        }
        if (sdUploadMinPerformancePercentSupplier == null) {
            throw new IllegalArgumentException(
                    OperationMessages.fieldMustNotBeBlank("sdUploadMinPerformancePercentSupplier"));
        }

        this.printerRegistry = printerRegistry;
        this.monitoringScheduler = monitoringScheduler;
        this.printerActionGuard = printerActionGuard;
        this.printFileService = printFileService;
        this.sdCardService = sdCardService;
        this.printerSdFileService = printerSdFileService;
        this.printerEventStore = printerEventStore;
        this.sdCardFileParser = new SdCardFileParser();
        this.debugWireTracingEnabledSupplier = debugWireTracingEnabledSupplier;
        this.sdUploadBatchSizeSupplier = sdUploadBatchSizeSupplier;
        this.sdUploadRecoveryWindowMultiplierSupplier = sdUploadRecoveryWindowMultiplierSupplier;
        this.sdUploadMaxErrorsSupplier = sdUploadMaxErrorsSupplier;
        this.sdUploadMaxConsecutiveIdenticalResendsSupplier = sdUploadMaxConsecutiveIdenticalResendsSupplier;
        this.sdUploadMinPerformancePercentSupplier = sdUploadMinPerformancePercentSupplier;
    }

    public Optional<UploadProgress> uploadProgress(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_ID_MUST_NOT_BE_BLANK);
        }

        return Optional.ofNullable(uploadProgressByPrinterId.get(printerId.trim()));
    }

    public UploadResult uploadToPrinterSd(
            String printerId,
            String printFileId,
            String requestedTargetFilename) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_ID_MUST_NOT_BE_BLANK);
        }
        if (printFileId == null || printFileId.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINT_FILE_ID_MUST_NOT_BE_BLANK);
        }

        PrinterRuntimeNode node = printerRegistry.findById(printerId.trim())
                .orElseThrow(() -> new IllegalStateException(OperationMessages.PRINTER_NOT_FOUND));

        PrinterActionGuard.GuardDecision decision = printerActionGuard.validateForSdUpload(node);
        if (!decision.allowed()) {
            throw new IllegalStateException(OperationMessages.safeDetail(
                    decision.detail(),
                    decision.failureReason() == null ? OperationMessages.PRECONDITION_FAILED
                            : decision.failureReason().name()));
        }

        PrintFile printFile = printFileService.findById(printFileId.trim())
                .orElseThrow(() -> new IllegalArgumentException(OperationMessages.PRINT_FILE_NOT_FOUND));

        Path hostPath = Path.of(printFile.path()).toAbsolutePath().normalize();
        validateHostFile(hostPath);
        UploadPlan uploadPlan = analyzeUploadPlan(hostPath);

        String targetFilename = normalizeTargetFilename(
                requestedTargetFilename,
                printFile.originalFilename());

        String executionToken = "sd-upload:" + printFile.id() + ":" + Instant.now();

        node.beginJobExecution(executionToken);
        monitoringScheduler.stopMonitoring(node.id());

        boolean writeOpened = false;
        int nextProtocolLineNumber = 2;
        UploadGuardState guardState = new UploadGuardState();

        try {

            updateUploadProgress(UploadProgress.running(
                    node.id(),
                    printFile.id(),
                    printFile.originalFilename(),
                    targetFilename,
                    uploadPlan.totalLineCount(),
                    uploadPlan.totalByteCount()));

            printerEventStore.record(
                    node.id(),
                    null,
                    "SD_CARD_UPLOAD_STARTED",
                    "SD upload started: host file "
                            + printFile.originalFilename()
                            + " -> "
                            + targetFilename
                            + " | totalLines="
                            + uploadPlan.totalLineCount()
                            + " | totalBytes="
                            + uploadPlan.totalByteCount());

            node.printerPort().connect();

            sendChecksummedLineWithRetry(
                    node,
                    0,
                    PrinterProtocolDefaults.COMMAND_RESET_LINE_NUMBER,
                    guardState,
                    printFile,
                    targetFilename);

            String startResponse = sendChecksummedLineWithRetry(
                    node,
                    1,
                    PrinterProtocolDefaults.COMMAND_OPEN_SD_WRITE + " " + targetFilename,
                    guardState,
                    printFile,
                    targetFilename);

            if (responseContainsOpenFailure(startResponse) || !responseContainsOk(startResponse)) {
                throw new IllegalStateException(
                        "Unexpected SD upload start response: "
                                + OperationMessages.safeDetail(startResponse, "no response"));
            }

            writeOpened = true;

            printerEventStore.record(
                    node.id(),
                    null,
                    "SD_CARD_UPLOAD_WRITE_OPENED",
                    "SD write opened: "
                            + targetFilename
                            + " | response="
                            + OperationMessages.safeDetail(startResponse, "no response"));

            StreamUploadResult streamResult = streamFileLinesWithChecksum(
                    node,
                    hostPath,
                    2,
                    uploadPlan,
                    printFile,
                    targetFilename,
                    guardState);

            long uploadedLineCount = streamResult.uploadedLineCount();
            nextProtocolLineNumber = streamResult.nextLineNumber();

            String finishResponse = sendChecksummedLineWithRetry(
                    node,
                    nextProtocolLineNumber,
                    PrinterProtocolDefaults.COMMAND_CLOSE_SD_WRITE,
                    guardState,
                    printFile,
                    targetFilename);
            nextProtocolLineNumber++;

            printerEventStore.record(
                    node.id(),
                    null,
                    "SD_CARD_UPLOAD_WRITE_FINISHED",
                    "SD write finished: "
                            + targetFilename
                            + " | lines="
                            + uploadedLineCount
                            + " | response="
                            + OperationMessages.safeDetail(finishResponse, "no response"));

            SdCardFileList fileList = listFilesWithChecksum(
                    node,
                    nextProtocolLineNumber,
                    guardState,
                    printFile,
                    targetFilename);

            for (SdCardFile file : fileList.files()) {
                printerSdFileService.registerListedFile(node.id(), file);
            }

            PrinterSdFile linkedFile = linkUploadedTarget(node.id(), targetFilename, printFile.id(), fileList.files());

            printerEventStore.record(
                    node.id(),
                    null,
                    "SD_CARD_UPLOAD_SUCCEEDED",
                    "SD upload succeeded: host file "
                            + printFile.originalFilename()
                            + " -> "
                            + linkedFile.firmwarePath());

            updateUploadProgress(UploadProgress.finished(
                    node.id(),
                    printFile.id(),
                    printFile.originalFilename(),
                    targetFilename,
                    uploadPlan.totalLineCount(),
                    uploadPlan.totalByteCount(),
                    uploadedLineCount,
                    rejectedLineCountFromProgress(node.id()),
                    "Uploaded as " + linkedFile.firmwarePath()));

            return new UploadResult(
                    node.id(),
                    printFile.id(),
                    printFile.originalFilename(),
                    targetFilename,
                    linkedFile.firmwarePath(),
                    linkedFile.id(),
                    uploadedLineCount,
                    uploadPlan.totalLineCount(),
                    uploadPlan.totalByteCount(),
                    rejectedLineCountFromProgress(node.id()),
                    true,
                    null);
        } catch (Exception exception) {
            if (writeOpened) {
                closeUploadSessionAfterFailure(
                        node,
                        nextProtocolLineNumber,
                        exception,
                        guardState,
                        printFile,
                        targetFilename);
            }

            String detail = OperationMessages.safeDetail(
                    exception.getMessage(),
                    JobFailureReason.UNKNOWN.name());

            updateUploadProgress(UploadProgress.failed(
                    node.id(),
                    printFile.id(),
                    printFile.originalFilename(),
                    targetFilename,
                    uploadPlan.totalLineCount(),
                    uploadPlan.totalByteCount(),
                    uploadedLineCountFromProgress(node.id()),
                    rejectedLineCountFromProgress(node.id()),
                    detail));

            try {
                printerEventStore.record(
                        node.id(),
                        null,
                        "SD_CARD_UPLOAD_FAILED",
                        "SD upload failed: host file "
                                + printFile.originalFilename()
                                + " -> "
                                + targetFilename
                                + " | detail="
                                + detail);
            } catch (Exception persistException) {
                System.err.println(OperationMessages.failedToPersistEvent(
                        node.id(),
                        OperationMessages.safeDetail(
                                persistException.getMessage(),
                                OperationMessages.FAILED_TO_SAVE_PRINTER_EVENT)));
            }

            throw exception;
        } finally {
            try {
                node.printerPort().disconnect();
            } catch (Exception exception) {
                System.err.println(OperationMessages.failedToDisconnectPrinterNode(
                        node.id(),
                        OperationMessages.safeDetail(
                                exception.getMessage(),
                                OperationMessages.UNKNOWN_RUNTIME_CLOSE_ERROR)));
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
                                OperationMessages.UNKNOWN_API_ERROR)));
            }
        }
    }

    public CloseUploadSessionResult closeOpenUploadSession(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_ID_MUST_NOT_BE_BLANK);
        }

        PrinterRuntimeNode node = printerRegistry.findById(printerId.trim())
                .orElseThrow(() -> new IllegalStateException(OperationMessages.PRINTER_NOT_FOUND));

        PrinterActionGuard.GuardDecision decision = printerActionGuard.validateForSdUpload(node);
        if (!decision.allowed()) {
            throw new IllegalStateException(OperationMessages.safeDetail(
                    decision.detail(),
                    decision.failureReason() == null ? OperationMessages.PRECONDITION_FAILED
                            : decision.failureReason().name()));
        }

        String executionToken = "sd-upload-recovery:" + Instant.now();

        node.beginJobExecution(executionToken);
        monitoringScheduler.stopMonitoring(node.id());

        int lineNumber = 2;
        String response = null;
        int attempts = 0;
        int maxAttempts = Math.max(1, sdUploadMaxConsecutiveIdenticalResendsSupplier.getAsInt());

        try {
            node.printerPort().connect();

            while (attempts < maxAttempts) {
                attempts++;

                response = sendChecksummedLineForRecoveryClose(
                        node,
                        lineNumber,
                        PrinterProtocolDefaults.COMMAND_CLOSE_SD_WRITE);

                printerEventStore.record(
                        node.id(),
                        null,
                        "SD_CARD_UPLOAD_RECOVERY_CLOSE_ATTEMPT",
                        "SD upload recovery close attempt: line="
                                + lineNumber
                                + " | attempt="
                                + attempts
                                + " | response="
                                + OperationMessages.safeDetail(response, "no response"));

                Integer requestedResendLine = requestedResendLine(response);
                if (requestedResendLine == null) {
                    printerEventStore.record(
                            node.id(),
                            null,
                            "SD_CARD_UPLOAD_RECOVERY_CLOSED",
                            "SD upload recovery close completed: line="
                                    + lineNumber
                                    + " | attempts="
                                    + attempts
                                    + " | response="
                                    + OperationMessages.safeDetail(response, "no response"));

                    return new CloseUploadSessionResult(
                            node.id(),
                            lineNumber,
                            attempts,
                            true,
                            response,
                            null);
                }

                int resendLine = requestedResendLine < 0 ? lineNumber : requestedResendLine;

                printerEventStore.record(
                        node.id(),
                        null,
                        "SD_CARD_UPLOAD_RECOVERY_RESEND_REQUESTED",
                        "SD upload recovery close needs resend: currentLine="
                                + lineNumber
                                + " | requestedLine="
                                + resendLine
                                + " | attempt="
                                + attempts
                                + " | response="
                                + OperationMessages.safeDetail(response, "no response"));

                lineNumber = resendLine;
            }

            String detail = "Failed to close SD upload session after "
                    + maxAttempts
                    + " attempts. Last response: "
                    + OperationMessages.safeDetail(response, "no response");

            printerEventStore.record(
                    node.id(),
                    null,
                    "SD_CARD_UPLOAD_RECOVERY_CLOSE_FAILED",
                    detail);

            throw new IllegalStateException(detail);
        } finally {
            try {
                node.printerPort().disconnect();
            } catch (Exception exception) {
                System.err.println(OperationMessages.failedToDisconnectPrinterNode(
                        node.id(),
                        OperationMessages.safeDetail(
                                exception.getMessage(),
                                OperationMessages.UNKNOWN_RUNTIME_CLOSE_ERROR)));
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
                                OperationMessages.UNKNOWN_API_ERROR)));
            }
        }
    }

    private StreamUploadResult streamFileLinesWithChecksum(
            PrinterRuntimeNode node,
            Path hostPath,
            int firstLineNumber,
            UploadPlan uploadPlan,
            PrintFile printFile,
            String targetFilename,
            UploadGuardState guardState) {
        long uploadedLineCount = 0;
        long nextProgressEventPercent = 10;
        int configuredBatchSize = Math.max(1, sdUploadBatchSizeSupplier.getAsInt());
        int recoveryWindowMultiplier = Math.max(1, sdUploadRecoveryWindowMultiplierSupplier.getAsInt());

        RecentWindowHistory windowHistory = new RecentWindowHistory(configuredBatchSize, recoveryWindowMultiplier);

        try (BufferedReader reader = Files.newBufferedReader(hostPath)) {
            String rawLine;
            List<String> batchLines = new ArrayList<>();
            List<Integer> batchLineNumbers = new ArrayList<>();

            while ((rawLine = reader.readLine()) != null) {
                String payload = normalizeUploadPayload(rawLine);
                if (payload == null) {
                    continue;
                }

                int lineNumber = firstLineNumber + (int) uploadedLineCount;
                uploadedLineCount++;

                batchLines.add(payload);
                batchLineNumbers.add(lineNumber);

                int effectiveBatchSize = guardState.forceSingleSendMode() ? 1 : configuredBatchSize;

                if (batchLines.size() >= effectiveBatchSize) {
                    sendBatchWithChecksum(
                            node,
                            batchLines,
                            batchLineNumbers,
                            windowHistory,
                            guardState,
                            printFile,
                            targetFilename);
                    batchLines.clear();
                    batchLineNumbers.clear();
                }

                UploadProgress currentProgress = uploadProgressByPrinterId.get(node.id());
                Instant startedAt = currentProgress == null ? Instant.now() : currentProgress.startedAt();

                updateUploadProgress(UploadProgress.progress(
                        node.id(),
                        printFile.id(),
                        printFile.originalFilename(),
                        targetFilename,
                        uploadPlan.totalLineCount(),
                        uploadPlan.totalByteCount(),
                        uploadedLineCount,
                        rejectedLineCountFromProgress(node.id()),
                        startedAt));

                enforceMinPerformanceThreshold(node, printFile, targetFilename);

                if (uploadPlan.totalLineCount() > 0) {
                    long percent = uploadedLineCount * 100 / uploadPlan.totalLineCount();
                    if (percent >= nextProgressEventPercent) {
                        printerEventStore.record(
                                node.id(),
                                null,
                                "SD_CARD_UPLOAD_PROGRESS",
                                "SD upload progress: "
                                        + uploadedLineCount
                                        + "/"
                                        + uploadPlan.totalLineCount()
                                        + " lines ("
                                        + percent
                                        + "%)");

                        while (nextProgressEventPercent <= percent) {
                            nextProgressEventPercent += 10;
                        }
                    }
                }
            }

            if (!batchLines.isEmpty()) {
                sendBatchWithChecksum(
                        node,
                        batchLines,
                        batchLineNumbers,
                        windowHistory,
                        guardState,
                        printFile,
                        targetFilename);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(OperationMessages.PRINT_FILE_MUST_BE_READABLE, exception);
        }

        int nextLineNumber = firstLineNumber + (int) uploadedLineCount;
        return new StreamUploadResult(uploadedLineCount, nextLineNumber);
    }

    private void sendBatchWithChecksum(
            PrinterRuntimeNode node,
            List<String> payloads,
            List<Integer> lineNumbers,
            RecentWindowHistory windowHistory,
            UploadGuardState guardState,
            PrintFile printFile,
            String targetFilename) {
        if (payloads.size() != lineNumbers.size()) {
            throw new IllegalArgumentException("Payloads and line numbers lists must have the same size");
        }

        if (payloads.isEmpty()) {
            return;
        }

        if (windowHistory == null) {
            throw new IllegalArgumentException("windowHistory must not be null");
        }
        if (guardState == null) {
            throw new IllegalArgumentException("guardState must not be null");
        }

        if (guardState.forceSingleSendMode() || payloads.size() == 1) {
            for (int i = 0; i < payloads.size(); i++) {
                int lineNumber = lineNumbers.get(i);
                String payload = payloads.get(i);

                windowHistory.storeWindow(List.of(lineNumber), List.of(payload));

                String response = sendChecksummedLineOnce(
                        node,
                        lineNumber,
                        payload,
                        printFile,
                        targetFilename);
                Integer requestedResendLine = requestedResendLine(response);

                if (requestedResendLine != null) {
                    incrementRejectedLineCount(node.id());

                    int resendLine = requestedResendLine < 0 ? lineNumber : requestedResendLine;

                    guardState.enableSingleSendMode();

                    registerIdenticalResendAndEnforceThreshold(
                            node,
                            guardState,
                            resendLine,
                            printFile,
                            targetFilename);
                    enforceMaxErrorThreshold(node, printFile, targetFilename);

                    if (!windowHistory.isRecoverable(resendLine)) {
                        throw new SdUploadLineException(
                                resendLine,
                                String.format(Locale.ROOT, RESEND_OUTSIDE_RECOVERY_WINDOW_DETAIL, resendLine));
                    }

                    replayRecoveryStateMachine(
                            node,
                            windowHistory,
                            resendLine,
                            guardState,
                            printFile,
                            targetFilename);
                }
            }

            return;
        }

        sendBatchPipelined(
                node,
                payloads,
                lineNumbers,
                windowHistory,
                guardState,
                printFile,
                targetFilename);
    }

    private void sendBatchPipelined(
            PrinterRuntimeNode node,
            List<String> payloads,
            List<Integer> lineNumbers,
            RecentWindowHistory windowHistory,
            UploadGuardState guardState,
            PrintFile printFile,
            String targetFilename) {
        if (payloads.size() != lineNumbers.size()) {
            throw new IllegalArgumentException("Payloads and line numbers lists must have the same size");
        }
        if (payloads.isEmpty()) {
            return;
        }
        if (windowHistory == null) {
            throw new IllegalArgumentException("windowHistory must not be null");
        }
        if (guardState == null) {
            throw new IllegalArgumentException("guardState must not be null");
        }

        windowHistory.storeWindow(lineNumbers, payloads);

        List<String> checksummedLines = new ArrayList<>(payloads.size());
        for (int i = 0; i < payloads.size(); i++) {
            checksummedLines.add(buildChecksummedLine(lineNumbers.get(i), payloads.get(i)));
        }

        int resendLine = sendAndReadPipelinedWindow(
                node,
                checksummedLines,
                lineNumbers,
                guardState,
                printFile,
                targetFilename);
        if (resendLine < 0) {
            return;
        }

        if (!windowHistory.isRecoverable(resendLine)) {
            throw new SdUploadLineException(
                    resendLine,
                    String.format(Locale.ROOT, RESEND_OUTSIDE_RECOVERY_WINDOW_DETAIL, resendLine));
        }

        printerEventStore.record(
                node.id(),
                null,
                "SD_CARD_UPLOAD_RECOVERY_STARTED",
                "SD upload recovery started from line "
                        + resendLine
                        + " | oldestRecoverable="
                        + windowHistory.oldestRecoverableLineNumber()
                        + " | newestSent="
                        + windowHistory.newestSentLineNumber());

        replayRecoveryStateMachine(
                node,
                windowHistory,
                resendLine,
                guardState,
                printFile,
                targetFilename);
    }

    private int sendAndReadPipelinedWindow(
            PrinterRuntimeNode node,
            List<String> checksummedLines,
            List<Integer> lineNumbers,
            UploadGuardState guardState,
            PrintFile printFile,
            String targetFilename) {
        for (String checksummedLine : checksummedLines) {
            logUploadWire(node.id(), "->", checksummedLine + " [pipelined]");
            node.printerPort().writeRawLine(checksummedLine, SerialIOMode.FILE_STREAMING);
        }

        for (int i = 0; i < lineNumbers.size(); i++) {
            String response = node.printerPort().readRawResponse(SerialIOMode.FILE_STREAMING);
            int lineNumber = lineNumbers.get(i);

            logUploadWire(node.id(), "<-", response);

            Integer requestedResendLine = requestedResendLine(response);
            if (requestedResendLine != null) {
                incrementRejectedLineCount(node.id());

                int resendLine = requestedResendLine < 0 ? lineNumber : requestedResendLine;

                guardState.enableSingleSendMode();

                registerIdenticalResendAndEnforceThreshold(
                        node,
                        guardState,
                        resendLine,
                        printFile,
                        targetFilename);
                enforceMaxErrorThreshold(node, printFile, targetFilename);

                printerEventStore.record(
                        node.id(),
                        null,
                        "SD_CARD_UPLOAD_RESEND_REQUESTED",
                        "SD upload resend requested during pipelined upload: line="
                                + resendLine
                                + " | response="
                                + OperationMessages.safeDetail(response, "no response"));

                discardPendingUploadInput(node);

                printerEventStore.record(
                        node.id(),
                        null,
                        "SD_CARD_UPLOAD_CHANNEL_DRAINED",
                        "SD upload drained pending serial input before resend recovery: resendLine="
                                + resendLine);

                return resendLine;
            }

            if (responseContainsUploadError(response)) {
                incrementRejectedLineCount(node.id());
                enforceMaxErrorThreshold(node, printFile, targetFilename);

                throw new SdUploadLineException(
                        lineNumber,
                        "Printer reported SD upload error for line "
                                + lineNumber
                                + ": "
                                + OperationMessages.safeDetail(response, "no response"));
            }

            if (!responseContainsOk(response)) {
                throw new SdUploadLineException(
                        lineNumber,
                        "Unexpected pipelined SD upload response for line "
                                + lineNumber
                                + ": "
                                + OperationMessages.safeDetail(response, "no response"));
            }
        }

        return -1;
    }

    private UploadPlan analyzeUploadPlan(Path hostPath) {
        long totalLineCount = 0;

        try (BufferedReader reader = Files.newBufferedReader(hostPath)) {
            String rawLine;

            while ((rawLine = reader.readLine()) != null) {
                if (normalizeUploadPayload(rawLine) != null) {
                    totalLineCount++;
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException(OperationMessages.PRINT_FILE_MUST_BE_READABLE, exception);
        }

        try {
            return new UploadPlan(totalLineCount, Files.size(hostPath));
        } catch (IOException exception) {
            throw new IllegalStateException(OperationMessages.PRINT_FILE_MUST_BE_READABLE, exception);
        }
    }

    private void updateUploadProgress(UploadProgress progress) {
        uploadProgressByPrinterId.put(progress.printerId(), progress);
    }

    private long uploadedLineCountFromProgress(String printerId) {
        UploadProgress progress = uploadProgressByPrinterId.get(printerId);
        return progress == null ? 0L : progress.uploadedLineCount();
    }

    private long rejectedLineCountFromProgress(String printerId) {
        UploadProgress progress = uploadProgressByPrinterId.get(printerId);
        return progress == null ? 0L : progress.rejectedLineCount();
    }

    private void incrementRejectedLineCount(String printerId) {
        UploadProgress current = uploadProgressByPrinterId.get(printerId);
        if (current == null) {
            return;
        }

        updateUploadProgress(current.withRejectedLineCount(current.rejectedLineCount() + 1L));
    }

    private SdCardFileList listFilesWithChecksum(
            PrinterRuntimeNode node,
            int lineNumber,
            UploadGuardState guardState,
            PrintFile printFile,
            String targetFilename) {
        String response = sendChecksummedLineWithRetry(
                node,
                lineNumber,
                PrinterProtocolDefaults.COMMAND_LIST_SD_FILES,
                guardState,
                printFile,
                targetFilename);
        SdCardFileList fileList = new SdCardFileList(node.id(), sdCardFileParser.parse(response), response);

        printerEventStore.record(
                node.id(),
                null,
                OperationMessages.EVENT_SD_CARD_FILES_LISTED,
                OperationMessages.sdCardFilesListed(fileList.files().size()));

        return fileList;
    }

    private String sendChecksummedLineOnce(
            PrinterRuntimeNode node,
            int lineNumber,
            String payload,
            PrintFile printFile,
            String targetFilename) {
        long startTime = System.nanoTime();
        String checksummedLine = buildChecksummedLine(lineNumber, payload);
        long checksumTime = System.nanoTime();

        long sendStartTime = System.nanoTime();
        long sendTimestamp = System.currentTimeMillis();
        logUploadWire(node.id(), "->", checksummedLine + " [single-send]");
        String response = node.printerPort().sendRawLine(checksummedLine, SerialIOMode.FILE_STREAMING);
        long responseTime = System.nanoTime();
        long responseTimestamp = System.currentTimeMillis();

        int sleepCycles = 0;
        if (node.printerPort() instanceof SerialConnection) {
            sleepCycles = ((SerialConnection) node.printerPort()).getLastSleepCycles();
        }

        logUploadWire(node.id(), "<-", response);

        if (debugWireTracingEnabledSupplier.getAsBoolean()) {
            long checksumMs = (checksumTime - startTime) / 1_000_000;
            long sendWaitMs = (responseTime - sendStartTime) / 1_000_000;
            long wallClockMs = responseTimestamp - sendTimestamp;
            long totalMs = (responseTime - startTime) / 1_000_000;
            System.out.println("[PrinterHub] SD upload timing " + node.id() + " line=" + lineNumber
                    + " single-send checksum=" + checksumMs + "ms send+wait=" + sendWaitMs + "ms wall="
                    + wallClockMs + "ms sleeps=" + sleepCycles + " total=" + totalMs + "ms");
        }

        if (responseContainsUploadError(response) && requestedResendLine(response) == null) {
            incrementRejectedLineCount(node.id());
            enforceMaxErrorThreshold(node, printFile, targetFilename);

            throw new SdUploadLineException(
                    lineNumber,
                    "Printer reported SD upload error for line "
                            + lineNumber
                            + ": "
                            + OperationMessages.safeDetail(response, "no response"));
        }

        if (responseContainsOk(response) || requestedResendLine(response) != null) {
            return response;
        }

        throw new SdUploadLineException(
                lineNumber,
                "Unexpected SD upload response for line "
                        + lineNumber
                        + ": "
                        + OperationMessages.safeDetail(response, "no response"));
    }

    private String sendChecksummedLineWithRetry(
            PrinterRuntimeNode node,
            int lineNumber,
            String payload,
            UploadGuardState guardState,
            PrintFile printFile,
            String targetFilename) {
        long startTime = System.nanoTime();
        String checksummedLine = buildChecksummedLine(lineNumber, payload);
        long checksumTime = System.nanoTime();

        String lastResponse = null;

        for (int attempt = 1; attempt <= PrinterProtocolDefaults.SD_UPLOAD_MAX_RETRIES_PER_LINE; attempt++) {
            long sendStartTime = System.nanoTime();
            long sendTimestamp = System.currentTimeMillis();
            logUploadWire(node.id(), "->", checksummedLine + " [attempt " + attempt + "]");
            lastResponse = node.printerPort().sendRawLine(checksummedLine, SerialIOMode.FILE_STREAMING);
            long responseTime = System.nanoTime();
            long responseTimestamp = System.currentTimeMillis();

            int sleepCycles = 0;
            if (node.printerPort() instanceof SerialConnection) {
                sleepCycles = ((SerialConnection) node.printerPort()).getLastSleepCycles();
            }

            logUploadWire(node.id(), "<-", lastResponse);

            if (debugWireTracingEnabledSupplier.getAsBoolean()) {
                long checksumMs = (checksumTime - startTime) / 1_000_000;
                long sendWaitMs = (responseTime - sendStartTime) / 1_000_000;
                long wallClockMs = responseTimestamp - sendTimestamp;
                long totalMs = (responseTime - startTime) / 1_000_000;
                System.out.println("[PrinterHub] SD upload timing " + node.id() + " line=" + lineNumber
                        + " attempt=" + attempt
                        + " checksum=" + checksumMs + "ms"
                        + " send+wait=" + sendWaitMs + "ms"
                        + " wall=" + wallClockMs + "ms"
                        + " sleeps=" + sleepCycles
                        + " total=" + totalMs + "ms");
            }

            Integer requestedResendLine = requestedResendLine(lastResponse);
            if (requestedResendLine != null && requestedResendLine == lineNumber) {
                incrementRejectedLineCount(node.id());

                if (guardState != null && printFile != null && targetFilename != null) {
                    registerIdenticalResendAndEnforceThreshold(
                            node,
                            guardState,
                            lineNumber,
                            printFile,
                            targetFilename);
                    enforceMaxErrorThreshold(node, printFile, targetFilename);
                }

                printerEventStore.record(
                        node.id(),
                        null,
                        "SD_CARD_UPLOAD_RESEND_REQUESTED",
                        "SD upload resend requested: line="
                                + lineNumber
                                + " | attempt="
                                + attempt
                                + " | response="
                                + OperationMessages.safeDetail(lastResponse, "no response"));
                continue;
            }

            if (requestedResendLine != null) {
                incrementRejectedLineCount(node.id());

                if (guardState != null && printFile != null && targetFilename != null) {
                    int resendLine = requestedResendLine < 0 ? lineNumber : requestedResendLine;
                    registerIdenticalResendAndEnforceThreshold(
                            node,
                            guardState,
                            resendLine,
                            printFile,
                            targetFilename);
                    enforceMaxErrorThreshold(node, printFile, targetFilename);
                }

                throw new SdUploadLineException(
                        requestedResendLine < 0 ? lineNumber : requestedResendLine,
                        "Printer requested resend for line "
                                + (requestedResendLine < 0 ? "unknown" : requestedResendLine)
                                + " while PrinterHub was uploading line "
                                + lineNumber
                                + ": "
                                + OperationMessages.safeDetail(lastResponse, "no response"));
            }

            if (responseContainsUploadError(lastResponse)) {
                incrementRejectedLineCount(node.id());

                if (printFile != null && targetFilename != null) {
                    enforceMaxErrorThreshold(node, printFile, targetFilename);
                }

                throw new SdUploadLineException(
                        lineNumber,
                        "Printer reported SD upload error for line "
                                + lineNumber
                                + ": "
                                + OperationMessages.safeDetail(lastResponse, "no response"));
            }

            if (responseContainsOk(lastResponse)) {
                return lastResponse;
            }

            throw new SdUploadLineException(
                    lineNumber,
                    "Unexpected SD upload response for line "
                            + lineNumber
                            + ": "
                            + OperationMessages.safeDetail(lastResponse, "no response"));
        }

        if (printFile != null && targetFilename != null) {
            incrementRejectedLineCount(node.id());
            enforceMaxErrorThreshold(node, printFile, targetFilename);
        }

        throw new SdUploadLineException(
                lineNumber,
                "SD upload exceeded retry limit for line "
                        + lineNumber
                        + ": "
                        + OperationMessages.safeDetail(lastResponse, "no response"));
    }

    private void replayRecoveryStateMachine(
            PrinterRuntimeNode node,
            RecentWindowHistory windowHistory,
            int resendLine,
            UploadGuardState guardState,
            PrintFile printFile,
            String targetFilename) {
        guardState.enableSingleSendMode();

        int replayCursor = resendLine;

        while (replayCursor <= windowHistory.newestSentLineNumber()) {
            BufferedWindow.WindowLine windowLine = windowHistory.findLine(replayCursor);
            if (windowLine == null) {
                throw new SdUploadLineException(
                        replayCursor,
                        "Recovery could not locate buffered payload for line " + replayCursor + ".");
            }

            String response = sendChecksummedLineOnce(
                    node,
                    windowLine.lineNumber(),
                    windowLine.payload(),
                    printFile,
                    targetFilename);

            Integer requestedResendLine = requestedResendLine(response);
            if (requestedResendLine != null) {
                incrementRejectedLineCount(node.id());

                int resendTarget = requestedResendLine < 0 ? replayCursor : requestedResendLine;

                registerIdenticalResendAndEnforceThreshold(
                        node,
                        guardState,
                        resendTarget,
                        printFile,
                        targetFilename);
                enforceMaxErrorThreshold(node, printFile, targetFilename);

                if (!windowHistory.isRecoverable(resendTarget)) {
                    throw new SdUploadLineException(
                            resendTarget,
                            String.format(Locale.ROOT, RESEND_OUTSIDE_RECOVERY_WINDOW_DETAIL, resendTarget));
                }

                printerEventStore.record(
                        node.id(),
                        null,
                        "SD_CARD_UPLOAD_RECOVERY_JUMP",
                        "SD upload recovery jump: currentLine="
                                + replayCursor
                                + " -> resendLine="
                                + resendTarget);

                sleepRecoveryReplayDelay();
                replayCursor = resendTarget;
                continue;
            }

            sleepRecoveryReplayDelay();
            replayCursor++;
        }

        printerEventStore.record(
                node.id(),
                null,
                "SD_CARD_UPLOAD_RECOVERY_COMPLETED",
                "SD upload recovery completed up to line " + windowHistory.newestSentLineNumber());
    }

    private String normalizeUploadPayload(String rawLine) {
        if (rawLine == null) {
            return null;
        }

        String payload = rawLine.replace("\r", "");
        int commentIndex = payload.indexOf(';');

        if (commentIndex >= 0) {
            payload = payload.substring(0, commentIndex).stripTrailing();
        }

        if (payload.isBlank()) {
            return null;
        }

        return payload;
    }

    private void closeUploadSessionAfterFailure(
            PrinterRuntimeNode node,
            int nextProtocolLineNumber,
            Exception exception,
            UploadGuardState guardState,
            PrintFile printFile,
            String targetFilename) {
        int closeLineNumber = Math.max(2, nextProtocolLineNumber);

        if (exception instanceof SdUploadLineException uploadException) {
            closeLineNumber = Math.max(closeLineNumber, uploadException.lineNumber());
        }

        try {
            String response = sendChecksummedLineWithRetry(
                    node,
                    closeLineNumber,
                    PrinterProtocolDefaults.COMMAND_CLOSE_SD_WRITE,
                    guardState,
                    printFile,
                    targetFilename);

            printerEventStore.record(
                    node.id(),
                    null,
                    "SD_CARD_UPLOAD_RECOVERY_CLOSED",
                    "SD upload recovery close sent: line="
                            + closeLineNumber
                            + " | response="
                            + OperationMessages.safeDetail(response, "no response"));
        } catch (Exception closeException) {
            System.err.println(OperationMessages.apiOperationFailed(
                    "Failed to close SD upload session after upload failure: "
                            + OperationMessages.safeDetail(
                                    closeException.getMessage(),
                                    OperationMessages.UNKNOWN_API_ERROR)));
        }
    }

    private void enforceMaxErrorThreshold(
            PrinterRuntimeNode node,
            PrintFile printFile,
            String targetFilename) {
        int maxErrors = Math.max(1, sdUploadMaxErrorsSupplier.getAsInt());
        long rejectedLineCount = rejectedLineCountFromProgress(node.id());

        if (rejectedLineCount < maxErrors) {
            return;
        }

        String detail = "SD upload aborted because cumulative protocol errors reached the configured limit: "
                + "rejectedLineCount="
                + rejectedLineCount
                + " | maxErrors="
                + maxErrors
                + " | hostFile="
                + printFile.originalFilename()
                + " | targetFilename="
                + targetFilename;

        recordUploadAbortEvent(node, detail);
        throw new IllegalStateException(detail);
    }

    private void registerIdenticalResendAndEnforceThreshold(
            PrinterRuntimeNode node,
            UploadGuardState guardState,
            int resendLine,
            PrintFile printFile,
            String targetFilename) {
        if (resendLine < 0) {
            return;
        }

        int consecutiveIdenticalResends = guardState.registerResend(resendLine);
        int maxConsecutiveIdenticalResends = Math.max(1, sdUploadMaxConsecutiveIdenticalResendsSupplier.getAsInt());

        if (consecutiveIdenticalResends < maxConsecutiveIdenticalResends) {
            return;
        }

        String detail = "SD upload aborted because the printer kept requesting the same resend line: "
                + "resendLine="
                + resendLine
                + " | consecutiveIdenticalResends="
                + consecutiveIdenticalResends
                + " | maxConsecutiveIdenticalResends="
                + maxConsecutiveIdenticalResends
                + " | hostFile="
                + printFile.originalFilename()
                + " | targetFilename="
                + targetFilename;

        recordUploadAbortEvent(node, detail);
        throw new SdUploadLineException(resendLine, detail);
    }

    private void enforceMinPerformanceThreshold(
            PrinterRuntimeNode node,
            PrintFile printFile,
            String targetFilename) {
        int minPerformancePercent = Math.max(0, sdUploadMinPerformancePercentSupplier.getAsInt());

        if (minPerformancePercent <= 0) {
            return;
        }

        UploadProgress progress = uploadProgressByPrinterId.get(node.id());
        if (progress == null || !progress.active()) {
            return;
        }

        if (progress.elapsedSeconds() < 30L) {
            return;
        }

        if (progress.uploadedLineCount() < 200L) {
            return;
        }

        double efficiencyPercent = progress.efficiencyPercent();
        if (efficiencyPercent >= minPerformancePercent) {
            return;
        }

        String detail = "SD upload aborted because effective performance stayed below the configured minimum: "
                + "efficiencyPercent="
                + String.format(Locale.ROOT, "%.1f", efficiencyPercent)
                + " | minPerformancePercent="
                + minPerformancePercent
                + " | uploadedLineCount="
                + progress.uploadedLineCount()
                + " | rejectedLineCount="
                + progress.rejectedLineCount()
                + " | elapsedSeconds="
                + progress.elapsedSeconds()
                + " | hostFile="
                + printFile.originalFilename()
                + " | targetFilename="
                + targetFilename;

        recordUploadAbortEvent(node, detail);
        throw new IllegalStateException(detail);
    }

    private void recordUploadAbortEvent(
            PrinterRuntimeNode node,
            String detail) {
        try {
            printerEventStore.record(
                    node.id(),
                    null,
                    "SD_CARD_UPLOAD_ABORTED",
                    detail);
        } catch (Exception persistException) {
            System.err.println(OperationMessages.failedToPersistEvent(
                    node.id(),
                    OperationMessages.safeDetail(
                            persistException.getMessage(),
                            OperationMessages.FAILED_TO_SAVE_PRINTER_EVENT)));
        }
    }

    private String buildChecksummedLine(int lineNumber, String payload) {
        String normalizedPayload = payload == null ? "" : payload;
        String withoutChecksum = "N" + lineNumber + " " + normalizedPayload;
        int checksum = 0;

        for (int index = 0; index < withoutChecksum.length(); index++) {
            checksum ^= withoutChecksum.charAt(index);
        }

        return withoutChecksum + "*" + checksum;
    }

    private void logUploadWire(String printerId, String direction, String detail) {
        if (!debugWireTracingEnabledSupplier.getAsBoolean()) {
            return;
        }
        System.out.println("[PrinterHub] SD upload wire " + printerId + " " + direction + " "
                + sanitizeWireDetail(detail));
    }

    private String sanitizeWireDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return "<no response>";
        }

        return detail
                .replace("\r", "")
                .replace("\n", " | ")
                .trim();
    }

    private boolean responseContainsOk(String response) {
        if (response == null || response.isBlank()) {
            return false;
        }

        return response.toLowerCase(Locale.ROOT).contains("ok");
    }

    private boolean responseContainsOpenFailure(String response) {
        if (response == null || response.isBlank()) {
            return false;
        }

        String normalized = response.toLowerCase(Locale.ROOT);
        return normalized.contains("open failed");
    }

    private Integer requestedResendLine(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }

        String normalized = response.toLowerCase(Locale.ROOT);

        if (!normalized.contains("resend")) {
            return null;
        }

        Integer requestedLine = extractRequestedResendLine(response);

        if (requestedLine == null) {
            return -1;
        }

        return requestedLine;
    }

    private boolean responseContainsUploadError(String response) {
        if (response == null || response.isBlank()) {
            return false;
        }

        String normalized = response.toLowerCase(Locale.ROOT);
        return normalized.contains("error:");
    }

    private Integer extractRequestedResendLine(String response) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?i)resend\\s*:\\s*(\\d+)")
                .matcher(response);

        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        return null;
    }

    private PrinterSdFile linkUploadedTarget(
            String printerId,
            String targetFilename,
            String printFileId,
            List<SdCardFile> listedFiles) {
        Optional<PrinterSdFile> exactMatch = printerSdFileService.findByPrinterIdAndFirmwarePath(printerId,
                targetFilename);

        if (exactMatch.isPresent()) {
            PrinterSdFile file = exactMatch.get();
            return printerSdFileService.register(
                    file.printerId(),
                    file.firmwarePath(),
                    file.displayName(),
                    file.sizeBytes(),
                    file.rawLine(),
                    printFileId);
        }

        Optional<SdCardFile> listedMatch = listedFiles.stream()
                .filter(file -> sameFirmwarePath(file.filename(), targetFilename))
                .findFirst();

        if (listedMatch.isPresent()) {
            SdCardFile file = listedMatch.get();
            return printerSdFileService.register(
                    printerId,
                    file.filename(),
                    file.filename(),
                    file.sizeBytes(),
                    file.rawLine(),
                    printFileId);
        }

        throw new IllegalStateException(
                "Uploaded file was not confirmed by printer SD listing: "
                        + targetFilename);
    }

    private boolean sameFirmwarePath(String a, String b) {
        if (a == null || b == null) {
            return false;
        }

        return a.trim().equalsIgnoreCase(b.trim());
    }

    private void validateHostFile(Path hostPath) {
        if (!Files.exists(hostPath)) {
            throw new IllegalArgumentException(OperationMessages.PRINT_FILE_MUST_EXIST);
        }
        if (!Files.isRegularFile(hostPath)) {
            throw new IllegalArgumentException(OperationMessages.PRINT_FILE_MUST_BE_REGULAR_FILE);
        }
        if (!Files.isReadable(hostPath)) {
            throw new IllegalArgumentException(OperationMessages.PRINT_FILE_MUST_BE_READABLE);
        }
    }

    private String normalizeTargetFilename(String requestedTargetFilename, String originalFilename) {
        String candidate = requestedTargetFilename;

        if (candidate == null || candidate.isBlank()) {
            candidate = originalFilename;
        }

        if (candidate == null || candidate.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINT_FILE_FILENAME_MUST_NOT_BE_BLANK);
        }

        String normalized = Path.of(candidate.trim()).getFileName().toString();
        normalized = normalized.replaceAll("[^A-Za-z0-9._-]", "_");

        if (normalized.isBlank() || ".".equals(normalized) || "..".equals(normalized)) {
            throw new IllegalArgumentException(OperationMessages.PRINT_FILE_FILENAME_MUST_NOT_BE_BLANK);
        }

        String upper = normalized.toUpperCase(Locale.ROOT);
        int extensionIndex = upper.lastIndexOf('.');
        String stem = extensionIndex >= 0 ? upper.substring(0, extensionIndex) : upper;
        String extension = extensionIndex >= 0 ? upper.substring(extensionIndex + 1) : "";

        if (extension.isBlank()) {
            extension = "GCO";
        } else if ("GCODE".equals(extension)) {
            extension = "GCO";
        } else if (extension.length() > 3) {
            extension = extension.substring(0, 3);
        }

        stem = stem.replaceAll("[^A-Z0-9_-]", "_");
        if (stem.length() > 8) {
            stem = stem.substring(0, 8);
        }
        if (stem.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINT_FILE_FILENAME_MUST_NOT_BE_BLANK);
        }

        return stem + "." + extension;
    }

    public record UploadResult(
            String printerId,
            String printFileId,
            String originalFilename,
            String requestedTargetFilename,
            String linkedFirmwarePath,
            String printerSdFileId,
            long uploadedLineCount,
            long totalLineCount,
            long totalByteCount,
            long rejectedLineCount,
            boolean success,
            String detail) {
    }

    private record UploadPlan(long totalLineCount, long totalByteCount) {
    }

    private record StreamUploadResult(long uploadedLineCount, int nextLineNumber) {
    }

    public record UploadProgress(
            String printerId,
            String printFileId,
            String originalFilename,
            String requestedTargetFilename,
            String state,
            boolean active,
            long uploadedLineCount,
            long totalLineCount,
            long totalByteCount,
            long rejectedLineCount,
            Instant startedAt,
            Instant updatedAt,
            String detail) {

        /**
         * Calculate the current upload rate in bytes per second.
         * Returns 0 if no progress has been made or insufficient time has elapsed.
         */
        public double bytesPerSecond() {
            if (startedAt == null || updatedAt == null || uploadedLineCount == 0) {
                return 0.0;
            }

            long elapsedMs = updatedAt.toEpochMilli() - startedAt.toEpochMilli();
            if (elapsedMs <= 0) {
                return 0.0;
            }

            // Estimate bytes uploaded based on line count (rough approximation)
            // This is an estimate since we don't track exact bytes per line
            long estimatedBytesUploaded = (long) (uploadedLineCount * 50.0); // Average ~50 bytes per line
            return (estimatedBytesUploaded * 1000.0) / elapsedMs;
        }

        /**
         * Calculate the current upload rate in lines per second.
         * Returns 0 if no progress has been made or insufficient time has elapsed.
         */
        public double linesPerSecond() {
            if (startedAt == null || updatedAt == null || uploadedLineCount == 0) {
                return 0.0;
            }

            long elapsedMs = updatedAt.toEpochMilli() - startedAt.toEpochMilli();
            if (elapsedMs <= 0) {
                return 0.0;
            }

            return (uploadedLineCount * 1000.0) / elapsedMs;
        }

        /**
         * Calculate the elapsed time in seconds since upload started.
         */
        public long elapsedSeconds() {
            if (startedAt == null || updatedAt == null) {
                return 0L;
            }

            return (updatedAt.toEpochMilli() - startedAt.toEpochMilli()) / 1000L;
        }

        /**
         * Calculate the estimated time remaining in seconds.
         * Returns 0 if no progress has been made or upload is complete.
         */
        public long estimatedSecondsRemaining() {
            if (!active || uploadedLineCount == 0 || totalLineCount == 0) {
                return 0L;
            }

            double linesPerSecond = linesPerSecond();
            if (linesPerSecond <= 0.0) {
                return 0L;
            }

            long remainingLines = totalLineCount - uploadedLineCount;
            return (long) Math.ceil(remainingLines / linesPerSecond);
        }

        /**
         * Calculate the theoretical maximum throughput based on baud rate.
         * Assumes 115200 baud (14.4 KB/s) with some overhead.
         */
        public double theoreticalMaxBytesPerSecond() {
            // 115200 baud = 115200 bits/second
            // 8 bits per byte = 14400 bytes/second theoretical
            // With protocol overhead, effective is typically 10-12 KB/s
            return 12000.0; // Conservative estimate
        }

        /**
         * Calculate efficiency as percentage of theoretical maximum.
         * Returns 0 if no progress has been made.
         */
        public double efficiencyPercent() {
            double actual = bytesPerSecond();
            double theoretical = theoreticalMaxBytesPerSecond();

            if (actual <= 0.0 || theoretical <= 0.0) {
                return 0.0;
            }

            return Math.min(100.0, (actual * 100.0) / theoretical);
        }

        public static UploadProgress running(
                String printerId,
                String printFileId,
                String originalFilename,
                String requestedTargetFilename,
                long totalLineCount,
                long totalByteCount) {
            Instant now = Instant.now();
            return new UploadProgress(
                    printerId,
                    printFileId,
                    originalFilename,
                    requestedTargetFilename,
                    "running",
                    true,
                    0L,
                    totalLineCount,
                    totalByteCount,
                    0L,
                    now,
                    now,
                    "Upload started.");
        }

        public static UploadProgress progress(
                String printerId,
                String printFileId,
                String originalFilename,
                String requestedTargetFilename,
                long totalLineCount,
                long totalByteCount,
                long uploadedLineCount,
                long rejectedLineCount,
                Instant startedAt) {
            Instant now = Instant.now();
            return new UploadProgress(
                    printerId,
                    printFileId,
                    originalFilename,
                    requestedTargetFilename,
                    "running",
                    true,
                    uploadedLineCount,
                    totalLineCount,
                    totalByteCount,
                    rejectedLineCount,
                    startedAt == null ? now : startedAt,
                    now,
                    "Upload in progress.");
        }

        public static UploadProgress finished(
                String printerId,
                String printFileId,
                String originalFilename,
                String requestedTargetFilename,
                long totalLineCount,
                long totalByteCount,
                long uploadedLineCount,
                long rejectedLineCount,
                String detail) {
            Instant now = Instant.now();
            return new UploadProgress(
                    printerId,
                    printFileId,
                    originalFilename,
                    requestedTargetFilename,
                    "success",
                    false,
                    uploadedLineCount,
                    totalLineCount,
                    totalByteCount,
                    rejectedLineCount,
                    now,
                    now,
                    detail);
        }

        public static UploadProgress failed(
                String printerId,
                String printFileId,
                String originalFilename,
                String requestedTargetFilename,
                long totalLineCount,
                long totalByteCount,
                long uploadedLineCount,
                long rejectedLineCount,
                String detail) {
            Instant now = Instant.now();
            return new UploadProgress(
                    printerId,
                    printFileId,
                    originalFilename,
                    requestedTargetFilename,
                    "error",
                    false,
                    uploadedLineCount,
                    totalLineCount,
                    totalByteCount,
                    rejectedLineCount,
                    now,
                    now,
                    detail);
        }

        private UploadProgress withRejectedLineCount(long newRejectedLineCount) {
            return new UploadProgress(
                    printerId,
                    printFileId,
                    originalFilename,
                    requestedTargetFilename,
                    state,
                    active,
                    uploadedLineCount,
                    totalLineCount,
                    totalByteCount,
                    newRejectedLineCount,
                    startedAt,
                    Instant.now(),
                    detail);
        }
    }

    public record CloseUploadSessionResult(
            String printerId,
            int lineNumber,
            int attempts,
            boolean success,
            String response,
            String detail) {
    }

    private record BufferedWindow(
            int firstLineNumber,
            int lastLineNumber,
            List<String> payloads) {

        private BufferedWindow {
            if (payloads == null || payloads.isEmpty()) {
                throw new IllegalArgumentException("payloads must not be null or empty");
            }
            if (lastLineNumber < firstLineNumber) {
                throw new IllegalArgumentException("lastLineNumber must be >= firstLineNumber");
            }
            if ((lastLineNumber - firstLineNumber + 1) != payloads.size()) {
                throw new IllegalArgumentException("window line range does not match payload count");
            }
        }

        private boolean contains(int lineNumber) {
            return lineNumber >= firstLineNumber && lineNumber <= lastLineNumber;
        }

        private WindowLine findLine(int lineNumber) {
            if (!contains(lineNumber)) {
                return null;
            }

            int index = lineNumber - firstLineNumber;
            return new WindowLine(lineNumber, payloads.get(index));
        }

        private record WindowLine(int lineNumber, String payload) {
        }
    }

    private static final class RecentWindowHistory {
        private final BufferedWindow[] windows;
        private int currentWindowSlot;
        private int newestSentLineNumber;
        private int oldestRecoverableLineNumber;

        private RecentWindowHistory(int batchSize, int multiplier) {
            if (batchSize < 1) {
                throw new IllegalArgumentException("batchSize must be greater than zero");
            }
            if (multiplier < 1) {
                throw new IllegalArgumentException("multiplier must be greater than zero");
            }

            this.windows = new BufferedWindow[multiplier];
            this.currentWindowSlot = 0;
            this.newestSentLineNumber = -1;
            this.oldestRecoverableLineNumber = -1;
        }

        private void storeWindow(List<Integer> lineNumbers, List<String> payloads) {
            if (lineNumbers == null || payloads == null || lineNumbers.isEmpty() || payloads.isEmpty()) {
                throw new IllegalArgumentException("lineNumbers and payloads must not be null or empty");
            }
            if (lineNumbers.size() != payloads.size()) {
                throw new IllegalArgumentException("lineNumbers and payloads must have the same size");
            }

            for (int i = 1; i < lineNumbers.size(); i++) {
                if (lineNumbers.get(i) != lineNumbers.get(i - 1) + 1) {
                    throw new IllegalArgumentException("lineNumbers must be strictly contiguous and ascending");
                }
            }

            int firstLineNumber = lineNumbers.get(0);
            int lastLineNumber = lineNumbers.get(lineNumbers.size() - 1);

            BufferedWindow window = new BufferedWindow(
                    firstLineNumber,
                    lastLineNumber,
                    List.copyOf(payloads));

            windows[currentWindowSlot % windows.length] = window;
            currentWindowSlot++;

            newestSentLineNumber = Math.max(newestSentLineNumber, lastLineNumber);
            oldestRecoverableLineNumber = recomputeOldestRecoverableLineNumber();
        }

        private boolean isRecoverable(int lineNumber) {
            if (lineNumber < 0) {
                return false;
            }
            if (oldestRecoverableLineNumber < 0 || newestSentLineNumber < 0) {
                return false;
            }
            if (lineNumber < oldestRecoverableLineNumber || lineNumber > newestSentLineNumber) {
                return false;
            }
            return findLine(lineNumber) != null;
        }

        private BufferedWindow.WindowLine findLine(int lineNumber) {
            for (BufferedWindow window : windows) {
                if (window != null && window.contains(lineNumber)) {
                    return window.findLine(lineNumber);
                }
            }
            return null;
        }

        private int newestSentLineNumber() {
            return newestSentLineNumber;
        }

        private int oldestRecoverableLineNumber() {
            return oldestRecoverableLineNumber;
        }

        private int recomputeOldestRecoverableLineNumber() {
            int oldest = Integer.MAX_VALUE;

            for (BufferedWindow window : windows) {
                if (window != null) {
                    oldest = Math.min(oldest, window.firstLineNumber());
                }
            }

            return oldest == Integer.MAX_VALUE ? -1 : oldest;
        }
    }

    private static final class SdUploadLineException extends IllegalStateException {
        private final int lineNumber;

        private SdUploadLineException(int lineNumber, String message) {
            super(message);
            this.lineNumber = lineNumber;
        }

        private int lineNumber() {
            return lineNumber;
        }
    }

    private static final class UploadGuardState {
        private Integer lastRequestedResendLine;
        private int consecutiveIdenticalResends;
        private boolean forceSingleSendMode;

        private int registerResend(int resendLine) {
            if (lastRequestedResendLine != null && lastRequestedResendLine == resendLine) {
                consecutiveIdenticalResends++;
            } else {
                lastRequestedResendLine = resendLine;
                consecutiveIdenticalResends = 1;
            }

            return consecutiveIdenticalResends;
        }

        private void enableSingleSendMode() {
            this.forceSingleSendMode = true;
        }

        private boolean forceSingleSendMode() {
            return forceSingleSendMode;
        }
    }

    private void discardPendingUploadInput(PrinterRuntimeNode node) {
        node.printerPort().discardPendingInput(
                SerialDefaults.FILE_STREAMING_QUIET_PERIOD_MS,
                Math.max(250, SerialDefaults.FILE_STREAMING_QUIET_PERIOD_MS * 20));
    }

    private void sleepRecoveryReplayDelay() {
        try {
            Thread.sleep(SerialDefaults.FILE_STREAMING_RECOVERY_REPLAY_DELAY_MS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SD upload recovery replay interrupted", exception);
        }
    }

    private String sendChecksummedLineForRecoveryClose(
            PrinterRuntimeNode node,
            int lineNumber,
            String payload) {
        long startTime = System.nanoTime();
        String checksummedLine = buildChecksummedLine(lineNumber, payload);
        long checksumTime = System.nanoTime();

        String lastResponse = null;

        for (int attempt = 1; attempt <= PrinterProtocolDefaults.SD_UPLOAD_MAX_RETRIES_PER_LINE; attempt++) {
            long sendStartTime = System.nanoTime();
            long sendTimestamp = System.currentTimeMillis();

            logUploadWire(node.id(), "->", checksummedLine + " [recovery-close attempt " + attempt + "]");
            lastResponse = node.printerPort().sendRawLine(checksummedLine, SerialIOMode.FILE_STREAMING);

            long responseTime = System.nanoTime();
            long responseTimestamp = System.currentTimeMillis();

            int sleepCycles = 0;
            if (node.printerPort() instanceof SerialConnection) {
                sleepCycles = ((SerialConnection) node.printerPort()).getLastSleepCycles();
            }

            logUploadWire(node.id(), "<-", lastResponse);

            if (debugWireTracingEnabledSupplier.getAsBoolean()) {
                long checksumMs = (checksumTime - startTime) / 1_000_000;
                long sendWaitMs = (responseTime - sendStartTime) / 1_000_000;
                long wallClockMs = responseTimestamp - sendTimestamp;
                long totalMs = (responseTime - startTime) / 1_000_000;
                System.out.println("[PrinterHub] SD upload timing " + node.id() + " line=" + lineNumber
                        + " recovery-close attempt=" + attempt
                        + " checksum=" + checksumMs + "ms"
                        + " send+wait=" + sendWaitMs + "ms"
                        + " wall=" + wallClockMs + "ms"
                        + " sleeps=" + sleepCycles
                        + " total=" + totalMs + "ms");
            }

            Integer requestedResendLine = requestedResendLine(lastResponse);

            if (requestedResendLine != null) {
                return lastResponse;
            }

            if (responseContainsUploadError(lastResponse)) {
                throw new SdUploadLineException(
                        lineNumber,
                        "Printer reported SD upload error for line "
                                + lineNumber
                                + ": "
                                + OperationMessages.safeDetail(lastResponse, "no response"));
            }

            if (responseContainsOk(lastResponse)) {
                return lastResponse;
            }

            throw new SdUploadLineException(
                    lineNumber,
                    "Unexpected SD upload response for line "
                            + lineNumber
                            + ": "
                            + OperationMessages.safeDetail(lastResponse, "no response"));
        }

        throw new SdUploadLineException(
                lineNumber,
                "SD upload recovery close exceeded retry limit for line "
                        + lineNumber
                        + ": "
                        + OperationMessages.safeDetail(lastResponse, "no response"));
    }

}
