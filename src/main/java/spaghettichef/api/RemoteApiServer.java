package spaghettichef.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import spaghettichef.AppVersion;
import spaghettichef.OperationMessages;
import spaghettichef.SpaghettiChefLog;
import spaghettichef.PrinterSnapshot;
import spaghettichef.PrinterState;
import spaghettichef.SerialPortGuidance;
import spaghettichef.command.PrinterCommandService;
import spaghettichef.command.SdCardFile;
import spaghettichef.command.SdCardFileList;
import spaghettichef.command.SdCardService;
import spaghettichef.config.RuntimeDefaults;
import spaghettichef.job.AsyncPrintJobExecutor;
// import spaghettichef.job.JobFailureReason;
import spaghettichef.command.SdCardUploadService;
import spaghettichef.job.AutonomousPrintControlService;
import spaghettichef.job.PrintFile;
import spaghettichef.job.PrintFileService;
import spaghettichef.job.PrintJobExecutionStep;
import spaghettichef.job.PrinterSdFile;
import spaghettichef.job.PrinterActionGuard;
import spaghettichef.job.PrinterSdFileService;
import spaghettichef.job.JobState;
import spaghettichef.persistence.PrintJobExecutionStepStore;
import spaghettichef.job.JobType;
import spaghettichef.job.PrintJob;
import spaghettichef.job.PrintJobService;
import spaghettichef.job.PrinterResponseClassifier;
import spaghettichef.monitoring.GlobalMonitoringService;
import spaghettichef.monitoring.GlobalMonitoringSnapshot;
import spaghettichef.monitoring.PrinterMonitoringScheduler;
import spaghettichef.persistence.MonitoringRules;
import spaghettichef.persistence.MonitoringRulesStore;
import spaghettichef.persistence.OperatorAuditEvent;
import spaghettichef.persistence.OperatorAuditStore;
import spaghettichef.persistence.PrintFileSettings;
import spaghettichef.persistence.PrintFileSettingsStore;
import spaghettichef.persistence.PrinterConfigurationStore;
import spaghettichef.persistence.PrinterEvent;
import spaghettichef.persistence.PrinterEventStore;
import spaghettichef.persistence.RoleProfileStore;
import spaghettichef.persistence.SecuritySettings;
import spaghettichef.persistence.SecuritySettingsStore;
import spaghettichef.persistence.SerialTransferSettings;
import spaghettichef.persistence.SerialTransferSettingsStore;
import spaghettichef.runtime.PrinterRegistry;
import spaghettichef.runtime.PrinterRuntimeNode;
import spaghettichef.runtime.PrinterRuntimeNodeFactory;
import spaghettichef.runtime.PrinterRuntimeStateCache;
import spaghettichef.security.LocalRole;
import spaghettichef.security.Permission;
import spaghettichef.security.RoleProfile;
import spaghettichef.security.ActionPermissionResolver;
import spaghettichef.security.AuthorizationException;
import spaghettichef.security.AuthorizationService;
import spaghettichef.security.ConfirmationRequiredException;
import spaghettichef.security.DangerousAction;
import spaghettichef.security.DangerousActionGuard;
import spaghettichef.camera.CameraSnapshotDeletionReport;
import spaghettichef.camera.CameraSnapshotPurgeReport;
import spaghettichef.camera.CameraSnapshotPurgeService;
import spaghettichef.camera.CameraSnapshotManagementService;
import spaghettichef.camera.CameraCaptureService;
import spaghettichef.camera.CameraCalculationRunService;
import spaghettichef.camera.CameraDeltaSetGenerationResult;
import spaghettichef.camera.CameraDeltaSetService;
import spaghettichef.camera.CameraAnalysisTraceRow;
import spaghettichef.camera.CameraAnalysisTraceService;
import spaghettichef.camera.CameraCalculationComparisonFrame;
import spaghettichef.camera.CameraCalculationComparisonService;
import spaghettichef.camera.CameraCalculationRunComparison;
import spaghettichef.camera.CameraCalculationRunSummary;
import spaghettichef.camera.CameraAnalysisSessionService;
import spaghettichef.camera.CameraSafetyDecisionService;
import spaghettichef.camera.CameraSettingsService;
import spaghettichef.camera.CameraStoragePaths;
import spaghettichef.persistence.CameraSnapshotEntry;
import spaghettichef.persistence.CameraSnapshotEntryStore;
import spaghettichef.persistence.CameraSnapshotJobSummary;
import spaghettichef.persistence.CameraSettings;
import spaghettichef.persistence.CameraDeltaFrame;
import spaghettichef.persistence.CameraDeltaFrameStore;
import spaghettichef.persistence.CameraDeltaSet;
import spaghettichef.persistence.CameraDeltaSetStore;
import spaghettichef.persistence.CameraCalculationResult;
import spaghettichef.persistence.CameraCalculationResultStore;
import spaghettichef.persistence.CameraCalculationRun;
import spaghettichef.persistence.CameraCalculationRunStore;
import spaghettichef.persistence.CameraAnalysisSampleStore;
import spaghettichef.persistence.CameraAnalysisSessionStore;
import spaghettichef.persistence.CameraEventStore;
import spaghettichef.persistence.CameraSettingsStore;
import spaghettichef.persistence.CameraSnapshotMetadataStore;
// import spaghettichef.persistence.PrintJobStore;
import spaghettichef.camera.CameraJobService;
import spaghettichef.camera.CameraMonitoringScheduler;
import spaghettichef.camera.CameraMonitoringService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final SecuritySettingsStore securitySettingsStore;
    private final RoleProfileStore roleProfileStore;
    private final OperatorAuditStore operatorAuditStore;
    private final PrinterEventStore printerEventStore;
    private final PrinterCommandService printerCommandService;
    private final SdCardService sdCardService;
    private final PrintFileService printFileService;
    private final PrinterSdFileService printerSdFileService;
    private final PrintJobService printJobService;
    private final AsyncPrintJobExecutor asyncPrintJobExecutor;
    private final PrintJobExecutionStepStore printJobExecutionStepStore;
    private final SdCardUploadService sdCardUploadService;
    private final GlobalMonitoringService globalMonitoringService;
    private final PrinterResponseClassifier printerResponseClassifier;
    private final AutonomousPrintControlService autonomousPrintControlService;
    private final ActionPermissionResolver actionPermissionResolver;
    private final DangerousActionGuard dangerousActionGuard;
    private final CameraApiHandler cameraApiHandler;
    private final CameraMonitoringScheduler cameraMonitoringScheduler;
    private final CameraSnapshotManagementService cameraSnapshotManagementService;
    private final CameraSnapshotPurgeService cameraSnapshotPurgeService;
    private final CameraDeltaSetService cameraDeltaSetService;
    private final CameraDeltaSetStore cameraDeltaSetStore;
    private final CameraDeltaFrameStore cameraDeltaFrameStore;
    private final CameraCalculationRunService cameraCalculationRunService;
    private final CameraCalculationRunStore cameraCalculationRunStore;
    private final CameraCalculationResultStore cameraCalculationResultStore;
    private final CameraAnalysisTraceService cameraAnalysisTraceService;
    private final CameraCalculationComparisonService cameraCalculationComparisonService;
    private final Path cameraStorageDirectory;

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
        this.securitySettingsStore = new SecuritySettingsStore();
        this.roleProfileStore = new RoleProfileStore();
        this.operatorAuditStore = new OperatorAuditStore();
        this.printerEventStore = printerEventStore;
        this.printerCommandService = printerCommandService;
        this.sdCardService = sdCardService;
        this.sdCardUploadService = sdCardUploadService;
        this.printFileService = printFileService;
        this.printerSdFileService = printerSdFileService;
        this.printJobService = printJobService;
        this.asyncPrintJobExecutor = asyncPrintJobExecutor;
        this.printJobExecutionStepStore = printJobExecutionStepStore;
        this.globalMonitoringService = new GlobalMonitoringService(
                printerRegistry,
                stateCache,
                printJobService,
                sdCardUploadService);
        this.printerResponseClassifier = new PrinterResponseClassifier();
        this.autonomousPrintControlService = new AutonomousPrintControlService(
                printJobService,
                printerRegistry,
                monitoringScheduler,
                printJobExecutionStepStore);
        this.actionPermissionResolver = new ActionPermissionResolver();
        this.dangerousActionGuard = new DangerousActionGuard();

        CameraSettingsService cameraSettingsService = new CameraSettingsService(
                new CameraSettingsStore());

        CameraEventStore cameraEventStore = new CameraEventStore();
        CameraSnapshotMetadataStore cameraSnapshotMetadataStore = new CameraSnapshotMetadataStore();
        CameraSnapshotEntryStore cameraSnapshotEntryStore = new CameraSnapshotEntryStore();

        this.cameraStorageDirectory = CameraStoragePaths.defaultBaseDirectory();
        SpaghettiChefLog.info("Default camera storage base: "
                + this.cameraStorageDirectory.toAbsolutePath().normalize());

        CameraJobService cameraJobService = new CameraJobService();

        CameraCaptureService cameraCaptureService = new CameraCaptureService(
                cameraSettingsService,
                cameraEventStore,
                cameraSnapshotMetadataStore,
                this.cameraStorageDirectory,
                java.time.Clock.systemUTC(),
                new spaghettichef.camera.ImageDeltaFrameAnalyzer(),
                new spaghettichef.camera.SpaghettiDetectionService(),
                cameraSnapshotEntryStore,
                cameraJobService);

        CameraMonitoringService cameraMonitoringService = new CameraMonitoringService(cameraCaptureService);

        this.cameraMonitoringScheduler = new CameraMonitoringScheduler(
                cameraMonitoringService,
                Executors.newScheduledThreadPool(2));

        this.cameraSnapshotManagementService = new CameraSnapshotManagementService(
                cameraSettingsService,
                cameraSnapshotEntryStore);
        this.cameraSnapshotPurgeService = new CameraSnapshotPurgeService(cameraSnapshotEntryStore);
        this.cameraDeltaSetService = new CameraDeltaSetService();
        this.cameraDeltaSetStore = new CameraDeltaSetStore();
        this.cameraDeltaFrameStore = new CameraDeltaFrameStore();
        this.cameraCalculationRunService = new CameraCalculationRunService();
        this.cameraCalculationRunStore = new CameraCalculationRunStore();
        this.cameraCalculationResultStore = new CameraCalculationResultStore();
        this.cameraAnalysisTraceService = new CameraAnalysisTraceService(
                this.cameraCalculationRunStore,
                this.cameraCalculationResultStore,
                this.cameraDeltaFrameStore,
                cameraSnapshotEntryStore);
        this.cameraCalculationComparisonService = new CameraCalculationComparisonService(
                this.cameraCalculationRunStore,
                this.cameraCalculationResultStore);

        CameraAnalysisSampleStore cameraAnalysisSampleStore = new CameraAnalysisSampleStore();
        CameraSafetyDecisionService cameraSafetyDecisionService = new CameraSafetyDecisionService(
                cameraSettingsService,
                cameraAnalysisSampleStore,
                cameraEventStore,
                printJobService,
                autonomousPrintControlService);

        CameraAnalysisSessionService cameraAnalysisSessionService = new CameraAnalysisSessionService(
                cameraCaptureService,
                cameraSnapshotMetadataStore,
                new CameraAnalysisSessionStore(),
                cameraAnalysisSampleStore,
                cameraSafetyDecisionService,
                this.cameraStorageDirectory);

        this.cameraApiHandler = new CameraApiHandler(
                cameraCaptureService,
                cameraSettingsService,
                cameraEventStore,
                cameraSnapshotMetadataStore,
                cameraAnalysisSessionService,
                cameraJobService,
                this.cameraMonitoringScheduler,
                this.cameraSnapshotPurgeService);

    }

    public void start() {
        if (server != null) {
            return;
        }

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newFixedThreadPool(RuntimeDefaults.DEFAULT_API_THREAD_POOL_SIZE));

            server.createContext("/health", exchange -> safeHandle(exchange, this::handleHealth));
            server.createContext("/version", exchange -> safeHandle(exchange, this::handleVersion));
            server.createContext("/printers", exchange -> safeHandle(exchange, this::handlePrinters));
            server.createContext("/print-files", exchange -> safeHandle(exchange, this::handlePrintFiles));
            server.createContext("/printer-sd-files", exchange -> safeHandle(exchange, this::handlePrinterSdFiles));
            server.createContext("/jobs", exchange -> safeHandle(exchange, this::handleJobs));
            server.createContext("/monitoring", exchange -> safeHandle(exchange, this::handleMonitoring));
            server.createContext("/operator-audit", exchange -> safeHandle(exchange, this::handleOperatorAudit));
            server.createContext("/settings/monitoring",
                    exchange -> safeHandle(exchange, this::handleMonitoringSettings));
            server.createContext("/settings/print-files",
                    exchange -> safeHandle(exchange, this::handlePrintFileSettings));
            server.createContext("/settings/serial-transfer",
                    exchange -> safeHandle(exchange, this::handleSerialTransferSettings));
            server.createContext("/settings/security",
                    exchange -> safeHandle(exchange, this::handleSecuritySettings));
            server.createContext("/security", exchange -> safeHandle(exchange, this::handleSecurity));
            server.createContext("/admin", exchange -> safeHandle(exchange, this::handleAdmin));
            server.createContext("/dashboard", exchange -> safeHandle(exchange, this::handleDashboard));
            server.start();

            SpaghettiChefLog.info(OperationMessages.apiServerStarted(port));
        } catch (IOException exception) {
            throw new IllegalStateException(OperationMessages.failedToStartApiServer(port), exception);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            cameraMonitoringScheduler.close();
            asyncPrintJobExecutor.close();
            SpaghettiChefLog.info(OperationMessages.apiServerStopped());
        }
    }

    private void safeHandle(HttpExchange exchange, ExchangeHandler handler) throws IOException {
        addCorsHeaders(exchange);

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        try {
            byte[] requestBodyBytes = cacheRequestBody(exchange);
            String requestBody = new String(requestBodyBytes, StandardCharsets.UTF_8);
            AuditContext auditContext = auditContext(exchange, requestBody);
            try {
                guardDangerousAction(auditContext, requestBody);
                authorize(auditContext);
                recordAudit(auditContext, "ACCEPTED", null);
            } catch (ConfirmationRequiredException | AuthorizationException exception) {
                recordAudit(auditContext, "REJECTED", safeMessage(exception));
                throw exception;
            }
            handler.handle(exchange);
        } catch (ConfirmationRequiredException exception) {
            sendJson(exchange, 428, confirmationRequiredJson(exception.requiredConfirmation()));
        } catch (AuthorizationException exception) {
            sendJson(exchange, 403, errorJson(safeMessage(exception)));
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

            SpaghettiChefLog.error(OperationMessages.apiOperationFailed(message));
            sendJson(exchange, 500, errorJson(message));
        } catch (Exception exception) {
            SpaghettiChefLog.error(OperationMessages.unexpectedApiError(safeMessage(exception)));
            sendJson(exchange, 500, errorJson(OperationMessages.INTERNAL_SERVER_ERROR));
        }
    }

    private byte[] cacheRequestBody(HttpExchange exchange) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.setAttribute("cachedBody", new byte[0]);
            return new byte[0];
        }

        byte[] body = exchange.getRequestBody().readAllBytes();
        exchange.setAttribute("cachedBody", body);
        return body;
    }

    private void authorize(AuditContext context) {
        if (!context.securitySettings().securityEnabled()) {
            return;
        }

        if (context.permission().isEmpty()) {
            return;
        }

        new AuthorizationService(roleProfileStore.loadAll()).require(context.role(), context.permission().get());
    }

    private void guardDangerousAction(AuditContext context, String requestBody) {
        if (!context.securitySettings().requireDangerousActionConfirmation()) {
            return;
        }

        context.dangerousAction().ifPresent(action -> dangerousActionGuard.requireConfirmed(action, requestBody));
    }

    private AuditContext auditContext(HttpExchange exchange, String requestBody) {
        SecuritySettings settings = securitySettingsStore.load();
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        LocalRole role = requestRole(exchange).orElse(settings.defaultRole());

        return new AuditContext(
                method,
                path,
                role,
                settings,
                actionPermissionResolver.resolve(method, path, requestBody),
                dangerousActionGuard.resolve(method, path, requestBody));
    }

    private void recordAudit(AuditContext context, String result, String failureReason) {
        if (!context.auditable()) {
            return;
        }

        TargetRef target = targetRef(context.path());
        operatorAuditStore.save(OperatorAuditEvent.create(
                "local-dashboard",
                context.role().name(),
                context.permission().map(Enum::name).orElse(null),
                context.dangerousAction().map(Enum::name).orElse(null),
                context.method() + " " + context.path(),
                target.type(),
                target.id(),
                result,
                failureReason));
    }

    private Optional<LocalRole> requestRole(HttpExchange exchange) {
        String value = exchange.getRequestHeaders().getFirst("X-SpaghettiChef-Role");
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(LocalRole.valueOf(value.trim().toUpperCase(Locale.ROOT)));
    }

    private TargetRef targetRef(String path) {
        String[] parts = path == null ? new String[0] : path.split("/");
        if (parts.length >= 3 && "printers".equals(parts[1])) {
            return new TargetRef("printer", parts[2]);
        }
        if (parts.length >= 3 && "jobs".equals(parts[1])) {
            return new TargetRef("job", parts[2]);
        }
        if (parts.length >= 3 && "printer-sd-files".equals(parts[1])) {
            return new TargetRef("printerSdFile", parts[2]);
        }
        if (parts.length >= 3 && "print-files".equals(parts[1])) {
            return new TargetRef("printFile", parts[2]);
        }
        if (parts.length >= 2 && "settings".equals(parts[1])) {
            return new TargetRef("settings", parts.length >= 3 ? parts[2] : "settings");
        }
        if (parts.length >= 2 && "security".equals(parts[1])) {
            return new TargetRef("security", parts.length >= 3 ? parts[2] : "security");
        }

        return new TargetRef(null, null);
    }

    private void addCorsHeaders(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type, X-SpaghettiChef-Role");
    }

    private record AuditContext(
            String method,
            String path,
            LocalRole role,
            SecuritySettings securitySettings,
            Optional<Permission> permission,
            Optional<DangerousAction> dangerousAction) {
        private boolean auditable() {
            return !"GET".equalsIgnoreCase(method)
                    && (permission.isPresent() || dangerousAction.isPresent());
        }
    }

    private record TargetRef(String type, String id) {
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        sendJson(exchange, 200, "{\"status\":\"ok\"}");
    }

    private void handleVersion(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        sendJson(exchange, 200, "{\"version\":\"" + escapeJson(AppVersion.current()) + "\"}");
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

    private void handleSecuritySettings(HttpExchange exchange) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 200, securitySettingsJson(securitySettingsStore.load()));
            return;
        }

        if ("PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = readBody(exchange);
            SecuritySettings currentSettings = securitySettingsStore.load();
            SecuritySettings updatedSettings = new SecuritySettings(
                    optionalJsonBoolean(body, "securityEnabled", currentSettings.securityEnabled()),
                    optionalJsonLocalRole(body, "defaultRole", currentSettings.defaultRole()),
                    optionalJsonBoolean(
                            body,
                            "requireDangerousActionConfirmation",
                            currentSettings.requireDangerousActionConfirmation()));
            sendJson(exchange, 200, securitySettingsJson(securitySettingsStore.save(updatedSettings)));
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

    private void handleSecurity(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        if ("/security/profile".equals(path)) {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
                return;
            }

            SecuritySettings settings = securitySettingsStore.load();
            RoleProfile profile = roleProfileStore.loadAll().get(settings.defaultRole());
            sendJson(exchange, 200, securityProfileJson(settings, profile));
            return;
        }

        if ("/security/roles".equals(path)) {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 200, roleProfilesJson(roleProfileStore.loadAll()));
                return;
            }

            if ("PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
                RoleProfile profile = roleProfileFromJson(readBody(exchange));
                roleProfileStore.save(profile);
                sendJson(exchange, 200, roleProfilesJson(roleProfileStore.loadAll()));
                return;
            }

            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        sendJson(exchange, 404, errorJson(OperationMessages.resourceNotFound(path)));
    }

    private void handleAdmin(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if ("/admin/camera/snapshot/jobs".equals(path)) {
            if (!"GET".equalsIgnoreCase(method)) {
                sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
                return;
            }

            String printerId = queryParameter(exchange.getRequestURI().getRawQuery(), "printerId");
            List<CameraSnapshotJobSummary> jobs = printerId == null || printerId.isBlank()
                    ? cameraSnapshotManagementService.listJobs()
                    : cameraSnapshotManagementService.listJobs(printerId);
            sendJson(exchange, 200, cameraSnapshotJobSummariesJson(jobs));
            return;
        }

        String prefix = "/admin/camera/snapshot/jobs/";
        if (path.startsWith("/admin/camera/snapshot/files/")) {
            handleAdminCameraSnapshotFile(exchange, path);
            return;
        }

        if (path.startsWith("/admin/camera/delta-sets/")) {
            handleAdminCameraDeltaSet(exchange, path);
            return;
        }

        if (path.startsWith("/admin/camera/calculation-runs/")) {
            handleAdminCameraCalculationRun(exchange, path);
            return;
        }

        if (!path.startsWith(prefix)) {
            sendJson(exchange, 404, errorJson(OperationMessages.resourceNotFound(path)));
            return;
        }

        String remaining = path.substring(prefix.length());
        String[] parts = remaining.split("/");
        if (parts.length < 1 || parts[0].isBlank()) {
            sendJson(exchange, 404, errorJson(OperationMessages.resourceNotFound(path)));
            return;
        }

        String jobId = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
        String printerId = queryParameter(exchange.getRequestURI().getRawQuery(), "printerId");
        if (parts.length == 1) {
            if ("GET".equalsIgnoreCase(method)) {
                sendJson(exchange, 200, cameraSnapshotEntriesJson(
                        jobId,
                        printerId == null || printerId.isBlank()
                                ? cameraSnapshotManagementService.entriesForJob(jobId)
                                : cameraSnapshotManagementService.entriesForJob(printerId, jobId)));
                return;
            }

            if ("DELETE".equalsIgnoreCase(method)) {
                CameraSnapshotDeletionReport report = printerId == null || printerId.isBlank()
                        ? cameraSnapshotManagementService.deleteJobSnapshot(jobId)
                        : cameraSnapshotManagementService.deleteJobSnapshot(printerId, jobId);
                sendJson(exchange, 200, cameraSnapshotDeletionReportJson(report));
                return;
            }

            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        if (parts.length == 2 && "timeline".equals(parts[1])) {
            if (!"GET".equalsIgnoreCase(method)) {
                sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
                return;
            }

            sendJson(exchange, 200, cameraSnapshotTimelineJson(
                    jobId,
                    printerId == null || printerId.isBlank()
                            ? cameraSnapshotManagementService.entriesForJob(jobId)
                            : cameraSnapshotManagementService.entriesForJob(printerId, jobId)));
            return;
        }

        if (parts.length == 2 && "purge".equals(parts[1])) {
            if (!"POST".equalsIgnoreCase(method)) {
                sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
                return;
            }
            long cameraJobId = parsePositiveLong(jobId, "cameraJobId");
            String body = readBody(exchange);
            String requestedPrinterId = printerId == null || printerId.isBlank()
                    ? requiredJsonString(body, "printerId")
                    : printerId;
            int retentionSnapshotCount = optionalJsonInteger(
                    body,
                    "retentionSnapshotCount",
                    CameraSettings.DEFAULT_RETENTION_SNAPSHOT_COUNT);
            int purgeRetentionFrequency = optionalJsonInteger(
                    body,
                    "purgeRetentionFrequency",
                    CameraSettings.DEFAULT_PURGE_RETENTION_FREQUENCY);
            String message = optionalJsonString(body, "message", "manual snapshot purge");

            CameraSnapshotPurgeReport report = cameraSnapshotPurgeService.purge(
                    requestedPrinterId,
                    cameraJobId,
                    retentionSnapshotCount,
                    purgeRetentionFrequency,
                    message);
            sendJson(exchange, 200, cameraSnapshotPurgeReportJson(report));
            return;
        }

        if (parts.length == 2 && "delta-sets".equals(parts[1])) {
            long cameraJobId = parsePositiveLong(jobId, "cameraJobId");

            if ("GET".equalsIgnoreCase(method)) {
                sendJson(exchange, 200, cameraDeltaSetsJson(cameraDeltaSetStore.findByCameraJobId(cameraJobId)));
                return;
            }

            if ("POST".equalsIgnoreCase(method)) {
                String body = readBody(exchange);
                String requestedPrinterId = printerId == null || printerId.isBlank()
                        ? requiredJsonString(body, "printerId")
                        : printerId;
                int deltaSnapshotStep = optionalJsonInteger(body, "deltaSnapshotStep", 1);
                String methodName = optionalJsonString(body, "methodName", null);
                String message = optionalJsonString(body, "message", null);

                CameraDeltaSetGenerationResult result = cameraDeltaSetService.generate(
                        requestedPrinterId,
                        cameraJobId,
                        deltaSnapshotStep,
                        methodName,
                        message);
                sendJson(exchange, 201, cameraDeltaSetGenerationResultJson(result));
                return;
            }

            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        if (parts.length == 2 && "recalculate-preview".equals(parts[1])) {
            if (!"POST".equalsIgnoreCase(method)) {
                sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
                return;
            }

            sendJson(exchange, 202, "{"
                    + "\"jobId\":\"" + escapeJson(jobId) + "\","
                    + "\"state\":\"placeholder\","
                    + "\"message\":\"camera_recalculate_preview_not_implemented\""
                    + "}");
            return;
        }

        sendJson(exchange, 404, errorJson(OperationMessages.resourceNotFound(path)));
    }

    private void handleAdminCameraDeltaSet(HttpExchange exchange, String path) throws IOException {
        String method = exchange.getRequestMethod();
        String remaining = path.substring("/admin/camera/delta-sets/".length());
        String[] parts = remaining.split("/");
        if (parts.length < 1 || parts[0].isBlank()) {
            sendJson(exchange, 404, errorJson(OperationMessages.resourceNotFound(path)));
            return;
        }

        long deltaSetId = parsePositiveLong(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), "deltaSetId");

        if (parts.length == 1 && "GET".equalsIgnoreCase(method)) {
            Optional<CameraDeltaSet> deltaSet = cameraDeltaSetStore.findById(deltaSetId);
            if (deltaSet.isEmpty()) {
                sendJson(exchange, 404, errorJson("camera_delta_set_not_found"));
                return;
            }

            sendJson(exchange, 200, "{\"deltaSet\":" + cameraDeltaSetJson(deltaSet.get()) + "}");
            return;
        }

        if (parts.length == 2 && "frames".equals(parts[1])) {
            if (!"GET".equalsIgnoreCase(method)) {
                sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
                return;
            }

            sendJson(exchange, 200,
                    cameraDeltaFramesJson(deltaSetId, cameraDeltaFrameStore.findByDeltaSetId(deltaSetId)));
            return;
        }

        if (parts.length == 2 && "calculation-runs".equals(parts[1])) {
            if ("GET".equalsIgnoreCase(method)) {
                sendJson(exchange, 200, cameraCalculationRunsJson(
                        deltaSetId,
                        cameraCalculationRunStore.findByDeltaSetId(deltaSetId)));
                return;
            }

            if ("POST".equalsIgnoreCase(method)) {
                String body = readBody(exchange);
                CameraCalculationRun run = cameraCalculationRunService.run(
                        deltaSetId,
                        optionalJsonString(body, "methodName", null),
                        optionalJsonDoubleObject(body, "confidenceThreshold"),
                        optionalJsonString(body, "parameterJson", null),
                        optionalJsonString(body, "message", null),
                        optionalJsonString(body, "engineName", null),
                        optionalJsonString(body, "rustExecutablePath", null));
                sendJson(exchange, 201, "{\"calculationRun\":" + cameraCalculationRunJson(run) + "}");
                return;
            }

            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        sendJson(exchange, 404, errorJson(OperationMessages.resourceNotFound(path)));
    }

    private void handleAdminCameraCalculationRun(HttpExchange exchange, String path) throws IOException {
        String method = exchange.getRequestMethod();
        String remaining = path.substring("/admin/camera/calculation-runs/".length());
        String[] parts = remaining.split("/");
        if (parts.length < 1 || parts[0].isBlank()) {
            sendJson(exchange, 404, errorJson(OperationMessages.resourceNotFound(path)));
            return;
        }

        long calculationRunId = parsePositiveLong(
                URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                "calculationRunId");

        if (parts.length == 1 && "GET".equalsIgnoreCase(method)) {
            Optional<CameraCalculationRun> run = cameraCalculationRunStore.findById(calculationRunId);
            if (run.isEmpty()) {
                sendJson(exchange, 404, errorJson("camera_calculation_run_not_found"));
                return;
            }

            sendJson(exchange, 200, "{\"calculationRun\":" + cameraCalculationRunJson(run.get()) + "}");
            return;
        }

        if (parts.length == 2 && "results".equals(parts[1])) {
            if (!"GET".equalsIgnoreCase(method)) {
                sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
                return;
            }

            sendJson(exchange, 200, cameraCalculationResultsJson(
                    calculationRunId,
                    cameraCalculationResultStore.findByCalculationRunId(calculationRunId)));
            return;
        }

        if (parts.length == 2 && "trace".equals(parts[1])) {
            if (!"GET".equalsIgnoreCase(method)) {
                sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
                return;
            }

            String printerId = queryParameter(exchange.getRequestURI().getRawQuery(), "printerId");
            sendJson(exchange, 200, cameraAnalysisTraceJson(
                    calculationRunId,
                    cameraAnalysisTraceService.traceForCalculationRun(calculationRunId, printerId)));
            return;
        }

        if (parts.length == 2 && "compare".equals(parts[1])) {
            if (!"GET".equalsIgnoreCase(method)) {
                sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
                return;
            }

            String printerId = queryParameter(exchange.getRequestURI().getRawQuery(), "printerId");
            String rightRunIdValue = queryParameter(exchange.getRequestURI().getRawQuery(), "rightRunId");
            if (rightRunIdValue == null || rightRunIdValue.isBlank()) {
                sendJson(exchange, 400, errorJson("rightRunId is required"));
                return;
            }
            long rightRunId = parsePositiveLong(
                    rightRunIdValue,
                    "rightRunId");
            sendJson(exchange, 200, cameraCalculationRunComparisonJson(
                    cameraCalculationComparisonService.compare(calculationRunId, rightRunId, printerId)));
            return;
        }

        sendJson(exchange, 404, errorJson(OperationMessages.resourceNotFound(path)));
    }

    private void handleAdminCameraSnapshotFile(HttpExchange exchange, String path) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        String idValue = path.substring("/admin/camera/snapshot/files/".length());
        long entryId;
        try {
            entryId = Long.parseLong(idValue);
        } catch (NumberFormatException exception) {
            sendJson(exchange, 400, errorJson("invalid_camera_snapshot_entry_id"));
            return;
        }

        Optional<CameraSnapshotEntry> entry = cameraSnapshotManagementService.entryById(entryId);
        if (entry.isEmpty()) {
            sendJson(exchange, 404, errorJson("camera_snapshot_entry_not_found"));
            return;
        }
        if (entry.get().fileDeleted()) {
            sendJson(exchange, 410, errorJson("camera_snapshot_file_deleted"));
            return;
        }

        Path snapshotPath = Path.of(entry.get().snapshotPath()).normalize();
        if (!Files.isRegularFile(snapshotPath)) {
            sendJson(exchange, 404, errorJson("camera_snapshot_file_not_found"));
            return;
        }

        byte[] bytes = Files.readAllBytes(snapshotPath);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", entry.get().contentType());
        headers.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private void handleMonitoring(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        sendJson(exchange, 200, globalMonitoringSnapshotJson(globalMonitoringService.snapshot()));
    }

    private void handleOperatorAudit(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        sendJson(exchange, 200, operatorAuditEventsJson(operatorAuditStore.findRecent(50)));
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

        if (parts.length >= 2 && "camera".equals(parts[1])) {
            handlePrinterCamera(exchange, printerId, parts);
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
                    SpaghettiChefLog.error(OperationMessages.failedToRestorePrinterAfterPut(
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
                    SpaghettiChefLog.error(OperationMessages.failedToRestorePrinterAfterDelete(
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

    private void handlePrinterCamera(HttpExchange exchange, String printerId, String[] parts) throws IOException {
        PrinterRuntimeNode node = printerRegistry.findById(printerId).orElse(null);

        if (node == null) {
            sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_NOT_FOUND));
            return;
        }

        if (parts.length < 3) {
            sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_ENDPOINT_NOT_FOUND));
            return;
        }

        String cameraResource = parts[2];

        if ("status".equals(cameraResource)) {
            cameraApiHandler.handleStatus(exchange, printerId);
            return;
        }

        if ("settings".equals(cameraResource)) {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                cameraApiHandler.handleGetSettings(exchange, printerId);
                return;
            }

            if ("PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
                cameraApiHandler.handlePutSettings(exchange, printerId);
                return;
            }

            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        if ("jobs".equals(cameraResource)) {
            if (parts.length != 4) {
                sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_ENDPOINT_NOT_FOUND));
                return;
            }

            String cameraJobAction = parts[3];

            if ("start".equals(cameraJobAction)) {
                cameraApiHandler.handleStartCameraJob(exchange, printerId);
                return;
            }

            if ("stop".equals(cameraJobAction)) {
                cameraApiHandler.handleStopCameraJob(exchange, printerId);
                return;
            }

            if ("active".equals(cameraJobAction)) {
                cameraApiHandler.handleActiveCameraJob(exchange, printerId);
                return;
            }

            sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_ENDPOINT_NOT_FOUND));
            return;
        }

        if ("snapshot".equals(cameraResource)) {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                cameraApiHandler.handleLatestSnapshot(exchange, printerId);
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                cameraApiHandler.handleCapture(exchange, printerId);
                return;
            }

            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        if ("events".equals(cameraResource)) {
            cameraApiHandler.handleEvents(exchange, printerId);
            return;
        }

        if ("snapshots".equals(cameraResource)) {
            if (parts.length == 3) {
                cameraApiHandler.handleSnapshots(exchange, printerId);
                return;
            }

            if (parts.length == 4) {
                cameraApiHandler.handleSnapshotFile(exchange, printerId, parts[3]);
                return;
            }
        }

        if ("analysis-sessions".equals(cameraResource)) {
            if (parts.length == 3) {
                cameraApiHandler.handleAnalysisSessions(exchange, printerId);
                return;
            }

            if (parts.length == 4) {
                cameraApiHandler.handleAnalysisSession(exchange, printerId, parts[3]);
                return;
            }

            if (parts.length == 5 && "stop".equals(parts[4])) {
                cameraApiHandler.handleStopAnalysisSession(exchange, printerId, parts[3]);
                return;
            }

            if (parts.length == 5 && "samples".equals(parts[4])) {
                cameraApiHandler.handleAnalysisSessionSamples(exchange, printerId, parts[3]);
                return;
            }
        }

        sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_ENDPOINT_NOT_FOUND));
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

    private String securitySettingsJson(SecuritySettings settings) {
        return "{"
                + "\"securityEnabled\":" + settings.securityEnabled() + ","
                + "\"defaultRole\":\"" + escapeJson(settings.defaultRole().name()) + "\","
                + "\"requireDangerousActionConfirmation\":"
                + settings.requireDangerousActionConfirmation()
                + "}";
    }

    private String securityProfileJson(SecuritySettings settings, RoleProfile profile) {
        return "{"
                + "\"settings\":" + securitySettingsJson(settings) + ","
                + "\"roleProfile\":" + roleProfileJson(profile)
                + "}";
    }

    private String roleProfilesJson(Map<LocalRole, RoleProfile> profiles) {
        StringBuilder json = new StringBuilder();
        json.append("{\"roleProfiles\":[");

        boolean first = true;
        for (LocalRole role : LocalRole.values()) {
            RoleProfile profile = profiles.get(role);
            if (profile == null) {
                continue;
            }
            if (!first) {
                json.append(",");
            }
            json.append(roleProfileJson(profile));
            first = false;
        }

        json.append("]}");
        return json.toString();
    }

    private String roleProfileJson(RoleProfile profile) {
        if (profile == null) {
            return "null";
        }

        return "{"
                + "\"role\":\"" + escapeJson(profile.role().name()) + "\","
                + "\"displayName\":\"" + escapeJson(profile.displayName()) + "\","
                + "\"builtIn\":" + profile.builtIn() + ","
                + "\"permissions\":" + RoleProfileStore.permissionsJson(profile.permissions())
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

    private String operatorAuditEventsJson(List<OperatorAuditEvent> events) {
        StringBuilder json = new StringBuilder();
        json.append("{\"auditEvents\":[");

        boolean first = true;

        for (OperatorAuditEvent event : events) {
            if (!first) {
                json.append(",");
            }

            json.append("{")
                    .append("\"id\":").append(event.id()).append(",")
                    .append("\"actor\":").append(nullableString(event.actor())).append(",")
                    .append("\"role\":").append(nullableString(event.role())).append(",")
                    .append("\"permission\":").append(nullableString(event.permission())).append(",")
                    .append("\"dangerousAction\":").append(nullableString(event.dangerousAction())).append(",")
                    .append("\"actionType\":\"").append(escapeJson(event.actionType())).append("\",")
                    .append("\"targetType\":").append(nullableString(event.targetType())).append(",")
                    .append("\"targetId\":").append(nullableString(event.targetId())).append(",")
                    .append("\"result\":\"").append(escapeJson(event.result())).append("\",")
                    .append("\"failureReason\":").append(nullableString(event.failureReason())).append(",")
                    .append("\"createdAt\":\"").append(escapeJson(event.createdAt().toString())).append("\"")
                    .append("}");

            first = false;
        }

        json.append("]}");
        return json.toString();
    }

    private String cameraSnapshotJobSummariesJson(List<CameraSnapshotJobSummary> summaries) {
        StringBuilder json = new StringBuilder();
        json.append("{\"jobs\":[");

        boolean first = true;
        for (CameraSnapshotJobSummary summary : summaries) {
            if (!first) {
                json.append(",");
            }

            json.append("{")
                    .append("\"id\":").append(nullableLong(summary.cameraJobId())).append(",")
                    .append("\"cameraJobId\":").append(nullableLong(summary.cameraJobId())).append(",")
                    .append("\"cameraJobKey\":\"").append(escapeJson(summary.jobId())).append("\",")
                    .append("\"jobId\":\"").append(escapeJson(summary.jobId())).append("\",")
                    .append("\"printerId\":").append(nullableString(summary.printerId())).append(",")
                    .append("\"linkedPrintJobId\":").append(nullableString(summary.linkedPrintJobId())).append(",")
                    .append("\"state\":")
                    .append(nullableString(summary.state() == null ? null : summary.state().name())).append(",")
                    .append("\"startedAt\":").append(nullableString(instantString(summary.startedAt()))).append(",")
                    .append("\"stoppedAt\":").append(nullableString(instantString(summary.stoppedAt()))).append(",")
                    .append("\"captureIntervalSeconds\":").append(summary.captureIntervalSeconds()).append(",")
                    .append("\"retainedSnapshots\":").append(summary.retainedSnapshots()).append(",")
                    .append("\"sourceType\":").append(nullableString(summary.sourceType())).append(",")
                    .append("\"sourceDescription\":").append(nullableString(summary.sourceDescription())).append(",")
                    .append("\"snapshotDirectory\":").append(nullableString(summary.snapshotDirectory())).append(",")
                    .append("\"fileCount\":").append(summary.fileCount()).append(",")
                    .append("\"totalBytes\":").append(summary.totalBytes()).append(",")
                    .append("\"firstCapturedAt\":").append(nullableString(instantString(summary.firstCapturedAt())))
                    .append(",")
                    .append("\"lastCapturedAt\":").append(nullableString(instantString(summary.lastCapturedAt())))
                    .append("}");
            first = false;
        }

        json.append("]}");
        return json.toString();
    }

    private String cameraSnapshotEntriesJson(String jobId, List<CameraSnapshotEntry> entries) {
        return "{\"jobId\":\"" + escapeJson(jobId) + "\",\"entries\":" + cameraSnapshotEntryArrayJson(entries) + "}";
    }

    private String cameraSnapshotTimelineJson(String jobId, List<CameraSnapshotEntry> entries) {
        return "{\"jobId\":\"" + escapeJson(jobId) + "\",\"timeline\":" + cameraSnapshotEntryArrayJson(entries) + "}";
    }

    private String cameraSnapshotEntryArrayJson(List<CameraSnapshotEntry> entries) {
        StringBuilder json = new StringBuilder();
        json.append("[");

        boolean first = true;
        for (CameraSnapshotEntry entry : entries) {
            if (!first) {
                json.append(",");
            }

            String cameraJobKey = entry.cameraJobKey();

            json.append("{")
                    .append("\"id\":").append(nullableLong(entry.id())).append(",")
                    .append("\"type\":\"snapshot\",")
                    .append("\"printerId\":\"").append(escapeJson(entry.printerId())).append("\",")
                    .append("\"cameraJobId\":").append(nullableLong(entry.cameraJobId())).append(",")
                    .append("\"cameraJobKey\":\"").append(escapeJson(cameraJobKey)).append("\",")
                    .append("\"linkedPrintJobId\":").append(nullableString(entry.linkedPrintJobId())).append(",")
                    .append("\"jobId\":").append(nullableString(entry.linkedPrintJobId())).append(",")
                    .append("\"jobKey\":\"").append(escapeJson(cameraJobKey)).append("\",")
                    .append("\"snapshotPath\":\"").append(escapeJson(entry.snapshotPath())).append("\",")
                    .append("\"contentType\":\"").append(escapeJson(entry.contentType())).append("\",")
                    .append("\"sizeBytes\":").append(entry.sizeBytes()).append(",")
                    .append("\"capturedAt\":\"").append(escapeJson(entry.capturedAt().toString())).append("\",")
                    .append("\"retainedAt\":\"").append(escapeJson(entry.retainedAt().toString())).append("\",")
                    .append("\"sourceType\":").append(nullableString(entry.sourceType())).append(",")
                    .append("\"message\":").append(nullableString(entry.message())).append(",")
                    .append("\"fileDeleted\":").append(entry.fileDeleted()).append(",")
                    .append("\"deletedAt\":").append(nullableString(instantString(entry.deletedAt()))).append(",")
                    .append("\"deletionReason\":").append(nullableString(entry.deletionReason()))
                    .append("}");

            first = false;
        }

        json.append("]");
        return json.toString();
    }

    private String cameraDeltaSetGenerationResultJson(CameraDeltaSetGenerationResult result) {
        return "{"
                + "\"deltaSet\":" + cameraDeltaSetJson(result.deltaSet()) + ","
                + "\"sourceSnapshotCount\":" + result.sourceSnapshotCount() + ","
                + "\"generatedDeltaCount\":" + result.generatedDeltaCount() + ","
                + "\"skippedIntermediateSnapshotCount\":" + result.skippedIntermediateSnapshotCount()
                + "}";
    }

    private String cameraDeltaSetsJson(List<CameraDeltaSet> deltaSets) {
        StringBuilder json = new StringBuilder();
        json.append("{\"deltaSets\":[");

        boolean first = true;
        for (CameraDeltaSet deltaSet : deltaSets) {
            if (!first) {
                json.append(",");
            }

            json.append(cameraDeltaSetJson(deltaSet));
            first = false;
        }

        json.append("]}");
        return json.toString();
    }

    private String cameraDeltaSetJson(CameraDeltaSet deltaSet) {
        return "{"
                + "\"id\":" + nullableLong(deltaSet.id()) + ","
                + "\"printerId\":\"" + escapeJson(deltaSet.printerId()) + "\","
                + "\"cameraJobId\":" + deltaSet.cameraJobId() + ","
                + "\"methodName\":\"" + escapeJson(deltaSet.methodName()) + "\","
                + "\"deltaSnapshotStep\":" + deltaSet.deltaSnapshotStep() + ","
                + "\"sourceSnapshotCount\":" + deltaSet.sourceSnapshotCount() + ","
                + "\"generatedDeltaCount\":" + deltaSet.generatedDeltaCount() + ","
                + "\"createdAt\":\"" + escapeJson(deltaSet.createdAt().toString()) + "\","
                + "\"message\":" + nullableString(deltaSet.message())
                + "}";
    }

    private String cameraDeltaFramesJson(long deltaSetId, List<CameraDeltaFrame> frames) {
        StringBuilder json = new StringBuilder();
        json.append("{\"deltaSetId\":").append(deltaSetId).append(",\"frames\":[");

        boolean first = true;
        for (CameraDeltaFrame frame : frames) {
            if (!first) {
                json.append(",");
            }

            json.append("{")
                    .append("\"id\":").append(nullableLong(frame.id())).append(",")
                    .append("\"deltaSetId\":").append(frame.deltaSetId()).append(",")
                    .append("\"printerId\":\"").append(escapeJson(frame.printerId())).append("\",")
                    .append("\"cameraJobId\":").append(frame.cameraJobId()).append(",")
                    .append("\"fromSnapshotId\":").append(frame.fromSnapshotId()).append(",")
                    .append("\"toSnapshotId\":").append(frame.toSnapshotId()).append(",")
                    .append("\"fromCapturedAt\":\"").append(escapeJson(frame.fromCapturedAt().toString())).append("\",")
                    .append("\"toCapturedAt\":\"").append(escapeJson(frame.toCapturedAt().toString())).append("\",")
                    .append("\"deltaPath\":\"").append(escapeJson(frame.deltaPath())).append("\",")
                    .append("\"deltaScore\":").append(frame.deltaScore()).append(",")
                    .append("\"changedPixelRatio\":").append(frame.changedPixelRatio()).append(",")
                    .append("\"averagePixelDelta\":").append(frame.averagePixelDelta()).append(",")
                    .append("\"createdAt\":\"").append(escapeJson(frame.createdAt().toString())).append("\"")
                    .append("}");

            first = false;
        }

        json.append("]}");
        return json.toString();
    }

    private String cameraCalculationRunsJson(long deltaSetId, List<CameraCalculationRun> runs) {
        StringBuilder json = new StringBuilder();
        json.append("{\"deltaSetId\":").append(deltaSetId).append(",\"calculationRuns\":[");

        boolean first = true;
        for (CameraCalculationRun run : runs) {
            if (!first) {
                json.append(",");
            }

            json.append(cameraCalculationRunJson(run));
            first = false;
        }

        json.append("]}");
        return json.toString();
    }

    private String cameraCalculationRunJson(CameraCalculationRun run) {
        return "{"
                + "\"id\":" + nullableLong(run.id()) + ","
                + "\"printerId\":\"" + escapeJson(run.printerId()) + "\","
                + "\"cameraJobId\":" + run.cameraJobId() + ","
                + "\"deltaSetId\":" + run.deltaSetId() + ","
                + "\"methodName\":\"" + escapeJson(run.methodName()) + "\","
                + "\"engineName\":\"" + escapeJson(run.engineName()) + "\","
                + "\"algorithmVariant\":" + nullableString(run.algorithmVariant()) + ","
                + "\"engineVersion\":" + nullableString(run.engineVersion()) + ","
                + "\"executionDurationMs\":" + nullableLong(run.executionDurationMs()) + ","
                + "\"engineStatus\":\"" + escapeJson(run.engineStatus()) + "\","
                + "\"parameterJson\":\"" + escapeJson(run.parameterJson()) + "\","
                + "\"createdAt\":\"" + escapeJson(run.createdAt().toString()) + "\","
                + "\"resultCount\":" + run.resultCount() + ","
                + "\"message\":" + nullableString(run.message())
                + "}";
    }

    private String cameraCalculationRunComparisonJson(CameraCalculationRunComparison comparison) {
        StringBuilder json = new StringBuilder();
        json.append("{")
                .append("\"left\":").append(cameraCalculationRunSummaryJson(comparison.left())).append(",")
                .append("\"right\":").append(cameraCalculationRunSummaryJson(comparison.right())).append(",")
                .append("\"comparedFrameCount\":").append(comparison.comparedFrameCount()).append(",")
                .append("\"suspectedMismatchCount\":").append(comparison.suspectedMismatchCount()).append(",")
                .append("\"averageAbsoluteConfidenceDifference\":")
                .append(comparison.averageAbsoluteConfidenceDifference()).append(",")
                .append("\"frames\":[");

        boolean first = true;
        for (CameraCalculationComparisonFrame frame : comparison.frames()) {
            if (!first) {
                json.append(",");
            }

            json.append("{")
                    .append("\"deltaFrameId\":").append(frame.deltaFrameId()).append(",")
                    .append("\"leftResultId\":").append(nullableLong(frame.leftResultId())).append(",")
                    .append("\"rightResultId\":").append(nullableLong(frame.rightResultId())).append(",")
                    .append("\"leftConfidence\":").append(nullableDouble(frame.leftConfidence())).append(",")
                    .append("\"rightConfidence\":").append(nullableDouble(frame.rightConfidence())).append(",")
                    .append("\"confidenceDifference\":").append(nullableDouble(frame.confidenceDifference())).append(",")
                    .append("\"leftSuspected\":").append(nullableBoolean(frame.leftSuspected())).append(",")
                    .append("\"rightSuspected\":").append(nullableBoolean(frame.rightSuspected())).append(",")
                    .append("\"suspectedMismatch\":").append(frame.suspectedMismatch()).append(",")
                    .append("\"leftReasonCodes\":").append(nullableString(frame.leftReasonCodes())).append(",")
                    .append("\"rightReasonCodes\":").append(nullableString(frame.rightReasonCodes()))
                    .append("}");
            first = false;
        }

        json.append("]}");
        return json.toString();
    }

    private String cameraCalculationRunSummaryJson(CameraCalculationRunSummary summary) {
        return "{"
                + "\"run\":" + cameraCalculationRunJson(summary.run()) + ","
                + "\"resultCount\":" + summary.resultCount() + ","
                + "\"suspectedCount\":" + summary.suspectedCount() + ","
                + "\"averageConfidence\":" + summary.averageConfidence() + ","
                + "\"resultsPerSecond\":" + nullableDouble(summary.resultsPerSecond()) + ","
                + "\"averageMillisecondsPerFrame\":" + nullableDouble(summary.averageMillisecondsPerFrame())
                + "}";
    }

    private String cameraCalculationResultsJson(long calculationRunId, List<CameraCalculationResult> results) {
        StringBuilder json = new StringBuilder();
        json.append("{\"calculationRunId\":").append(calculationRunId).append(",\"results\":[");

        boolean first = true;
        for (CameraCalculationResult result : results) {
            if (!first) {
                json.append(",");
            }

            json.append("{")
                    .append("\"id\":").append(nullableLong(result.id())).append(",")
                    .append("\"calculationRunId\":").append(result.calculationRunId()).append(",")
                    .append("\"deltaFrameId\":").append(result.deltaFrameId()).append(",")
                    .append("\"confidence\":").append(result.confidence()).append(",")
                    .append("\"suspected\":").append(result.suspected()).append(",")
                    .append("\"reasonCodes\":").append(nullableString(result.reasonCodes())).append(",")
                    .append("\"message\":").append(nullableString(result.message())).append(",")
                    .append("\"createdAt\":\"").append(escapeJson(result.createdAt().toString())).append("\"")
                    .append("}");

            first = false;
        }

        json.append("]}");
        return json.toString();
    }

    private String cameraAnalysisTraceJson(long calculationRunId, List<CameraAnalysisTraceRow> rows) {
        StringBuilder json = new StringBuilder();
        json.append("{\"calculationRunId\":").append(calculationRunId).append(",\"trace\":[");

        boolean first = true;
        for (CameraAnalysisTraceRow row : rows) {
            if (!first) {
                json.append(",");
            }

            json.append("{")
                    .append("\"cameraJobId\":").append(row.cameraJobId()).append(",")
                    .append("\"deltaSetId\":").append(row.deltaSetId()).append(",")
                    .append("\"deltaFrameId\":").append(row.deltaFrameId()).append(",")
                    .append("\"calculationRunId\":").append(row.calculationRunId()).append(",")
                    .append("\"calculationResultId\":").append(row.calculationResultId()).append(",")
                    .append("\"fromSnapshotPath\":\"").append(escapeJson(row.fromSnapshotPath())).append("\",")
                    .append("\"toSnapshotPath\":\"").append(escapeJson(row.toSnapshotPath())).append("\",")
                    .append("\"deltaPath\":\"").append(escapeJson(row.deltaPath())).append("\",")
                    .append("\"confidence\":").append(row.confidence()).append(",")
                    .append("\"suspected\":").append(row.suspected()).append(",")
                    .append("\"reasonCodes\":").append(nullableString(row.reasonCodes())).append(",")
                    .append("\"message\":").append(nullableString(row.message())).append(",")
                    .append("\"createdAt\":\"").append(escapeJson(row.createdAt().toString())).append("\"")
                    .append("}");

            first = false;
        }

        json.append("]}");
        return json.toString();
    }

    private String cameraSnapshotDeletionReportJson(CameraSnapshotDeletionReport report) {
        StringBuilder failedFiles = new StringBuilder();
        failedFiles.append("[");

        boolean first = true;
        for (String failedFile : report.failedFiles()) {
            if (!first) {
                failedFiles.append(",");
            }

            failedFiles.append("\"").append(escapeJson(failedFile)).append("\"");
            first = false;
        }

        failedFiles.append("]");
        return "{"
                + "\"jobId\":\"" + escapeJson(report.jobId()) + "\","
                + "\"deletedFiles\":" + report.deletedFiles() + ","
                + "\"deletedBytes\":" + report.deletedBytes() + ","
                + "\"deletedMetadataRows\":" + report.deletedMetadataRows() + ","
                + "\"failedFiles\":" + failedFiles + ","
                + "\"message\":\"" + escapeJson(report.message()) + "\""
                + "}";
    }

    private String cameraSnapshotPurgeReportJson(CameraSnapshotPurgeReport report) {
        return "{"
                + "\"printerId\":\"" + escapeJson(report.printerId()) + "\","
                + "\"cameraJobId\":" + report.cameraJobId() + ","
                + "\"totalSnapshotCount\":" + report.totalSnapshotCount() + ","
                + "\"keptSnapshotCount\":" + report.keptSnapshotCount() + ","
                + "\"purgeCandidateCount\":" + report.purgeCandidateCount() + ","
                + "\"deletedSnapshotCount\":" + report.deletedSnapshotCount() + ","
                + "\"alreadyDeletedSnapshotCount\":" + report.alreadyDeletedSnapshotCount() + ","
                + "\"failedSnapshotCount\":" + report.failedSnapshotCount() + ","
                + "\"retentionSnapshotCount\":" + report.retentionSnapshotCount() + ","
                + "\"purgeRetentionFrequency\":" + report.purgeRetentionFrequency() + ","
                + "\"deletedSnapshotIds\":" + longsJson(report.deletedSnapshotIds()) + ","
                + "\"failedSnapshotIds\":" + longsJson(report.failedSnapshotIds()) + ","
                + "\"message\":\"" + escapeJson(report.message()) + "\""
                + "}";
    }

    private static String longsJson(List<Long> values) {
        StringBuilder json = new StringBuilder();
        json.append("[");

        boolean first = true;
        for (Long value : values) {
            if (!first) {
                json.append(",");
            }
            json.append(value);
            first = false;
        }

        json.append("]");
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

    private String globalMonitoringSnapshotJson(GlobalMonitoringSnapshot snapshot) {
        StringBuilder json = new StringBuilder();
        json.append("{")
                .append("\"generatedAt\":\"").append(escapeJson(snapshot.generatedAt().toString())).append("\",")
                .append("\"summary\":").append(globalMonitoringSummaryJson(snapshot.summary())).append(",")
                .append("\"printers\":[");

        boolean first = true;
        for (GlobalMonitoringSnapshot.PrinterRuntime printer : snapshot.printers()) {
            if (!first) {
                json.append(",");
            }
            json.append(globalMonitoringPrinterJson(printer));
            first = false;
        }

        json.append("],\"activeJobs\":[");
        first = true;
        for (PrintJob job : snapshot.activeJobs()) {
            if (!first) {
                json.append(",");
            }
            json.append(printJobJson(job));
            first = false;
        }

        json.append("],\"activeUploads\":[");
        first = true;
        for (SdCardUploadService.UploadProgress upload : snapshot.activeUploads()) {
            if (!first) {
                json.append(",");
            }
            json.append(sdCardUploadProgressJson(upload));
            first = false;
        }

        json.append("]}");
        return json.toString();
    }

    private String globalMonitoringSummaryJson(GlobalMonitoringSnapshot.Summary summary) {
        return "{"
                + "\"totalPrinters\":" + summary.totalPrinters() + ","
                + "\"enabledPrinters\":" + summary.enabledPrinters() + ","
                + "\"disabledPrinters\":" + summary.disabledPrinters() + ","
                + "\"busyPrinters\":" + summary.busyPrinters() + ","
                + "\"errorPrinters\":" + summary.errorPrinters() + ","
                + "\"activeJobs\":" + summary.activeJobs() + ","
                + "\"activeUploads\":" + summary.activeUploads()
                + "}";
    }

    private String globalMonitoringPrinterJson(GlobalMonitoringSnapshot.PrinterRuntime runtime) {
        PrinterRuntimeNode node = runtime.printer();

        return "{"
                + "\"id\":\"" + escapeJson(node.id()) + "\","
                + "\"displayName\":\"" + escapeJson(node.displayName()) + "\","
                + "\"name\":\"" + escapeJson(node.displayName()) + "\","
                + "\"portName\":\"" + escapeJson(node.portName()) + "\","
                + "\"mode\":\"" + escapeJson(node.mode()) + "\","
                + "\"serialPortKind\":\"" + escapeJson(SerialPortGuidance.kind(node.mode(), node.portName())) + "\","
                + "\"stableSerialPath\":" + SerialPortGuidance.stable(node.mode(), node.portName()) + ","
                + "\"serialPathWarning\":"
                + nullableString(SerialPortGuidance.warning(node.mode(), node.portName())) + ","
                + "\"enabled\":" + node.enabled() + ","
                + "\"state\":\"" + escapeJson(runtime.state()) + "\","
                + "\"busy\":" + runtime.busy() + ","
                + "\"activeJobId\":" + nullableString(runtime.activeJobId()) + ","
                + "\"errorMessage\":" + nullableString(runtime.errorMessage()) + ","
                + "\"serialFailureType\":"
                + nullableString(runtime.serialFailureType() == null ? null : runtime.serialFailureType().name()) + ","
                + "\"updatedAt\":"
                + nullableString(runtime.updatedAt() == null ? null : runtime.updatedAt().toString())
                + "}";
    }

    private String printerJson(PrinterRuntimeNode node) {
        PrinterSnapshot snapshot = stateCache.findByPrinterId(node.id()).orElse(null);

        return "{"
                + "\"id\":\"" + escapeJson(node.id()) + "\","
                + "\"displayName\":\"" + escapeJson(node.displayName()) + "\","
                + "\"name\":\"" + escapeJson(node.displayName()) + "\","
                + "\"portName\":\"" + escapeJson(node.portName()) + "\","
                + "\"mode\":\"" + escapeJson(node.mode()) + "\","
                + "\"serialPortKind\":\"" + escapeJson(SerialPortGuidance.kind(node.mode(), node.portName())) + "\","
                + "\"stableSerialPath\":" + SerialPortGuidance.stable(node.mode(), node.portName()) + ","
                + "\"serialPathWarning\":"
                + nullableString(SerialPortGuidance.warning(node.mode(), node.portName())) + ","
                + "\"enabled\":" + node.enabled() + ","
                + "\"state\":\"" + (snapshot == null ? "UNKNOWN" : snapshot.state()) + "\","
                + "\"hotendTemperature\":" + nullableNumber(snapshot == null ? null : snapshot.hotendTemperature())
                + ","
                + "\"bedTemperature\":" + nullableNumber(snapshot == null ? null : snapshot.bedTemperature()) + ","
                + "\"lastResponse\":" + nullableString(snapshot == null ? null : snapshot.lastResponse()) + ","
                + "\"errorMessage\":" + nullableString(snapshot == null ? null : snapshot.errorMessage()) + ","
                + "\"serialFailureType\":"
                + nullableString(snapshot == null || snapshot.serialFailureType() == null
                        ? null
                        : snapshot.serialFailureType().name())
                + ","
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
                    + "\"serialFailureType\":null,"
                    + "\"updatedAt\":null"
                    + "}";
        }

        return "{"
                + "\"state\":\"" + snapshot.state() + "\","
                + "\"hotendTemperature\":" + nullableNumber(snapshot.hotendTemperature()) + ","
                + "\"bedTemperature\":" + nullableNumber(snapshot.bedTemperature()) + ","
                + "\"lastResponse\":" + nullableString(snapshot.lastResponse()) + ","
                + "\"errorMessage\":" + nullableString(snapshot.errorMessage()) + ","
                + "\"serialFailureType\":"
                + nullableString(snapshot.serialFailureType() == null ? null : snapshot.serialFailureType().name())
                + ","
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
        Object cachedBody = exchange.getAttribute("cachedBody");
        if (cachedBody instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }

        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private byte[] readBodyBytes(HttpExchange exchange) throws IOException {
        Object cachedBody = exchange.getAttribute("cachedBody");
        if (cachedBody instanceof byte[] bytes) {
            return bytes;
        }

        return exchange.getRequestBody().readAllBytes();
    }

    private String errorJson(String message) {
        return "{\"error\":" + nullableString(message) + "}";
    }

    private String confirmationRequiredJson(DangerousAction action) {
        return "{"
                + "\"error\":\"confirmation_required\","
                + "\"requiredConfirmation\":" + nullableString(action.name())
                + "}";
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

    private String nullableDouble(Double value) {
        if (value == null) {
            return "null";
        }

        return String.valueOf(value);
    }

    private String nullableBoolean(Boolean value) {
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

    private String instantString(Instant instant) {
        return instant == null ? null : instant.toString();
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

    private long parsePositiveLong(String value, String fieldName) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0L) {
                throw new IllegalArgumentException(fieldName + " must be greater than zero");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(fieldName + " must be a number");
        }
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

    private LocalRole optionalJsonLocalRole(String body, String fieldName, LocalRole fallback) {
        String value = optionalJsonString(body, fieldName, null);
        if (value == null || value.isBlank()) {
            return fallback;
        }

        return LocalRole.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private RoleProfile roleProfileFromJson(String body) {
        LocalRole role = optionalJsonLocalRole(body, "role", null);
        if (role == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("role"));
        }

        return new RoleProfile(
                role,
                optionalJsonString(body, "displayName", role.displayName()),
                permissionsFromJsonBody(body),
                true);
    }

    private java.util.Set<Permission> permissionsFromJsonBody(String body) {
        Matcher matcher = Pattern.compile("\"permissions\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL).matcher(body);
        if (!matcher.find()) {
            return java.util.EnumSet.noneOf(Permission.class);
        }

        return RoleProfileStore.parsePermissions("[" + matcher.group(1) + "]");
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
                + "\"configuredMaxBatchSize\":" + progress.configuredMaxBatchSize() + ","
                + "\"configuredMinBatchSize\":" + progress.configuredMinBatchSize() + ","
                + "\"activeBatchSize\":" + progress.activeBatchSize() + ","
                + "\"batchUpgradeStep\":" + progress.batchUpgradeStep() + ","
                + "\"batchDowngradeStep\":" + progress.batchDowngradeStep() + ","
                + "\"stableLinesForUpgrade\":" + progress.stableLinesForUpgrade() + ","
                + "\"acceptedLinesSinceLastResend\":" + progress.acceptedLinesSinceLastResend() + ","
                + "\"recentResendWindowLines\":" + progress.recentResendWindowLines() + ","
                + "\"recentResendCount\":" + progress.recentResendCount() + ","
                + "\"resendThresholdForDowngrade\":" + progress.resendThresholdForDowngrade() + ","
                + "\"recoveryThresholdForMinBatch\":" + progress.recoveryThresholdForMinBatch() + ","
                + "\"recoveryCount\":" + progress.recoveryCount() + ","
                + "\"singleSendMode\":" + progress.singleSendMode() + ","
                + "\"transportMode\":" + nullableString(progress.transportMode()) + ","
                + "\"lastAdaptationReason\":" + nullableString(progress.lastAdaptationReason()) + ","
                + "\"lastAdaptationAt\":"
                + nullableString(progress.lastAdaptationAt() == null ? null : progress.lastAdaptationAt().toString())
                + ","
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
            SpaghettiChefLog.error(OperationMessages.failedToRollbackPrinterRegistration(
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
