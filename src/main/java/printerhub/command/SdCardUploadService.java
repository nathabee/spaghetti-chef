package printerhub.command;

import printerhub.OperationMessages;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BooleanSupplier;
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
                () -> new MonitoringRulesStore().load().debugWireTracingEnabled()
        );
    }

    SdCardUploadService(
            PrinterRegistry printerRegistry,
            PrinterMonitoringScheduler monitoringScheduler,
            PrinterActionGuard printerActionGuard,
            PrintFileService printFileService,
            SdCardService sdCardService,
            PrinterSdFileService printerSdFileService,
            PrinterEventStore printerEventStore,
            BooleanSupplier debugWireTracingEnabledSupplier) {
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
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("debugWireTracingEnabledSupplier"));
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

        String targetFilename = normalizeTargetFilename(
                requestedTargetFilename,
                printFile.originalFilename());

        String executionToken = "sd-upload:" + printFile.id() + ":" + Instant.now();

        node.beginJobExecution(executionToken);
        monitoringScheduler.stopMonitoring(node.id());

        boolean writeOpened = false;

        try {
            printerEventStore.record(
                    node.id(),
                    null,
                    "SD_CARD_UPLOAD_STARTED",
                    "SD upload started: host file "
                            + printFile.originalFilename()
                            + " -> "
                            + targetFilename);

            node.printerPort().connect();

            sendChecksummedLineWithRetry(node, 0, "M110 N0");

            String startResponse = sendChecksummedLineWithRetry(
                    node,
                    1,
                    "M28 " + targetFilename);

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

            long uploadedLineCount = streamFileLinesWithChecksum(node, hostPath, 2);

            String finishResponse = sendChecksummedLineWithRetry(
                    node,
                    (int) uploadedLineCount + 2,
                    "M29");

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
                    (int) uploadedLineCount + 3);
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

            return new UploadResult(
                    node.id(),
                    printFile.id(),
                    printFile.originalFilename(),
                    targetFilename,
                    linkedFile.firmwarePath(),
                    linkedFile.id(),
                    uploadedLineCount,
                    true,
                    null);
        } catch (Exception exception) {
            if (writeOpened) {
                closeUploadSessionAfterFailure(node, exception);
            }

            String detail = OperationMessages.safeDetail(
                    exception.getMessage(),
                    JobFailureReason.UNKNOWN.name());

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

    private long streamFileLinesWithChecksum(
            PrinterRuntimeNode node,
            Path hostPath,
            int firstLineNumber) {
        long uploadedLineCount = 0;

        try (BufferedReader reader = Files.newBufferedReader(hostPath)) {
            String rawLine;

            while ((rawLine = reader.readLine()) != null) {
                String payload = normalizeUploadPayload(rawLine);
                if (payload == null) {
                    continue;
                }

                uploadedLineCount++;
                sendChecksummedLineWithRetry(
                        node,
                        firstLineNumber + (int) uploadedLineCount - 1,
                        payload);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(OperationMessages.PRINT_FILE_MUST_BE_READABLE, exception);
        }

        return uploadedLineCount;
    }

    private SdCardFileList listFilesWithChecksum(PrinterRuntimeNode node, int lineNumber) {
        String response = sendChecksummedLineWithRetry(
                node,
                lineNumber,
                PrinterProtocolDefaults.COMMAND_LIST_SD_FILES);
        SdCardFileList fileList = new SdCardFileList(node.id(), sdCardFileParser.parse(response), response);

        printerEventStore.record(
                node.id(),
                null,
                OperationMessages.EVENT_SD_CARD_FILES_LISTED,
                OperationMessages.sdCardFilesListed(fileList.files().size()));

        return fileList;
    }

    private String sendChecksummedLineWithRetry(
            PrinterRuntimeNode node,
            int lineNumber,
            String payload) {
        String checksummedLine = buildChecksummedLine(lineNumber, payload);
        String lastResponse = null;

        for (int attempt = 1; attempt <= PrinterProtocolDefaults.SD_UPLOAD_MAX_RETRIES_PER_LINE; attempt++) {
            logUploadWire(node.id(), "->", checksummedLine + " [attempt " + attempt + "]");
            lastResponse = node.printerPort().sendRawLine(checksummedLine);
            logUploadWire(node.id(), "<-", lastResponse);

            if (responseRequestsResend(lastResponse, lineNumber)) {
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
                "SD upload exceeded retry limit for line "
                        + lineNumber
                        + ": "
                        + OperationMessages.safeDetail(lastResponse, "no response"));
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

    private void closeUploadSessionAfterFailure(PrinterRuntimeNode node, Exception exception) {
        int closeLineNumber = exception instanceof SdUploadLineException uploadException
                ? uploadException.lineNumber()
                : 2;

        try {
            sendChecksummedLineWithRetry(node, closeLineNumber, "M29");
        } catch (Exception closeException) {
            System.err.println(OperationMessages.apiOperationFailed(
                    "Failed to close SD upload session after upload failure: "
                            + OperationMessages.safeDetail(closeException.getMessage(), OperationMessages.UNKNOWN_API_ERROR)));
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

    private boolean responseRequestsResend(String response, int expectedLineNumber) {
        if (response == null || response.isBlank()) {
            return false;
        }

        String normalized = response.toLowerCase(Locale.ROOT);

        if (!normalized.contains("resend")) {
            return false;
        }

        Integer requestedLine = extractRequestedResendLine(response);

        if (requestedLine == null) {
            return true;
        }

        return requestedLine == expectedLineNumber;
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
            boolean success,
            String detail) {
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
}
