package printerhub.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import printerhub.OperationMessages;
import printerhub.PrinterSnapshot;
import printerhub.PrinterState;
import printerhub.command.PrinterCommandService;
import printerhub.command.SdCardFile;
import printerhub.command.SdCardFileList;
import printerhub.command.SdCardService;
import printerhub.config.RuntimeDefaults;
import printerhub.job.AsyncPrintJobExecutor;
// import printerhub.job.JobFailureReason;
import printerhub.command.SdCardUploadService;
import printerhub.job.AutonomousPrintControlService;
import printerhub.job.PrintFile;
import printerhub.job.PrintFileService;
import printerhub.job.PrintJobExecutionStep;
import printerhub.job.PrinterSdFile;
import printerhub.job.PrinterActionGuard;
import printerhub.job.PrinterSdFileService;
import printerhub.job.JobState;
import printerhub.persistence.PrintJobExecutionStepStore;
import printerhub.job.JobType;
import printerhub.job.PrintJob;
import printerhub.job.PrintJobService;
import printerhub.job.PrinterResponseClassifier;
import printerhub.monitoring.PrinterMonitoringScheduler;
import printerhub.persistence.MonitoringRules;
import printerhub.persistence.MonitoringRulesStore;
import printerhub.persistence.PrintFileSettings;
import printerhub.persistence.PrintFileSettingsStore;
import printerhub.persistence.PrinterConfigurationStore;
import printerhub.persistence.PrinterEvent;
import printerhub.persistence.PrinterEventStore;
import printerhub.persistence.SerialTransferSettings;
import printerhub.persistence.SerialTransferSettingsStore;
import printerhub.runtime.PrinterRegistry;
import printerhub.runtime.PrinterRuntimeNode;
import printerhub.runtime.PrinterRuntimeNodeFactory;
import printerhub.runtime.PrinterRuntimeStateCache;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RemoteApiServer {

    private final int port;
    private final PrinterRegistry printerRegistry;
    private final PrinterRuntimeStateCache stateCache;
    private final PrinterMonitoringScheduler monitoringScheduler;
    private final PrinterConfigurationStore printerConfigurationStore;
    private final MonitoringRulesStore monitoringRulesStore;
    private final PrintFileSettingsStore printFileSettingsStore;
    private final SerialTransferSettingsStore serialTransferSettingsStore;
    private final PrinterEventStore printerEventStore;
    private final PrinterCommandService printerCommandService;
    private final SdCardService sdCardService;
    private final PrintFileService printFileService;
    private final PrinterSdFileService printerSdFileService;
    private final PrintJobService printJobService;
    private final AsyncPrintJobExecutor asyncPrintJobExecutor;
    private final PrintJobExecutionStepStore printJobExecutionStepStore;
    private final SdCardUploadService sdCardUploadService;
    private final PrinterResponseClassifier printerResponseClassifier;
    private final AutonomousPrintControlService autonomousPrintControlService;

    private HttpServer server;

    public RemoteApiServer(
            int port,
            PrinterRegistry printerRegistry,
            PrinterRuntimeStateCache stateCache,
            PrinterMonitoringScheduler monitoringScheduler,
            PrinterConfigurationStore printerConfigurationStore,
            MonitoringRulesStore monitoringRulesStore,
            PrintFileSettingsStore printFileSettingsStore,
            SerialTransferSettingsStore serialTransferSettingsStore,
            PrinterEventStore printerEventStore,
            PrinterCommandService printerCommandService,
            SdCardService sdCardService,
            SdCardUploadService sdCardUploadService,
            PrintFileService printFileService,
            PrinterSdFileService printerSdFileService,
            PrintJobService printJobService,
            AsyncPrintJobExecutor asyncPrintJobExecutor,
            PrintJobExecutionStepStore printJobExecutionStepStore) {
        if (port < RuntimeDefaults.MIN_PORT || port > RuntimeDefaults.MAX_PORT) {
            throw new IllegalArgumentException(OperationMessages.PORT_MUST_BE_IN_VALID_RANGE);
        }
        if (printerRegistry == null) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_REGISTRY_MUST_NOT_BE_NULL);
        }
        if (stateCache == null) {
            throw new IllegalArgumentException(OperationMessages.STATE_CACHE_MUST_NOT_BE_NULL);
        }
        if (monitoringScheduler == null) {
            throw new IllegalArgumentException(OperationMessages.MONITORING_SCHEDULER_MUST_NOT_BE_NULL);
        }
        if (printerConfigurationStore == null) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_CONFIGURATION_STORE_MUST_NOT_BE_NULL);
        }
        if (monitoringRulesStore == null) {
            throw new IllegalArgumentException(OperationMessages.MONITORING_RULES_STORE_MUST_NOT_BE_NULL);
        }
        if (printFileSettingsStore == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("printFileSettingsStore"));
        }
        if (serialTransferSettingsStore == null) {
            throw new IllegalArgumentException(OperationMessages.SERIAL_TRANSFER_SETTINGS_STORE_MUST_NOT_BE_NULL);
        }
        if (printerEventStore == null) {
            throw new IllegalArgumentException(OperationMessages.EVENT_STORE_MUST_NOT_BE_NULL);
        }
        if (printerCommandService == null) {
            throw new IllegalArgumentException(OperationMessages.COMMAND_SERVICE_MUST_NOT_BE_NULL);
        }
        if (sdCardService == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("sdCardService"));
        }
        if (printFileService == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("printFileService"));
        }
        if (printerSdFileService == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("printerSdFileService"));
        }
        if (printJobService == null) {
            throw new IllegalArgumentException(OperationMessages.PRINT_JOB_SERVICE_MUST_NOT_BE_NULL);
        }
        if (asyncPrintJobExecutor == null) {
            throw new IllegalArgumentException(OperationMessages.PRINT_JOB_EXECUTION_SERVICE_MUST_NOT_BE_NULL);
        }
        if (printJobExecutionStepStore == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("printJobExecutionStepStore"));
        }
        if (sdCardUploadService == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("sdCardUploadService"));
        }

        this.port = port;
        this.printerRegistry = printerRegistry;
        this.stateCache = stateCache;
        this.monitoringScheduler = monitoringScheduler;
        this.printerConfigurationStore = printerConfigurationStore;
        this.monitoringRulesStore = monitoringRulesStore;
        this.printFileSettingsStore = printFileSettingsStore;
        this.serialTransferSettingsStore = serialTransferSettingsStore;
        this.printerEventStore = printerEventStore;
        this.printerCommandService = printerCommandService;
        this.sdCardService = sdCardService;
        this.sdCardUploadService = sdCardUploadService;
        this.printFileService = printFileService;
        this.printerSdFileService = printerSdFileService;
        this.printJobService = printJobService;
        this.asyncPrintJobExecutor = asyncPrintJobExecutor;
        this.printJobExecutionStepStore = printJobExecutionStepStore;
        this.printerResponseClassifier = new PrinterResponseClassifier();
        this.autonomousPrintControlService = new AutonomousPrintControlService(
                printJobService,
                printerRegistry,
                monitoringScheduler,
                printJobExecutionStepStore);
    }

    public void start() {
        if (server != null) {
            return;
        }

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newFixedThreadPool(RuntimeDefaults.DEFAULT_API_THREAD_POOL_SIZE));

            server.createContext("/health", exchange -> safeHandle(exchange, this::handleHealth));
            server.createContext("/printers", exchange -> safeHandle(exchange, this::handlePrinters));
            server.createContext("/print-files", exchange -> safeHandle(exchange, this::handlePrintFiles));
            server.createContext("/printer-sd-files", exchange -> safeHandle(exchange, this::handlePrinterSdFiles));
            server.createContext("/jobs", exchange -> safeHandle(exchange, this::handleJobs));
            server.createContext("/settings/monitoring",
                    exchange -> safeHandle(exchange, this::handleMonitoringSettings));
            server.createContext("/settings/print-files",
                    exchange -> safeHandle(exchange, this::handlePrintFileSettings));
            server.createContext("/settings/serial-transfer",
                    exchange -> safeHandle(exchange, this::handleSerialTransferSettings));
            server.createContext("/dashboard", exchange -> safeHandle(exchange, this::handleDashboard));
            server.start();

            System.out.println(OperationMessages.apiServerStarted(port));
        } catch (IOException exception) {
            throw new IllegalStateException(OperationMessages.failedToStartApiServer(port), exception);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            asyncPrintJobExecutor.close();
            System.out.println(OperationMessages.apiServerStopped());
        }
    }

    private void safeHandle(HttpExchange exchange, ExchangeHandler handler) throws IOException {
        addCorsHeaders(exchange);

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        try {
            handler.handle(exchange);
        } catch (IllegalArgumentException exception) {
            sendJson(exchange, 400, errorJson(safeMessage(exception)));
        } catch (IllegalStateException exception) {
            String message = safeMessage(exception);

            if (OperationMessages.INVALID_JOB_STATE.equals(message)) {
                sendJson(exchange, 400, errorJson(message));
                return;
            }

            if (OperationMessages.JOB_NOT_FOUND.equals(message)
                    || OperationMessages.PRINTER_NOT_FOUND.equals(message)
                    || OperationMessages.PRINTER_SD_FILE_NOT_FOUND.equals(message)) {
                sendJson(exchange, 404, errorJson(message));
                return;
            }

            System.err.println(OperationMessages.apiOperationFailed(message));
            sendJson(exchange, 500, errorJson(message));
        } catch (Exception exception) {
            System.err.println(OperationMessages.unexpectedApiError(safeMessage(exception)));
            sendJson(exchange, 500, errorJson(OperationMessages.INTERNAL_SERVER_ERROR));
        }
    }

    private void addCorsHeaders(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        sendJson(exchange, 200, "{\"status\":\"ok\"}");
    }

    private void handleMonitoringSettings(HttpExchange exchange) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 200, monitoringRulesJson(monitoringRulesStore.load()));
            return;
        }

        if ("PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = readBody(exchange);
            MonitoringRules currentRules = monitoringRulesStore.load();

            MonitoringRules updatedRules = new MonitoringRules(
                    optionalJsonLong(body, "pollIntervalSeconds", currentRules.pollIntervalSeconds()),
                    optionalJsonLong(
                            body,
                            "snapshotMinimumIntervalSeconds",
                            currentRules.snapshotMinimumIntervalSeconds()),
                    optionalJsonDouble(
                            body,
                            "temperatureDeltaThreshold",
                            currentRules.temperatureDeltaThreshold()),
                    optionalJsonLong(
                            body,
                            "eventDeduplicationWindowSeconds",
                            currentRules.eventDeduplicationWindowSeconds()),
                    optionalJsonErrorPersistenceBehavior(
                            body,
                            "errorPersistenceBehavior",
                            currentRules.errorPersistenceBehavior()),
                    optionalJsonBoolean(
                            body,
                            "debugWireTracingEnabled",
                            currentRules.debugWireTracingEnabled()));

            monitoringRulesStore.save(updatedRules);
            monitoringScheduler.updateMonitoringRules(updatedRules);

            sendJson(exchange, 200, monitoringRulesJson(updatedRules));
            return;
        }

        sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
    }

    private void handlePrintFileSettings(HttpExchange exchange) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 200, printFileSettingsJson(printFileSettingsStore.load()));
            return;
        }

        if ("PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = readBody(exchange);
            PrintFileSettings settings = new PrintFileSettings(requiredJsonString(body, "storageDirectory"));
            sendJson(exchange, 200, printFileSettingsJson(printFileSettingsStore.save(settings)));
            return;
        }

        sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
    }

    private void handleSerialTransferSettings(HttpExchange exchange) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 200, serialTransferSettingsJson(serialTransferSettingsStore.load()));
            return;
        }

        if ("PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = readBody(exchange);
            SerialTransferSettings currentSettings = serialTransferSettingsStore.load();

            SerialTransferSettings updatedSettings = new SerialTransferSettings(
                    optionalJsonInteger(body, "sdUploadBatchSize", currentSettings.sdUploadBatchSize()),
                    optionalJsonInteger(body, "sdUploadMinBatchSize", currentSettings.sdUploadMinBatchSize()),
                    optionalJsonInteger(body, "sdUploadBatchUpgradeStep", currentSettings.sdUploadBatchUpgradeStep()),
                    optionalJsonInteger(body, "sdUploadBatchDowngradeStep",
                            currentSettings.sdUploadBatchDowngradeStep()),
                    optionalJsonInteger(
                            body,
                            "sdUploadStableLinesForUpgrade",
                            currentSettings.sdUploadStableLinesForUpgrade()),
                    optionalJsonInteger(
                            body,
                            "sdUploadResendWindowLines",
                            currentSettings.sdUploadResendWindowLines()),
                    optionalJsonInteger(
                            body,
                            "sdUploadResendThresholdForDowngrade",
                            currentSettings.sdUploadResendThresholdForDowngrade()),
                    optionalJsonInteger(
                            body,
                            "sdUploadRecoveryThresholdForMinBatch",
                            currentSettings.sdUploadRecoveryThresholdForMinBatch()),
                    optionalJsonInteger(
                            body,
                            "sdUploadRecoveryWindowMultiplier",
                            currentSettings.sdUploadRecoveryWindowMultiplier()),
                    optionalJsonInteger(body, "sdUploadMaxErrors", currentSettings.sdUploadMaxErrors()),
                    optionalJsonInteger(
                            body,
                            "sdUploadMaxConsecutiveIdenticalResends",
                            currentSettings.sdUploadMaxConsecutiveIdenticalResends()),
                    optionalJsonInteger(
                            body,
                            "sdUploadMinPerformancePercent",
                            currentSettings.sdUploadMinPerformancePercent()),
                    optionalJsonInteger(
                            body,
                            "sdUploadMaxRetriesPerLine",
                            currentSettings.sdUploadMaxRetriesPerLine()),
                    optionalJsonInteger(
                            body,
                            "fileStreamingReadTimeoutMs",
                            currentSettings.fileStreamingReadTimeoutMs()),
                    optionalJsonInteger(
                            body,
                            "fileStreamingQuietPeriodMs",
                            currentSettings.fileStreamingQuietPeriodMs()),
                    optionalJsonInteger(
                            body,
                            "fileStreamingReadActivitySleepMs",
                            currentSettings.fileStreamingReadActivitySleepMs()),
                    optionalJsonInteger(
                            body,
                            "fileStreamingReadIdleSleepMs",
                            currentSettings.fileStreamingReadIdleSleepMs()),
                    optionalJsonInteger(
                            body,
                            "fileStreamingRecoveryReplayDelayMs",
                            currentSettings.fileStreamingRecoveryReplayDelayMs()));

            sendJson(exchange, 200, serialTransferSettingsJson(serialTransferSettingsStore.save(updatedSettings)));
            return;
        }

        sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
    }

    private void handlePrinters(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        if ("/printers".equals(path)) {
            handlePrintersRoot(exchange);
            return;
        }

        if (path.startsWith("/printers/")) {
            handlePrinterById(exchange, path);
            return;
        }

        sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_ENDPOINT_NOT_FOUND));
    }

    private void handleJobs(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        if ("/jobs".equals(path)) {
            handleJobsRoot(exchange);
            return;
        }

        if (path.startsWith("/jobs/")) {
            handleJobById(exchange, path);
            return;
        }

        sendJson(exchange, 404, errorJson(OperationMessages.JOB_ENDPOINT_NOT_FOUND));
    }

    private void handlePrintFiles(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        if ("/print-files".equals(path)) {
            handlePrintFilesRoot(exchange);
            return;
        }

        if ("/print-files/uploads".equals(path)) {
            handlePrintFileUpload(exchange);
            return;
        }

        if (path.startsWith("/print-files/")) {
            String remaining = path.substring("/print-files/".length());

            if (remaining.endsWith("/content")) {
                String printFileId = remaining.substring(0, remaining.length() - "/content".length());

                if (printFileId.isBlank()) {
                    sendJson(exchange, 404, errorJson(OperationMessages.PRINT_FILE_NOT_FOUND));
                    return;
                }

                handlePrintFileContent(exchange, printFileId);
                return;
            }

            String printFileId = remaining;

            if (printFileId.isBlank()) {
                sendJson(exchange, 404, errorJson(OperationMessages.PRINT_FILE_NOT_FOUND));
                return;
            }

            handlePrintFileResource(exchange, printFileId);
            return;
        }

        sendJson(exchange, 404, errorJson(OperationMessages.PRINT_FILE_NOT_FOUND));
    }

    private void handlePrinterSdFiles(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        if ("/printer-sd-files".equals(path)) {
            handlePrinterSdFilesRoot(exchange);
            return;
        }

        if (path.startsWith("/printer-sd-files/")) {
            handlePrinterSdFileById(exchange, path);
            return;
        }

        sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_SD_FILE_NOT_FOUND));
    }

    private void handlePrinterSdFilesRoot(HttpExchange exchange) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            String printerId = queryParameter(exchange.getRequestURI().getRawQuery(), "printerId");
            List<PrinterSdFile> files = printerId == null || printerId.isBlank()
                    ? printerSdFileService.findAll()
                    : printerSdFileService.findByPrinterId(printerId);
            sendJson(exchange, 200, printerSdFilesJson(files));
            return;
        }

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = readBody(exchange);
            PrinterSdFile file = printerSdFileService.register(
                    requiredJsonString(body, "printerId"),
                    requiredJsonString(body, "firmwarePath"),
                    optionalJsonString(body, "displayName", null),
                    optionalJsonLongObject(body, "sizeBytes"),
                    optionalJsonString(body, "rawLine", null),
                    optionalJsonString(body, "printFileId", null));
            sendJson(exchange, 201, printerSdFileJson(file));
            return;
        }

        sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
    }

    private void handlePrinterSdFileById(HttpExchange exchange, String path) throws IOException {
        String remaining = path.substring("/printer-sd-files/".length());

        if (remaining.isBlank()) {
            sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_SD_FILE_NOT_FOUND));
            return;
        }

        String[] parts = remaining.split("/");
        String printerSdFileId = parts[0];

        if (parts.length == 1 && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            PrinterSdFile file = printerSdFileService.findById(printerSdFileId).orElse(null);

            if (file == null) {
                sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_SD_FILE_NOT_FOUND));
                return;
            }

            sendJson(exchange, 200, printerSdFileJson(file));
            return;
        }

        if (parts.length == 2
                && ("enable".equals(parts[1]) || "disable".equals(parts[1]))
                && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            PrinterSdFile file = printerSdFileService.setEnabled(printerSdFileId, "enable".equals(parts[1]));
            sendJson(exchange, 200, printerSdFileJson(file));
            return;
        }

        if (parts.length == 1 && "DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
            PrinterSdFile file = printerSdFileService.findById(printerSdFileId).orElse(null);

            if (file == null) {
                sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_SD_FILE_NOT_FOUND));
                return;
            }

            if (!file.deleted()) {
                PrinterRuntimeNode node = printerRegistry.findById(file.printerId()).orElse(null);
                if (node == null) {
                    sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_NOT_FOUND));
                    return;
                }

                PrinterActionGuard.GuardDecision decision = new PrinterActionGuard().validateForSdUpload(node);
                if (!decision.allowed()) {
                    sendJson(exchange, 400, errorJson(OperationMessages.safeDetail(
                            decision.detail(),
                            decision.failureReason() == null
                                    ? OperationMessages.PRECONDITION_FAILED
                                    : decision.failureReason().name())));
                    return;
                }

                String response = sdCardService.deleteFile(node, file.firmwarePath());
                PrinterResponseClassifier.ResponseClassification classification = printerResponseClassifier
                        .classifyResponse("M30 " + file.firmwarePath(), response);
                if (!classification.success()) {
                    sendJson(exchange, 400, errorJson(OperationMessages.safeDetail(
                            classification.detail(),
                            OperationMessages.UNKNOWN_API_ERROR)));
                    return;
                }
            }

            PrinterSdFile deletedFile = printerSdFileService.markDeleted(printerSdFileId);
            sendJson(exchange, 200, printerSdFileJson(deletedFile));
            return;
        }

        sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_SD_FILE_NOT_FOUND));
    }

    private void handlePrintFileUpload(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        String filename = queryParameter(exchange.getRequestURI().getRawQuery(), "filename");
        PrintFile printFile = printFileService.storeUploadedFile(filename, readBodyBytes(exchange));
        sendJson(exchange, 201, printFileJson(printFile));
    }

    private void handlePrintFilesRoot(HttpExchange exchange) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 200, printFilesJson(printFileService.findAll()));
            return;
        }

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = readBody(exchange);
            PrintFile printFile = printFileService.registerHostFile(requiredJsonString(body, "path"));
            sendJson(exchange, 201, printFileJson(printFile));
            return;
        }

        sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
    }

    private void handlePrintFileResource(HttpExchange exchange, String printFileId) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        PrintFile printFile = printFileService.findById(printFileId).orElse(null);

        if (printFile == null) {
            sendJson(exchange, 404, errorJson(OperationMessages.PRINT_FILE_NOT_FOUND));
            return;
        }

        sendJson(exchange, 200, printFileJson(printFile));
    }

    private void handlePrintFileContent(HttpExchange exchange, String printFileId) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        PrintFile printFile = printFileService.findById(printFileId).orElse(null);

        if (printFile == null) {
            sendJson(exchange, 404, errorJson(OperationMessages.PRINT_FILE_NOT_FOUND));
            return;
        }

        sendJson(exchange, 200, "{\"printFile\":" + printFileJson(printFile)
                + ",\"content\":\"" + escapeJson(printFileService.readContent(printFileId)) + "\"}");
    }

    private void handleJobsRoot(HttpExchange exchange) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            List<PrintJob> jobs = printJobService.findRecent(RuntimeDefaults.DEFAULT_RECENT_JOB_LIMIT);
            sendJson(exchange, 200, printJobsJson(jobs));
            return;
        }

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = readBody(exchange);

            JobType type = parseJobType(requiredJsonString(body, "type"));
            String printerId = optionalJsonString(body, "printerId", null);
            String printFileId = optionalJsonString(body, "printFileId", null);
            String printerSdFileId = optionalJsonString(body, "printerSdFileId", null);
            Double targetTemperature = optionalJsonDoubleObject(body, "targetTemperature");
            Integer fanSpeed = optionalJsonIntegerObject(body, "fanSpeed");
            PrintFile printFile = null;
            PrinterSdFile printerSdFile = null;

            if (type == JobType.PRINT_FILE) {
                if (printerSdFileId == null || printerSdFileId.isBlank()) {
                    sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_SD_FILE_NOT_FOUND));
                    return;
                }

                printerSdFile = printerSdFileService.findById(printerSdFileId).orElse(null);

                if (printerSdFile == null) {
                    sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_SD_FILE_NOT_FOUND));
                    return;
                }

                if (printerSdFile.deleted()) {
                    sendJson(exchange, 400, errorJson(OperationMessages.PRINTER_SD_FILE_DELETED));
                    return;
                }

                if (!printerSdFile.enabled()) {
                    sendJson(exchange, 400, errorJson(OperationMessages.PRINTER_SD_FILE_DISABLED));
                    return;
                }

                if (printerId == null || printerId.isBlank()) {
                    printerId = printerSdFile.printerId();
                } else if (!printerId.equals(printerSdFile.printerId())) {
                    sendJson(exchange, 400, errorJson(OperationMessages.PRINTER_SD_FILE_NOT_FOUND));
                    return;
                }

                printFileId = printerSdFile.printFileId();
                if (printFileId != null) {
                    printFile = printFileService.findById(printFileId).orElse(null);
                }
            }

            PrintJob job = printJobService.create(
                    resolveJobName(optionalJsonString(body, "name", null), type, printFile, printerSdFile),
                    type,
                    printerId,
                    printFileId,
                    printerSdFileId,
                    targetTemperature,
                    fanSpeed);

            sendJson(exchange, 201, printJobJson(job));
            return;
        }

        sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
    }

    private void handleJobById(HttpExchange exchange, String path) throws IOException {
        String remaining = path.substring("/jobs/".length());

        if (remaining.isBlank()) {
            sendJson(exchange, 404, errorJson(OperationMessages.JOB_NOT_FOUND));
            return;
        }

        String[] parts = remaining.split("/");
        String jobId = parts[0];

        if (parts.length == 1) {
            handleJobResource(exchange, jobId);
            return;
        }

        if (parts.length == 2 && "start".equals(parts[1])) {
            handleJobStart(exchange, jobId);
            return;
        }

        if (parts.length == 2 && "pause".equals(parts[1])) {
            handleJobPause(exchange, jobId);
            return;
        }

        if (parts.length == 2 && "resume".equals(parts[1])) {
            handleJobResume(exchange, jobId);
            return;
        }

        if (parts.length == 2 && "cancel".equals(parts[1])) {
            handleJobCancel(exchange, jobId);
            return;
        }

        if (parts.length == 2 && "restart".equals(parts[1])) {
            handleJobRestart(exchange, jobId);
            return;
        }

        if (parts.length == 2 && "events".equals(parts[1])) {
            handleJobEvents(exchange, jobId);
            return;
        }

        if (parts.length == 2 && "execution-steps".equals(parts[1])) {
            handleJobExecutionSteps(exchange, jobId);
            return;
        }

        sendJson(exchange, 404, errorJson(OperationMessages.JOB_ENDPOINT_NOT_FOUND));
    }

    private void handleJobExecutionSteps(HttpExchange exchange, String jobId) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        PrintJob job = printJobService.findById(jobId).orElse(null);

        if (job == null) {
            sendJson(exchange, 404, errorJson(OperationMessages.JOB_NOT_FOUND));
            return;
        }

        List<PrintJobExecutionStep> steps = printJobExecutionStepStore.findByJobId(jobId);
        sendJson(exchange, 200, printJobExecutionStepsJson(steps));
    }

    private void handleJobEvents(HttpExchange exchange, String jobId) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        PrintJob job = printJobService.findById(jobId).orElse(null);

        if (job == null) {
            sendJson(exchange, 404, errorJson(OperationMessages.JOB_NOT_FOUND));
            return;
        }

        List<PrinterEvent> events = printerEventStore.findRecentByJobId(
                jobId,
                RuntimeDefaults.DEFAULT_RECENT_JOB_LIMIT);

        sendJson(exchange, 200, printerEventsJson(events));
    }

    private void handleJobResource(HttpExchange exchange, String jobId) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            PrintJob job = printJobService.findById(jobId).orElse(null);

            if (job == null) {
                sendJson(exchange, 404, errorJson(OperationMessages.JOB_NOT_FOUND));
                return;
            }

            sendJson(exchange, 200, printJobJson(job));
            return;
        }

        if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
            PrintJob job = printJobService.findById(jobId).orElse(null);

            if (job == null) {
                sendJson(exchange, 404, errorJson(OperationMessages.JOB_NOT_FOUND));
                return;
            }

            printJobService.delete(jobId);
            sendJson(exchange, 200, "{\"deleted\":\"" + escapeJson(jobId) + "\"}");
            return;
        }

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }
    }

    private void handleJobStart(HttpExchange exchange, String jobId) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        AsyncPrintJobExecutor.StartResult result = asyncPrintJobExecutor.start(jobId);
        sendJson(exchange, 200, jobStartJson(result));
    }

    private void handleJobCancel(HttpExchange exchange, String jobId) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        PrintJob currentJob = printJobService.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException(OperationMessages.JOB_NOT_FOUND));
        if (currentJob.state() == JobState.COMPLETED
                || currentJob.state() == JobState.FAILED
                || currentJob.state() == JobState.CANCELLED) {
            sendJson(exchange, 400, errorJson(OperationMessages.INVALID_JOB_STATE));
            return;
        }

        PrintJob job;

        if (currentJob.type() == JobType.PRINT_FILE
                && (currentJob.state() == JobState.RUNNING || currentJob.state() == JobState.PAUSED)) {
            job = autonomousPrintControlService.cancel(jobId).job();
        } else {
            job = printJobService.cancel(jobId);
        }

        sendJson(exchange, 200, printJobJson(job));
    }

    private void handleJobPause(HttpExchange exchange, String jobId) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        AutonomousPrintControlService.ControlResult result = autonomousPrintControlService.pause(jobId);
        sendJson(exchange, 200, controlResultJson(result));
    }

    private void handleJobResume(HttpExchange exchange, String jobId) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        AutonomousPrintControlService.ControlResult result = autonomousPrintControlService.resume(jobId);
        sendJson(exchange, 200, controlResultJson(result));
    }

    private void handleJobRestart(HttpExchange exchange, String jobId) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        PrintJob sourceJob = printJobService.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException(OperationMessages.JOB_NOT_FOUND));

        if (sourceJob.type() != JobType.PRINT_FILE
                || (sourceJob.state() != JobState.COMPLETED
                        && sourceJob.state() != JobState.FAILED
                        && sourceJob.state() != JobState.CANCELLED)) {
            sendJson(exchange, 400, errorJson(OperationMessages.INVALID_JOB_STATE));
            return;
        }

        String printerSdFileId = sourceJob.printerSdFileId();
        if (printerSdFileId == null || printerSdFileId.isBlank()) {
            sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_SD_FILE_NOT_FOUND));
            return;
        }

        PrinterSdFile printerSdFile = printerSdFileService.findById(printerSdFileId).orElse(null);
        if (printerSdFile == null) {
            sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_SD_FILE_NOT_FOUND));
            return;
        }
        if (printerSdFile.deleted()) {
            sendJson(exchange, 400, errorJson(OperationMessages.PRINTER_SD_FILE_DELETED));
            return;
        }
        if (!printerSdFile.enabled()) {
            sendJson(exchange, 400, errorJson(OperationMessages.PRINTER_SD_FILE_DISABLED));
            return;
        }

        PrintJob restarted = printJobService.create(
                "Restart of " + sourceJob.name(),
                JobType.PRINT_FILE,
                sourceJob.printerId(),
                printerSdFile.printFileId(),
                printerSdFile.id(),
                sourceJob.targetTemperature(),
                sourceJob.fanSpeed());

        printJobService.recordJobAuditEvent(
                sourceJob.id(),
                OperationMessages.EVENT_JOB_RESTARTED,
                "Job restart requested: " + restarted.id());
        printJobService.recordJobAuditEvent(
                restarted.id(),
                OperationMessages.EVENT_JOB_RESTARTED,
                "Job restarted from source job: " + sourceJob.id());

        sendJson(exchange, 201, "{\"sourceJobId\":\"" + escapeJson(sourceJob.id())
                + "\",\"job\":" + printJobJson(restarted) + "}");
    }

    private void handlePrintersRoot(HttpExchange exchange) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 200, printersJson());
            return;
        }

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = readBody(exchange);

            PrinterRuntimeNode node = PrinterRuntimeNodeFactory.create(
                    requiredJsonString(body, "id"),
                    requiredJsonString(body, "displayName"),
                    requiredJsonString(body, "portName"),
                    requiredJsonString(body, "mode"),
                    optionalJsonBoolean(body, "enabled", true));

            boolean registered = false;

            try {
                printerRegistry.register(node);
                registered = true;

                printerConfigurationStore.save(node);
                monitoringScheduler.startMonitoring(node);

                sendJson(exchange, 201, printerJson(node));
            } catch (Exception exception) {
                if (registered) {
                    rollbackRegister(node.id());
                }
                throw exception;
            }

            return;
        }

        sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
    }

    private void handlePrinterById(HttpExchange exchange, String path) throws IOException {
        String remaining = path.substring("/printers/".length());

        if (remaining.isBlank()) {
            sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_NOT_FOUND));
            return;
        }

        String[] parts = remaining.split("/");
        String printerId = parts[0];

        if (parts.length == 1) {
            handlePrinterResource(exchange, printerId);
            return;
        }

        if (parts.length == 2 && "status".equals(parts[1])) {
            handlePrinterStatus(exchange, printerId);
            return;
        }

        if (parts.length == 2 && "enable".equals(parts[1])) {
            handlePrinterEnable(exchange, printerId);
            return;
        }

        if (parts.length == 2 && "disable".equals(parts[1])) {
            handlePrinterDisable(exchange, printerId);
            return;
        }

        if (parts.length == 2 && "commands".equals(parts[1])) {
            handlePrinterCommands(exchange, printerId);
            return;
        }

        if (parts.length == 3 && "sd-card".equals(parts[1]) && "files".equals(parts[2])) {
            handlePrinterSdCardFiles(exchange, printerId);
            return;
        }

        if (parts.length == 3 && "sd-card".equals(parts[1]) && "uploads".equals(parts[2])) {
            handlePrinterSdCardUploads(exchange, printerId);
            return;
        }

        if (parts.length == 4
                && "sd-card".equals(parts[1])
                && "uploads".equals(parts[2])
                && "status".equals(parts[3])) {
            handlePrinterSdCardUploadStatus(exchange, printerId);
            return;
        }

        if (parts.length == 4
                && "sd-card".equals(parts[1])
                && "recovery".equals(parts[2])
                && "close-upload".equals(parts[3])) {
            handlePrinterSdCardCloseUploadRecovery(exchange, printerId);
            return;
        }

        if (parts.length == 2 && "events".equals(parts[1])) {
            handlePrinterEvents(exchange, printerId);
            return;
        }

        sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_ENDPOINT_NOT_FOUND));
    }

    private void handlePrinterResource(HttpExchange exchange, String printerId) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            Optional<PrinterRuntimeNode> node = printerRegistry.findById(printerId);

            if (node.isEmpty()) {
                sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_NOT_FOUND));
                return;
            }

            sendJson(exchange, 200, printerJson(node.get()));
            return;
        }

        if ("PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = readBody(exchange);

            PrinterRuntimeNode oldNode = printerRegistry.findById(printerId).orElse(null);

            if (oldNode == null) {
                sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_NOT_FOUND));
                return;
            }

            boolean enabled = optionalJsonBoolean(body, "enabled", oldNode.enabled());

            PrinterRuntimeNode newNode = PrinterRuntimeNodeFactory.create(
                    printerId,
                    requiredJsonString(body, "displayName"),
                    requiredJsonString(body, "portName"),
                    requiredJsonString(body, "mode"),
                    enabled);

            monitoringScheduler.stopMonitoring(printerId);
            printerRegistry.remove(printerId);

            boolean restored = false;

            try {
                printerConfigurationStore.save(newNode);
                printerRegistry.register(newNode);
                monitoringScheduler.startMonitoring(newNode);

                sendJson(exchange, 200, printerJson(newNode));
            } catch (Exception exception) {
                try {
                    printerRegistry.register(oldNode);
                    monitoringScheduler.startMonitoring(oldNode);
                    restored = true;
                } catch (Exception rollbackException) {
                    System.err.println(OperationMessages.failedToRestorePrinterAfterPut(
                            printerId,
                            safeMessage(rollbackException)));
                }

                if (!restored) {
                    stateCache.update(
                            printerId,
                            PrinterSnapshot.error(
                                    PrinterState.ERROR,
                                    null,
                                    null,
                                    null,
                                    OperationMessages.printerUpdateRestoreFailed(printerId),
                                    Instant.now()));
                }

                throw exception;
            }

            return;
        }

        if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
            PrinterRuntimeNode existingNode = printerRegistry.findById(printerId).orElse(null);

            if (existingNode == null) {
                sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_NOT_FOUND));
                return;
            }

            monitoringScheduler.stopMonitoring(printerId);
            printerRegistry.remove(printerId);
            stateCache.remove(printerId);

            try {
                printerConfigurationStore.delete(printerId);
                existingNode.close();

                sendJson(exchange, 200, "{\"deleted\":\"" + escapeJson(printerId) + "\"}");
            } catch (Exception exception) {
                try {
                    printerRegistry.register(existingNode);
                    monitoringScheduler.startMonitoring(existingNode);
                } catch (Exception rollbackException) {
                    System.err.println(OperationMessages.failedToRestorePrinterAfterDelete(
                            printerId,
                            safeMessage(rollbackException)));
                }
                throw exception;
            }

            return;
        }

        sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
    }

    private void handlePrinterStatus(HttpExchange exchange, String printerId) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        PrinterRuntimeNode node = printerRegistry.findById(printerId).orElse(null);

        if (node == null) {
            sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_NOT_FOUND));
            return;
        }

        PrinterSnapshot snapshot = stateCache.findByPrinterId(printerId).orElse(null);
        sendJson(exchange, 200, snapshotJson(snapshot));
    }

    private void handlePrinterEnable(HttpExchange exchange, String printerId) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        PrinterRuntimeNode node = printerRegistry.findById(printerId).orElse(null);

        if (node == null) {
            sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_NOT_FOUND));
            return;
        }

        boolean previousEnabled = node.enabled();

        try {
            printerConfigurationStore.enable(printerId);
            node.enable();
            monitoringScheduler.startMonitoring(node);

            sendJson(exchange, 200, printerJson(node));
        } catch (Exception exception) {
            if (!previousEnabled) {
                node.disable();
                monitoringScheduler.startMonitoring(node);
            }
            throw exception;
        }
    }

    private void handlePrinterDisable(HttpExchange exchange, String printerId) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        PrinterRuntimeNode node = printerRegistry.findById(printerId).orElse(null);

        if (node == null) {
            sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_NOT_FOUND));
            return;
        }

        boolean previousEnabled = node.enabled();

        try {
            printerConfigurationStore.disable(printerId);
            node.disable();
            monitoringScheduler.stopMonitoring(printerId);
            node.close();
            monitoringScheduler.startMonitoring(node);

            sendJson(exchange, 200, printerJson(node));
        } catch (Exception exception) {
            if (previousEnabled) {
                node.enable();
                monitoringScheduler.startMonitoring(node);
            }
            throw exception;
        }
    }

    private void handlePrinterCommands(HttpExchange exchange, String printerId) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        PrinterRuntimeNode node = printerRegistry.findById(printerId).orElse(null);

        if (node == null) {
            sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_NOT_FOUND));
            return;
        }
        if (node.executionInProgress()) {
            sendJson(exchange, 409, errorJson(OperationMessages.PRINTER_BUSY));
            return;
        }

        String body = readBody(exchange);
        String command = requiredJsonString(body, "command");
        Double targetTemperature = optionalJsonDoubleObject(body, "targetTemperature");

        PrinterCommandService.CommandExecutionResult result = printerCommandService.execute(node, command,
                targetTemperature);

        sendJson(exchange, 200, commandExecutionJson(result));
    }

    private void handlePrinterSdCardUploads(HttpExchange exchange, String printerId) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        PrinterRuntimeNode node = printerRegistry.findById(printerId).orElse(null);

        if (node == null) {
            sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_NOT_FOUND));
            return;
        }

        String body = readBody(exchange);

        SdCardUploadService.UploadResult result = sdCardUploadService.uploadToPrinterSd(
                printerId,
                requiredJsonString(body, "printFileId"),
                optionalJsonString(body, "targetFilename", null));

        sendJson(exchange, 200, sdCardUploadResultJson(result));
    }

    private void handlePrinterSdCardUploadStatus(HttpExchange exchange, String printerId) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        PrinterRuntimeNode node = printerRegistry.findById(printerId).orElse(null);

        if (node == null) {
            sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_NOT_FOUND));
            return;
        }

        sendJson(exchange, 200, sdCardUploadProgressJson(
                sdCardUploadService.uploadProgress(printerId).orElse(null)));
    }

    private void handlePrinterSdCardCloseUploadRecovery(HttpExchange exchange, String printerId) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        PrinterRuntimeNode node = printerRegistry.findById(printerId).orElse(null);

        if (node == null) {
            sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_NOT_FOUND));
            return;
        }

        SdCardUploadService.CloseUploadSessionResult result = sdCardUploadService.closeOpenUploadSession(printerId);
        sendJson(exchange, 200, closeUploadSessionResultJson(result));
    }

    private void handlePrinterSdCardFiles(HttpExchange exchange, String printerId) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        PrinterRuntimeNode node = printerRegistry.findById(printerId).orElse(null);

        if (node == null) {
            sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_NOT_FOUND));
            return;
        }
        if (node.executionInProgress()) {
            sendJson(exchange, 409, errorJson(OperationMessages.PRINTER_BUSY));
            return;
        }

        SdCardFileList fileList = sdCardService.listFiles(node);
        for (SdCardFile file : fileList.files()) {
            printerSdFileService.registerListedFile(printerId, file);
        }
        sendJson(exchange, 200, sdCardFileListJson(fileList));
    }

    private void handlePrinterEvents(HttpExchange exchange, String printerId) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        PrinterRuntimeNode node = printerRegistry.findById(printerId).orElse(null);

        if (node == null) {
            sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_NOT_FOUND));
            return;
        }

        List<PrinterEvent> events = printerEventStore.findRecentByPrinterId(
                printerId,
                RuntimeDefaults.DEFAULT_RECENT_SNAPSHOT_LIMIT);

        sendJson(exchange, 200, printerEventsJson(events));
    }

    private String monitoringRulesJson(MonitoringRules monitoringRules) {
        return "{"
                + "\"pollIntervalSeconds\":" + monitoringRules.pollIntervalSeconds() + ","
                + "\"snapshotMinimumIntervalSeconds\":" + monitoringRules.snapshotMinimumIntervalSeconds() + ","
                + "\"temperatureDeltaThreshold\":" + formatDouble(monitoringRules.temperatureDeltaThreshold()) + ","
                + "\"eventDeduplicationWindowSeconds\":" + monitoringRules.eventDeduplicationWindowSeconds() + ","
                + "\"errorPersistenceBehavior\":\"" + escapeJson(monitoringRules.errorPersistenceBehavior().name())
                + "\","
                + "\"debugWireTracingEnabled\":" + monitoringRules.debugWireTracingEnabled()
                + "}";
    }

    private String printFileSettingsJson(PrintFileSettings settings) {
        return "{"
                + "\"storageDirectory\":\"" + escapeJson(settings.storageDirectory()) + "\""
                + "}";
    }

    private String serialTransferSettingsJson(SerialTransferSettings settings) {
        return "{"
                + "\"sdUploadBatchSize\":" + settings.sdUploadBatchSize() + ","
                + "\"sdUploadMinBatchSize\":" + settings.sdUploadMinBatchSize() + ","
                + "\"sdUploadBatchUpgradeStep\":" + settings.sdUploadBatchUpgradeStep() + ","
                + "\"sdUploadBatchDowngradeStep\":" + settings.sdUploadBatchDowngradeStep() + ","
                + "\"sdUploadStableLinesForUpgrade\":" + settings.sdUploadStableLinesForUpgrade() + ","
                + "\"sdUploadResendWindowLines\":" + settings.sdUploadResendWindowLines() + ","
                + "\"sdUploadResendThresholdForDowngrade\":" + settings.sdUploadResendThresholdForDowngrade() + ","
                + "\"sdUploadRecoveryThresholdForMinBatch\":" + settings.sdUploadRecoveryThresholdForMinBatch() + ","
                + "\"sdUploadRecoveryWindowMultiplier\":" + settings.sdUploadRecoveryWindowMultiplier() + ","
                + "\"sdUploadMaxErrors\":" + settings.sdUploadMaxErrors() + ","
                + "\"sdUploadMaxConsecutiveIdenticalResends\":"
                + settings.sdUploadMaxConsecutiveIdenticalResends() + ","
                + "\"sdUploadMinPerformancePercent\":" + settings.sdUploadMinPerformancePercent() + ","
                + "\"sdUploadMaxRetriesPerLine\":" + settings.sdUploadMaxRetriesPerLine() + ","
                + "\"fileStreamingReadTimeoutMs\":" + settings.fileStreamingReadTimeoutMs() + ","
                + "\"fileStreamingQuietPeriodMs\":" + settings.fileStreamingQuietPeriodMs() + ","
                + "\"fileStreamingReadActivitySleepMs\":" + settings.fileStreamingReadActivitySleepMs() + ","
                + "\"fileStreamingReadIdleSleepMs\":" + settings.fileStreamingReadIdleSleepMs() + ","
                + "\"fileStreamingRecoveryReplayDelayMs\":" + settings.fileStreamingRecoveryReplayDelayMs()
                + "}";
    }

    private String commandExecutionJson(PrinterCommandService.CommandExecutionResult result) {
        return "{"
                + "\"printerId\":\"" + escapeJson(result.printerId()) + "\","
                + "\"command\":\"" + escapeJson(result.command()) + "\","
                + "\"sentCommand\":\"" + escapeJson(result.sentCommand()) + "\","
                + "\"response\":" + nullableString(result.response())
                + "}";
    }

    private String sdCardFileListJson(SdCardFileList fileList) {
        StringBuilder json = new StringBuilder();
        json.append("{")
                .append("\"printerId\":\"").append(escapeJson(fileList.printerId())).append("\",")
                .append("\"files\":[");

        boolean first = true;

        for (SdCardFile file : fileList.files()) {
            if (!first) {
                json.append(",");
            }

            json.append("{")
                    .append("\"filename\":\"").append(escapeJson(file.filename())).append("\",")
                    .append("\"sizeBytes\":").append(nullableLong(file.sizeBytes())).append(",")
                    .append("\"rawLine\":\"").append(escapeJson(file.rawLine())).append("\"")
                    .append("}");
            first = false;
        }

        json.append("],")
                .append("\"rawResponse\":\"").append(escapeJson(fileList.rawResponse())).append("\"")
                .append("}");
        return json.toString();
    }

    private String printJobsJson(List<PrintJob> jobs) {
        StringBuilder json = new StringBuilder();
        json.append("{\"jobs\":[");

        boolean first = true;

        for (PrintJob job : jobs) {
            if (!first) {
                json.append(",");
            }
            json.append(printJobJson(job));
            first = false;
        }

        json.append("]}");
        return json.toString();
    }

    private String printFilesJson(List<PrintFile> printFiles) {
        StringBuilder json = new StringBuilder();
        json.append("{\"printFiles\":[");

        boolean first = true;

        for (PrintFile printFile : printFiles) {
            if (!first) {
                json.append(",");
            }
            json.append(printFileJson(printFile));
            first = false;
        }

        json.append("]}");
        return json.toString();
    }

    private String printerSdFilesJson(List<PrinterSdFile> files) {
        StringBuilder json = new StringBuilder();
        json.append("{\"printerSdFiles\":[");

        boolean first = true;

        for (PrinterSdFile file : files) {
            if (!first) {
                json.append(",");
            }
            json.append(printerSdFileJson(file));
            first = false;
        }

        json.append("]}");
        return json.toString();
    }

    private String printerSdFileJson(PrinterSdFile file) {
        return "{"
                + "\"id\":\"" + escapeJson(file.id()) + "\","
                + "\"printerId\":\"" + escapeJson(file.printerId()) + "\","
                + "\"firmwarePath\":\"" + escapeJson(file.firmwarePath()) + "\","
                + "\"displayName\":\"" + escapeJson(file.displayName()) + "\","
                + "\"sizeBytes\":" + nullableLong(file.sizeBytes()) + ","
                + "\"rawLine\":\"" + escapeJson(file.rawLine()) + "\","
                + "\"printFileId\":" + nullableString(file.printFileId()) + ","
                + "\"enabled\":" + file.enabled() + ","
                + "\"deleted\":" + file.deleted() + ","
                + "\"deletedAt\":" + nullableString(file.deletedAt() == null ? null : file.deletedAt().toString()) + ","
                + "\"createdAt\":\"" + escapeJson(file.createdAt().toString()) + "\","
                + "\"updatedAt\":\"" + escapeJson(file.updatedAt().toString()) + "\""
                + "}";
    }

    private String printFileJson(PrintFile printFile) {
        return "{"
                + "\"id\":\"" + escapeJson(printFile.id()) + "\","
                + "\"originalFilename\":\"" + escapeJson(printFile.originalFilename()) + "\","
                + "\"path\":\"" + escapeJson(printFile.path()) + "\","
                + "\"sizeBytes\":" + printFile.sizeBytes() + ","
                + "\"mediaType\":\"" + escapeJson(printFile.mediaType()) + "\","
                + "\"createdAt\":\"" + escapeJson(printFile.createdAt().toString()) + "\""
                + "}";
    }

    private String printJobJson(PrintJob job) {
        return "{"
                + "\"id\":\"" + escapeJson(job.id()) + "\","
                + "\"name\":\"" + escapeJson(job.name()) + "\","
                + "\"type\":\"" + escapeJson(job.type().name()) + "\","
                + "\"state\":\"" + escapeJson(job.state().name()) + "\","
                + "\"printerId\":" + nullableString(job.printerId()) + ","
                + "\"printFileId\":" + nullableString(job.printFileId()) + ","
                + "\"printerSdFileId\":" + nullableString(job.printerSdFileId()) + ","
                + "\"targetTemperature\":" + nullableNumber(job.targetTemperature()) + ","
                + "\"fanSpeed\":" + nullableInteger(job.fanSpeed()) + ","
                + "\"failureReason\":" + nullableString(job.failureReason()) + ","
                + "\"failureDetail\":" + nullableString(job.failureDetail()) + ","
                + "\"createdAt\":\"" + escapeJson(job.createdAt().toString()) + "\","
                + "\"updatedAt\":\"" + escapeJson(job.updatedAt().toString()) + "\","
                + "\"startedAt\":" + nullableString(job.startedAt() == null ? null : job.startedAt().toString()) + ","
                + "\"finishedAt\":" + nullableString(job.finishedAt() == null ? null : job.finishedAt().toString())
                + "}";
    }

    private String jobStartJson(AsyncPrintJobExecutor.StartResult result) {
        return "{"
                + "\"job\":" + printJobJson(result.job()) + ","
                + "\"execution\":{"
                + "\"accepted\":" + result.accepted() + ","
                + "\"success\":" + result.accepted() + ","
                + "\"wireCommand\":null,"
                + "\"response\":null,"
                + "\"outcome\":" + nullableString(result.accepted() ? "QUEUED" : "REJECTED") + ","
                + "\"failureReason\":" + nullableString(
                        result.failureReason() == null ? null : result.failureReason().name())
                + ","
                + "\"failureDetail\":" + nullableString(result.detail())
                + "}"
                + "}";
    }

    private String controlResultJson(AutonomousPrintControlService.ControlResult result) {
        return "{"
                + "\"job\":" + printJobJson(result.job()) + ","
                + "\"execution\":{"
                + "\"accepted\":" + result.success() + ","
                + "\"success\":" + result.success() + ","
                + "\"wireCommand\":" + nullableString(result.wireCommand()) + ","
                + "\"response\":" + nullableString(result.response()) + ","
                + "\"outcome\":" + nullableString(result.success() ? "SUCCESS" : "FAILED") + ","
                + "\"failureReason\":" + nullableString(
                        result.failureReason() == null ? null : result.failureReason().name())
                + ","
                + "\"failureDetail\":" + nullableString(result.failureDetail())
                + "}"
                + "}";
    }

    private String printerEventsJson(List<PrinterEvent> events) {
        StringBuilder json = new StringBuilder();
        json.append("{\"events\":[");

        boolean first = true;

        for (PrinterEvent event : events) {
            if (!first) {
                json.append(",");
            }

            json.append("{")
                    .append("\"id\":").append(event.id()).append(",")
                    .append("\"printerId\":").append(nullableString(event.printerId())).append(",")
                    .append("\"jobId\":").append(nullableString(event.jobId())).append(",")
                    .append("\"eventType\":\"").append(escapeJson(event.eventType())).append("\",")
                    .append("\"message\":").append(nullableString(event.message())).append(",")
                    .append("\"createdAt\":\"").append(escapeJson(event.createdAt().toString())).append("\"")
                    .append("}");

            first = false;
        }

        json.append("]}");
        return json.toString();
    }

    private String printersJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\"printers\":[");

        boolean first = true;

        for (PrinterRuntimeNode node : printerRegistry.all()) {
            if (!first) {
                json.append(",");
            }

            json.append(printerJson(node));
            first = false;
        }

        json.append("]}");

        return json.toString();
    }

    private String printerJson(PrinterRuntimeNode node) {
        PrinterSnapshot snapshot = stateCache.findByPrinterId(node.id()).orElse(null);

        return "{"
                + "\"id\":\"" + escapeJson(node.id()) + "\","
                + "\"displayName\":\"" + escapeJson(node.displayName()) + "\","
                + "\"name\":\"" + escapeJson(node.displayName()) + "\","
                + "\"portName\":\"" + escapeJson(node.portName()) + "\","
                + "\"mode\":\"" + escapeJson(node.mode()) + "\","
                + "\"enabled\":" + node.enabled() + ","
                + "\"state\":\"" + (snapshot == null ? "UNKNOWN" : snapshot.state()) + "\","
                + "\"hotendTemperature\":" + nullableNumber(snapshot == null ? null : snapshot.hotendTemperature())
                + ","
                + "\"bedTemperature\":" + nullableNumber(snapshot == null ? null : snapshot.bedTemperature()) + ","
                + "\"lastResponse\":" + nullableString(snapshot == null ? null : snapshot.lastResponse()) + ","
                + "\"errorMessage\":" + nullableString(snapshot == null ? null : snapshot.errorMessage()) + ","
                + "\"updatedAt\":" + nullableString(snapshot == null ? null : String.valueOf(snapshot.updatedAt()))
                + "}";
    }

    private String snapshotJson(PrinterSnapshot snapshot) {
        if (snapshot == null) {
            return "{"
                    + "\"state\":\"UNKNOWN\","
                    + "\"hotendTemperature\":null,"
                    + "\"bedTemperature\":null,"
                    + "\"lastResponse\":null,"
                    + "\"errorMessage\":null,"
                    + "\"updatedAt\":null"
                    + "}";
        }

        return "{"
                + "\"state\":\"" + snapshot.state() + "\","
                + "\"hotendTemperature\":" + nullableNumber(snapshot.hotendTemperature()) + ","
                + "\"bedTemperature\":" + nullableNumber(snapshot.bedTemperature()) + ","
                + "\"lastResponse\":" + nullableString(snapshot.lastResponse()) + ","
                + "\"errorMessage\":" + nullableString(snapshot.errorMessage()) + ","
                + "\"updatedAt\":" + nullableString(String.valueOf(snapshot.updatedAt()))
                + "}";
    }

    private void sendJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");

        exchange.sendResponseHeaders(statusCode, bytes.length);

        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private byte[] readBodyBytes(HttpExchange exchange) throws IOException {
        return exchange.getRequestBody().readAllBytes();
    }

    private String errorJson(String message) {
        return "{\"error\":" + nullableString(message) + "}";
    }

    private String nullableNumber(Double value) {
        if (value == null) {
            return "null";
        }

        return formatDouble(value);
    }

    private String nullableInteger(Integer value) {
        if (value == null) {
            return "null";
        }

        return String.valueOf(value);
    }

    private String nullableLong(Long value) {
        if (value == null) {
            return "null";
        }

        return String.valueOf(value);
    }

    private String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String nullableString(String value) {
        if (value == null) {
            return "null";
        }

        return "\"" + escapeJson(value) + "\"";
    }

    private String requiredJsonString(String body, String fieldName) {
        String value = optionalJsonString(body, fieldName, null);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank(fieldName));
        }

        return value.trim();
    }

    private String resolveJobName(
            String requestedName,
            JobType type,
            PrintFile printFile,
            PrinterSdFile printerSdFile) {
        if (requestedName != null && !requestedName.isBlank()) {
            return requestedName.trim();
        }

        if (type == JobType.PRINT_FILE && printerSdFile != null) {
            return "Print " + printerSdFile.displayName();
        }

        if (type == JobType.PRINT_FILE && printFile != null) {
            return "Print " + printFile.originalFilename();
        }

        throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("name"));
    }

    private String optionalJsonString(String body, String fieldName, String fallback) {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"([^\"]*)\"");

        Matcher matcher = pattern.matcher(body);

        if (!matcher.find()) {
            return fallback;
        }

        return matcher.group(1);
    }

    private String queryParameter(String rawQuery, String name) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }

        String prefix = name + "=";
        String[] parts = rawQuery.split("&");

        for (String part : parts) {
            if (part.startsWith(prefix)) {
                return URLDecoder.decode(part.substring(prefix.length()), StandardCharsets.UTF_8);
            }
        }

        return null;
    }

    private boolean optionalJsonBoolean(String body, String fieldName, boolean fallback) {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*(true|false)");

        Matcher matcher = pattern.matcher(body);

        if (!matcher.find()) {
            return fallback;
        }

        return Boolean.parseBoolean(matcher.group(1));
    }

    private long optionalJsonLong(String body, String fieldName, long fallback) {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*(-?[0-9]+)");

        Matcher matcher = pattern.matcher(body);

        if (!matcher.find()) {
            return fallback;
        }

        return Long.parseLong(matcher.group(1));
    }

    private Long optionalJsonLongObject(String body, String fieldName) {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*(-?[0-9]+)");

        Matcher matcher = pattern.matcher(body);

        if (!matcher.find()) {
            return null;
        }

        return Long.parseLong(matcher.group(1));
    }

    private int optionalJsonInteger(String body, String fieldName, int fallback) {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*(-?[0-9]+)");

        Matcher matcher = pattern.matcher(body);

        if (!matcher.find()) {
            return fallback;
        }

        return Integer.parseInt(matcher.group(1));
    }

    private Integer optionalJsonIntegerObject(String body, String fieldName) {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*(-?[0-9]+)");

        Matcher matcher = pattern.matcher(body);

        if (!matcher.find()) {
            return null;
        }

        return Integer.parseInt(matcher.group(1));
    }

    private double optionalJsonDouble(String body, String fieldName, double fallback) {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)");

        Matcher matcher = pattern.matcher(body);

        if (!matcher.find()) {
            return fallback;
        }

        return Double.parseDouble(matcher.group(1));
    }

    private Double optionalJsonDoubleObject(String body, String fieldName) {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)");

        Matcher matcher = pattern.matcher(body);

        if (!matcher.find()) {
            return null;
        }

        return Double.parseDouble(matcher.group(1));
    }

    private MonitoringRules.ErrorPersistenceBehavior optionalJsonErrorPersistenceBehavior(
            String body,
            String fieldName,
            MonitoringRules.ErrorPersistenceBehavior fallback) {
        String value = optionalJsonString(body, fieldName, null);

        if (value == null || value.isBlank()) {
            return fallback;
        }

        return MonitoringRules.parseErrorPersistenceBehavior(value);
    }

    private JobType parseJobType(String value) {
        try {
            return JobType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(OperationMessages.invalidEnumField("type", value), exception);
        }
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private void handleDashboard(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        String path = exchange.getRequestURI().getPath();

        if ("/dashboard".equals(path) || "/dashboard/".equals(path)) {
            sendResource(exchange, "/dashboard/index.html", "text/html; charset=utf-8");
            return;
        }

        if (!path.startsWith("/dashboard/")) {
            sendJson(exchange, 404, errorJson(OperationMessages.DASHBOARD_RESOURCE_NOT_FOUND));
            return;
        }

        String relativePath = path.substring("/dashboard/".length());

        if (relativePath.isBlank() || relativePath.contains("..")) {
            sendJson(exchange, 404, errorJson(OperationMessages.DASHBOARD_RESOURCE_NOT_FOUND));
            return;
        }

        String resourcePath = "/dashboard/" + relativePath;
        String contentType = detectDashboardContentType(resourcePath);

        sendResource(exchange, resourcePath, contentType);
    }

    private String detectDashboardContentType(String resourcePath) {
        if (resourcePath.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }

        if (resourcePath.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }

        if (resourcePath.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }

        if (resourcePath.endsWith(".svg")) {
            return "image/svg+xml";
        }

        return "application/octet-stream";
    }

    private void sendResource(
            HttpExchange exchange,
            String resourcePath,
            String contentType) throws IOException {
        try (InputStream inputStream = RemoteApiServer.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                sendJson(exchange, 404, errorJson(OperationMessages.resourceNotFound(resourcePath)));
                return;
            }

            byte[] bytes = inputStream.readAllBytes();

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);

            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        }
    }

    private String printJobExecutionStepsJson(List<PrintJobExecutionStep> steps) {
        StringBuilder json = new StringBuilder();
        json.append("{\"executionSteps\":[");

        boolean first = true;

        for (PrintJobExecutionStep step : steps) {
            if (!first) {
                json.append(",");
            }
            json.append(printJobExecutionStepJson(step));
            first = false;
        }

        json.append("]}");
        return json.toString();
    }

    private String sdCardUploadResultJson(SdCardUploadService.UploadResult result) {
        return "{"
                + "\"printerId\":\"" + escapeJson(result.printerId()) + "\","
                + "\"printFileId\":\"" + escapeJson(result.printFileId()) + "\","
                + "\"originalFilename\":\"" + escapeJson(result.originalFilename()) + "\","
                + "\"requestedTargetFilename\":\"" + escapeJson(result.requestedTargetFilename()) + "\","
                + "\"linkedFirmwarePath\":\"" + escapeJson(result.linkedFirmwarePath()) + "\","
                + "\"printerSdFileId\":\"" + escapeJson(result.printerSdFileId()) + "\","
                + "\"uploadedLineCount\":" + result.uploadedLineCount() + ","
                + "\"totalLineCount\":" + result.totalLineCount() + ","
                + "\"totalByteCount\":" + result.totalByteCount() + ","
                + "\"rejectedLineCount\":" + result.rejectedLineCount() + ","
                + "\"success\":" + result.success() + ","
                + "\"detail\":" + nullableString(result.detail())
                + "}";
    }

    private String sdCardUploadProgressJson(SdCardUploadService.UploadProgress progress) {
        if (progress == null) {
            return "{\"active\":false,\"state\":\"idle\"}";
        }

        long totalLineCount = progress.totalLineCount();
        long uploadedLineCount = progress.uploadedLineCount();
        long percent = totalLineCount <= 0 ? 0L : Math.min(100L, uploadedLineCount * 100L / totalLineCount);
        long qualityPercent = uploadedLineCount <= 0
                ? 100L
                : Math.max(0L, Math.min(100L,
                        (uploadedLineCount * 100L) / (uploadedLineCount + progress.rejectedLineCount())));

        return "{"
                + "\"printerId\":\"" + escapeJson(progress.printerId()) + "\","
                + "\"printFileId\":" + nullableString(progress.printFileId()) + ","
                + "\"originalFilename\":" + nullableString(progress.originalFilename()) + ","
                + "\"requestedTargetFilename\":" + nullableString(progress.requestedTargetFilename()) + ","
                + "\"state\":\"" + escapeJson(progress.state()) + "\","
                + "\"active\":" + progress.active() + ","
                + "\"uploadedLineCount\":" + progress.uploadedLineCount() + ","
                + "\"totalLineCount\":" + progress.totalLineCount() + ","
                + "\"totalByteCount\":" + progress.totalByteCount() + ","
                + "\"rejectedLineCount\":" + progress.rejectedLineCount() + ","
                + "\"percent\":" + percent + ","
                + "\"qualityPercent\":" + qualityPercent + ","
                + "\"startedAt\":"
                + nullableString(progress.startedAt() == null ? null : progress.startedAt().toString()) + ","
                + "\"updatedAt\":"
                + nullableString(progress.updatedAt() == null ? null : progress.updatedAt().toString()) + ","
                + "\"detail\":" + nullableString(progress.detail()) + ","
                + "\"bytesPerSecond\":" + formatDouble(progress.bytesPerSecond()) + ","
                + "\"linesPerSecond\":" + formatDouble(progress.linesPerSecond()) + ","
                + "\"elapsedSeconds\":" + progress.elapsedSeconds() + ","
                + "\"estimatedSecondsRemaining\":" + progress.estimatedSecondsRemaining() + ","
                + "\"theoreticalMaxBytesPerSecond\":" + formatDouble(progress.theoreticalMaxBytesPerSecond()) + ","
                + "\"efficiencyPercent\":" + formatDouble(progress.efficiencyPercent())
                + "}";
    }

    private String printJobExecutionStepJson(PrintJobExecutionStep step) {
        return "{"
                + "\"id\":" + step.id() + ","
                + "\"jobId\":\"" + escapeJson(step.jobId()) + "\","
                + "\"stepIndex\":" + step.stepIndex() + ","
                + "\"stepName\":\"" + escapeJson(step.stepName()) + "\","
                + "\"wireCommand\":" + nullableString(step.wireCommand()) + ","
                + "\"response\":" + nullableString(step.response()) + ","
                + "\"outcome\":\"" + escapeJson(step.outcome()) + "\","
                + "\"success\":" + step.success() + ","
                + "\"failureReason\":" + nullableString(step.failureReason()) + ","
                + "\"failureDetail\":" + nullableString(step.failureDetail()) + ","
                + "\"createdAt\":\"" + escapeJson(step.createdAt().toString()) + "\""
                + "}";
    }

    private void rollbackRegister(String printerId) {
        try {
            monitoringScheduler.stopMonitoring(printerId);
            printerRegistry.remove(printerId);
            stateCache.remove(printerId);
        } catch (Exception exception) {
            System.err.println(OperationMessages.failedToRollbackPrinterRegistration(
                    printerId,
                    safeMessage(exception)));
        }
    }

    private String closeUploadSessionResultJson(SdCardUploadService.CloseUploadSessionResult result) {
        return "{"
                + "\"printerId\":\"" + escapeJson(result.printerId()) + "\","
                + "\"lineNumber\":" + result.lineNumber() + ","
                + "\"attempts\":" + result.attempts() + ","
                + "\"success\":" + result.success() + ","
                + "\"response\":" + nullableString(result.response()) + ","
                + "\"detail\":" + nullableString(result.detail())
                + "}";
    }

    private String safeMessage(Exception exception) {
        if (exception == null) {
            return OperationMessages.UNKNOWN_API_ERROR;
        }

        return OperationMessages.safeDetail(
                exception.getMessage(),
                OperationMessages.UNKNOWN_API_ERROR);
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
