package spaghettichef.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import spaghettichef.camera.CameraSnapshotFile;
import spaghettichef.camera.CameraSnapshotService;
import spaghettichef.camera.CameraCaptureResult;
import spaghettichef.camera.CameraCaptureService;
import spaghettichef.camera.CameraAnalysisSessionService;
import spaghettichef.camera.CameraSourceType;
import spaghettichef.camera.CameraStatus;

import spaghettichef.camera.CameraJobService;
import spaghettichef.camera.CameraMonitoringScheduler;
import spaghettichef.camera.CameraSnapshotPurgeService;
import spaghettichef.persistence.CameraJob;
import spaghettichef.camera.ResolvedCameraSnapshotFile;
import spaghettichef.persistence.CameraAnalysisSample;
import spaghettichef.persistence.CameraAnalysisSession;
import spaghettichef.persistence.CameraEvent;
import spaghettichef.persistence.CameraEventStore;
import spaghettichef.persistence.CameraSettings;
import spaghettichef.persistence.CameraSnapshotMetadata;
import spaghettichef.persistence.CameraSnapshotMetadataStore;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class CameraApiHandler {

    private static final int DEFAULT_EVENT_LIMIT = 20;
    private static final int DEFAULT_ANALYSIS_SAMPLE_LIMIT = 200;
    private static final int MAX_ANALYSIS_SAMPLE_LIMIT = 500;

    private final CameraCaptureService captureService;
    private final spaghettichef.camera.CameraSettingsService settingsService;
    private final CameraEventStore eventStore;
    private final CameraSnapshotMetadataStore snapshotMetadataStore;
    private final CameraAnalysisSessionService analysisSessionService;
    private final CameraSnapshotService snapshotService;
    private final CameraJobService cameraJobService;
    private final CameraMonitoringScheduler cameraMonitoringScheduler;
    private final CameraSnapshotPurgeService cameraSnapshotPurgeService;

    public CameraApiHandler(
            CameraCaptureService captureService,
            spaghettichef.camera.CameraSettingsService settingsService,
            CameraEventStore eventStore,
            CameraSnapshotMetadataStore snapshotMetadataStore,
            CameraAnalysisSessionService analysisSessionService) {
        this(
                captureService,
                settingsService,
                eventStore,
                snapshotMetadataStore,
                analysisSessionService,
                new CameraJobService(),
                null,
                null);
    }

    public CameraApiHandler(
            CameraCaptureService captureService,
            spaghettichef.camera.CameraSettingsService settingsService,
            CameraEventStore eventStore,
            CameraSnapshotMetadataStore snapshotMetadataStore,
            CameraAnalysisSessionService analysisSessionService,
            CameraJobService cameraJobService,
            CameraMonitoringScheduler cameraMonitoringScheduler) {
        this(
                captureService,
                settingsService,
                eventStore,
                snapshotMetadataStore,
                analysisSessionService,
                cameraJobService,
                cameraMonitoringScheduler,
                null);
    }

    public CameraApiHandler(
            CameraCaptureService captureService,
            spaghettichef.camera.CameraSettingsService settingsService,
            CameraEventStore eventStore,
            CameraSnapshotMetadataStore snapshotMetadataStore,
            CameraAnalysisSessionService analysisSessionService,
            CameraJobService cameraJobService,
            CameraMonitoringScheduler cameraMonitoringScheduler,
            CameraSnapshotPurgeService cameraSnapshotPurgeService) {
        this.captureService = Objects.requireNonNull(captureService, "captureService");
        this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore");
        this.snapshotMetadataStore = Objects.requireNonNull(snapshotMetadataStore, "snapshotMetadataStore");
        this.analysisSessionService = Objects.requireNonNull(analysisSessionService, "analysisSessionService");
        this.cameraJobService = Objects.requireNonNull(cameraJobService, "cameraJobService");
        this.cameraMonitoringScheduler = cameraMonitoringScheduler;
        this.cameraSnapshotPurgeService = cameraSnapshotPurgeService;
        this.snapshotService = new CameraSnapshotService(this.settingsService);
    }

    public void handleStatus(HttpExchange exchange, String printerId) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendMethodNotAllowed(exchange, "GET");
            return;
        }

        try {
            CameraStatus status = captureService.status(printerId);
            sendJson(exchange, 200, statusJson(status));
        } catch (RuntimeException exception) {
            sendError(exchange, 500, "camera_status_failed", exception.getMessage());
        }
    }

    public void handleGetSettings(HttpExchange exchange, String printerId) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendMethodNotAllowed(exchange, "GET");
            return;
        }

        try {
            CameraSettings settings = settingsService.load(printerId);
            sendJson(exchange, 200, settingsJson(settings));
        } catch (RuntimeException exception) {
            sendError(exchange, 500, "camera_settings_load_failed", exception.getMessage());
        }
    }

    public void handlePutSettings(HttpExchange exchange, String printerId) throws IOException {
        if (!isMethod(exchange, "PUT")) {
            sendMethodNotAllowed(exchange, "PUT");
            return;
        }

        try {
            String body = readBody(exchange);
            CameraSettings current = settingsService.load(printerId);
            CameraSettings updated = mergeSettings(current, body);
            CameraSettings saved = settingsService.save(updated);

            sendJson(exchange, 200, settingsJson(saved));
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, "invalid_camera_settings", exception.getMessage());
        } catch (RuntimeException exception) {
            sendError(exchange, 500, "camera_settings_save_failed", exception.getMessage());
        }
    }

    public void handleCapture(HttpExchange exchange, String printerId) throws IOException {
        if (!isMethod(exchange, "POST")) {
            sendMethodNotAllowed(exchange, "POST");
            return;
        }

        try {
            CameraCaptureResult result = captureService.captureDiagnostic(printerId);
            sendJson(exchange, result.success() ? 200 : 409, captureResultJson(result));
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, "invalid_camera_capture_request", exception.getMessage());
        } catch (RuntimeException exception) {
            sendError(exchange, 500, "camera_capture_failed", exception.getMessage());
        }
    }

    public void handleLatestSnapshot(HttpExchange exchange, String printerId) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendMethodNotAllowed(exchange, "GET");
            return;
        }

        try {
            Optional<CameraSnapshotMetadata> metadata = snapshotMetadataStore.findLatestByPrinterId(printerId);

            if (metadata.isEmpty()) {
                sendError(exchange, 404, "camera_snapshot_not_available", "No camera snapshot is available");
                return;
            }

            Path snapshotPath = Path.of(metadata.get().filePath());

            if (!Files.isRegularFile(snapshotPath)) {
                sendError(exchange, 404, "camera_snapshot_file_not_found", "Camera snapshot file was not found");
                return;
            }

            byte[] bytes = Files.readAllBytes(snapshotPath);
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", metadata.get().contentType());
            headers.set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, bytes.length);

            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, "invalid_camera_snapshot_request", exception.getMessage());
        } catch (RuntimeException exception) {
            sendError(exchange, 500, "camera_snapshot_failed", exception.getMessage());
        }
    }

    public void handleEvents(HttpExchange exchange, String printerId) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendMethodNotAllowed(exchange, "GET");
            return;
        }

        try {
            List<CameraEvent> events = eventStore.findRecentByPrinterId(printerId, DEFAULT_EVENT_LIMIT);
            sendJson(exchange, 200, eventsJson(events));
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, "invalid_camera_events_request", exception.getMessage());
        } catch (RuntimeException exception) {
            sendError(exchange, 500, "camera_events_failed", exception.getMessage());
        }
    }

    public void handleAnalysisSessions(HttpExchange exchange, String printerId) throws IOException {
        try {
            if (isMethod(exchange, "POST")) {
                CameraAnalysisSession session = analysisSessionService.start(printerId);
                sendJson(exchange, 201, sessionJson(session));
                return;
            }

            if (isMethod(exchange, "GET")) {
                sendJson(exchange, 200, sessionsJson(analysisSessionService.list(printerId)));
                return;
            }

            sendMethodNotAllowed(exchange, "GET, POST");
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, "invalid_camera_analysis_session_request", exception.getMessage());
        } catch (RuntimeException exception) {
            sendError(exchange, 500, "camera_analysis_session_failed", exception.getMessage());
        }
    }

    public void handleAnalysisSession(HttpExchange exchange, String printerId, String sessionId) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendMethodNotAllowed(exchange, "GET");
            return;
        }

        try {
            sendJson(exchange, 200, sessionJson(analysisSessionService.find(printerId, sessionId)));
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 404, "camera_analysis_session_not_found", exception.getMessage());
        } catch (RuntimeException exception) {
            sendError(exchange, 500, "camera_analysis_session_failed", exception.getMessage());
        }
    }

    public void handleStopAnalysisSession(HttpExchange exchange, String printerId, String sessionId)
            throws IOException {
        if (!isMethod(exchange, "POST")) {
            sendMethodNotAllowed(exchange, "POST");
            return;
        }

        try {
            sendJson(exchange, 200, sessionJson(analysisSessionService.stop(printerId, sessionId)));
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 404, "camera_analysis_session_not_found", exception.getMessage());
        } catch (RuntimeException exception) {
            sendError(exchange, 500, "camera_analysis_session_stop_failed", exception.getMessage());
        }
    }

    public void handleAnalysisSessionSamples(HttpExchange exchange, String printerId, String sessionId)
            throws IOException {
        try {
            if (isMethod(exchange, "GET")) {
                int limit = queryParameter(exchange, "limit")
                        .map(CameraApiHandler::parsePositiveInteger)
                        .orElse(DEFAULT_ANALYSIS_SAMPLE_LIMIT);
                sendJson(exchange, 200, samplesJson(analysisSessionService.recentSamples(
                        printerId,
                        sessionId,
                        Math.min(limit, MAX_ANALYSIS_SAMPLE_LIMIT))));
                return;
            }

            if (isMethod(exchange, "POST")) {
                sendJson(exchange, 201, sampleJson(analysisSessionService.captureSample(sessionId, printerId)));
                return;
            }

            sendMethodNotAllowed(exchange, "GET, POST");
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 404, "camera_analysis_session_not_found", exception.getMessage());
        } catch (RuntimeException exception) {
            sendError(exchange, 500, "camera_analysis_samples_failed", exception.getMessage());
        }
    }

    public void handleSnapshots(HttpExchange exchange, String printerId) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendMethodNotAllowed(exchange, "GET");
            return;
        }

        try {
            Optional<Instant> from = queryParameter(exchange, "from").map(CameraApiHandler::parseInstant);
            Optional<Instant> to = queryParameter(exchange, "to").map(CameraApiHandler::parseInstant);

            sendJson(exchange, 200, snapshotFilesJson(snapshotService.list(printerId, from, to)));
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, "invalid_camera_snapshot_request", exception.getMessage());
        } catch (RuntimeException exception) {
            sendError(exchange, 500, "camera_snapshot_failed", exception.getMessage());
        }
    }

    public void handleSnapshotFile(HttpExchange exchange, String printerId, String fileId) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendMethodNotAllowed(exchange, "GET");
            return;
        }

        try {
            Optional<ResolvedCameraSnapshotFile> snapshotFile = snapshotService.resolve(printerId, fileId);

            if (snapshotFile.isEmpty()) {
                sendError(exchange, 404, "camera_snapshot_file_not_found", "Camera snapshot file was not found");
                return;
            }

            byte[] bytes = Files.readAllBytes(snapshotFile.get().path());
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", snapshotFile.get().contentType());
            headers.set("Cache-Control", "no-store");
            headers.set("Last-Modified", snapshotFile.get().modifiedAt().toString());
            exchange.sendResponseHeaders(200, bytes.length);

            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, "invalid_camera_snapshot_file_request", exception.getMessage());
        } catch (RuntimeException exception) {
            sendError(exchange, 500, "camera_snapshot_file_failed", exception.getMessage());
        }
    }

    public void handleStartCameraJob(HttpExchange exchange, String printerId) throws IOException {
        if (!isMethod(exchange, "POST")) {
            sendMethodNotAllowed(exchange, "POST");
            return;
        }

        try {
            CameraSettings settings = settingsService.load(printerId);

            if (!settings.enabled()) {
                sendError(exchange, 409, "camera_disabled", "Camera monitoring is disabled for this printer");
                return;
            }

            CameraJob job = cameraJobService.start(settings);

            if (cameraMonitoringScheduler != null) {
                cameraMonitoringScheduler.startMonitoring(printerId, job.requireId(), settings.captureIntervalSeconds());
            }

            Optional<CameraSnapshotMetadata> latestSnapshot = snapshotMetadataStore.findLatestByPrinterId(printerId);

            sendJson(exchange, 200, activeCameraJobJson(
                    printerId,
                    Optional.of(job),
                    cameraMonitoringScheduler != null && cameraMonitoringScheduler.isMonitoring(printerId),
                    latestSnapshot));
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, "invalid_camera_job_start_request", exception.getMessage());
        } catch (RuntimeException exception) {
            sendError(exchange, 500, "camera_job_start_failed", exception.getMessage());
        }
    }

    public void handleStopCameraJob(HttpExchange exchange, String printerId) throws IOException {
        if (!isMethod(exchange, "POST")) {
            sendMethodNotAllowed(exchange, "POST");
            return;
        }

        try {
            CameraSettings settings = settingsService.load(printerId);
            if (cameraMonitoringScheduler != null) {
                cameraMonitoringScheduler.stopMonitoring(printerId);
            }

            Optional<CameraJob> stopped = cameraJobService.completeActive(
                    printerId,
                    "Camera job stopped from dashboard");
            if (stopped.isPresent() && settings.purgeAutomatically() && cameraSnapshotPurgeService != null) {
                cameraSnapshotPurgeService.purge(
                        printerId,
                        stopped.get().requireId(),
                        settings.retentionSnapshotCount(),
                        settings.purgeRetentionFrequency(),
                        "automatic snapshot purge after camera job stop");
            }

            Optional<CameraSnapshotMetadata> latestSnapshot = snapshotMetadataStore.findLatestByPrinterId(printerId);

            sendJson(exchange, 200, activeCameraJobJson(
                    printerId,
                    stopped,
                    false,
                    latestSnapshot));
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, "invalid_camera_job_stop_request", exception.getMessage());
        } catch (RuntimeException exception) {
            sendError(exchange, 500, "camera_job_stop_failed", exception.getMessage());
        }
    }

    public void handleActiveCameraJob(HttpExchange exchange, String printerId) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendMethodNotAllowed(exchange, "GET");
            return;
        }

        try {
            Optional<CameraJob> activeJob = cameraJobService.findActive(printerId);
            Optional<CameraSnapshotMetadata> latestSnapshot = snapshotMetadataStore.findLatestByPrinterId(printerId);
            boolean monitoring = cameraMonitoringScheduler != null && cameraMonitoringScheduler.isMonitoring(printerId);

            sendJson(exchange, 200, activeCameraJobJson(
                    printerId,
                    activeJob,
                    monitoring,
                    latestSnapshot));
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, "invalid_camera_job_status_request", exception.getMessage());
        } catch (RuntimeException exception) {
            sendError(exchange, 500, "camera_job_status_failed", exception.getMessage());
        }
    }

    private static String activeCameraJobJson(
            String printerId,
            Optional<CameraJob> cameraJob,
            boolean monitoring,
            Optional<CameraSnapshotMetadata> latestSnapshot) {
        boolean runningJob = cameraJob
                .map(job -> "RUNNING".equalsIgnoreCase(job.state().name()))
                .orElse(false);

        StringBuilder json = new StringBuilder();

        json.append("{");
        json.append(jsonField("printerId", printerId)).append(",");
        json.append(jsonField("active", runningJob)).append(",");
        json.append(jsonField("monitoring", monitoring)).append(",");

        if (cameraJob.isPresent()) {
            CameraJob job = cameraJob.get();
            json.append(jsonField("jobId", Long.toString(job.requireId()))).append(",");
            json.append(jsonField("state", job.state().name())).append(",");
            json.append(jsonField("linkedPrintJobId", job.linkedPrintJobId().orElse(null))).append(",");
            json.append(jsonField("analysisSessionId", job.analysisSessionId().orElse(null))).append(",");
            json.append(jsonField("startedAt", job.startedAt().toString())).append(",");
            json.append(jsonField("stoppedAt", job.stoppedAt().map(Instant::toString).orElse(null))).append(",");
            json.append(jsonField("captureIntervalSeconds", job.captureIntervalSeconds())).append(",");
            json.append(jsonField("retainedSnapshots", job.retainedSnapshots())).append(",");
            json.append(jsonField("sourceType", job.sourceType())).append(",");
            json.append(jsonField("sourceDescription", job.sourceDescription().orElse(null))).append(",");
            json.append(jsonField("snapshotDirectory", job.snapshotDirectory())).append(",");
            json.append(jsonField("message", job.message().orElse(null))).append(",");
        } else {
            json.append(jsonField("jobId", (String) null)).append(",");
            json.append(jsonField("state", "IDLE")).append(",");
            json.append(jsonField("linkedPrintJobId", (String) null)).append(",");
            json.append(jsonField("analysisSessionId", (String) null)).append(",");
            json.append(jsonField("startedAt", (String) null)).append(",");
            json.append(jsonField("stoppedAt", (String) null)).append(",");
            json.append(jsonField("captureIntervalSeconds", 0)).append(",");
            json.append(jsonField("retainedSnapshots", 0)).append(",");
            json.append(jsonField("sourceType", (String) null)).append(",");
            json.append(jsonField("sourceDescription", (String) null)).append(",");
            json.append(jsonField("snapshotDirectory", (String) null)).append(",");
            json.append(jsonField("message", (String) null)).append(",");
        }

        if (latestSnapshot.isPresent()) {
            CameraSnapshotMetadata snapshot = latestSnapshot.get();
            String version = snapshot.id()
                    .map(String::valueOf)
                    .orElse(snapshot.capturedAt().toString());

            json.append(jsonField("latestSnapshotAvailable", true)).append(",");
            json.append(jsonField("latestSnapshotId", snapshot.id().map(String::valueOf).orElse(null))).append(",");
            json.append(jsonField("latestSnapshotVersion", version)).append(",");
            json.append(jsonField("latestCaptureAt", snapshot.capturedAt().toString())).append(",");
            json.append(jsonField("latestContentType", snapshot.contentType())).append(",");
            json.append(jsonField("latestWidth", snapshot.width().isPresent() ? snapshot.width().getAsInt() : 0))
                    .append(",");
            json.append(jsonField("latestHeight", snapshot.height().isPresent() ? snapshot.height().getAsInt() : 0));
        } else {
            json.append(jsonField("latestSnapshotAvailable", false)).append(",");
            json.append(jsonField("latestSnapshotId", (String) null)).append(",");
            json.append(jsonField("latestSnapshotVersion", (String) null)).append(",");
            json.append(jsonField("latestCaptureAt", (String) null)).append(",");
            json.append(jsonField("latestContentType", (String) null)).append(",");
            json.append(jsonField("latestWidth", 0)).append(",");
            json.append(jsonField("latestHeight", 0));
        }

        json.append("}");
        return json.toString();
    }

    private CameraSettings mergeSettings(CameraSettings current, String body) {
        boolean enabled = readBooleanField(body, "enabled").orElse(current.enabled());

        CameraSourceType sourceType = readStringField(body, "sourceType")
                .map(CameraSourceType::fromWireValue)
                .orElse(current.sourceType());

        if (!enabled) {
            sourceType = CameraSourceType.DISABLED;
        }

        if (enabled && sourceType == CameraSourceType.DISABLED) {
            throw new IllegalArgumentException("enabled camera settings must not use source type DISABLED");
        }

        String sourceValue = readStringField(body, "sourceValue")
                .orElse(current.sourceValue().orElse(null));

        int captureIntervalSeconds = readIntegerField(body, "captureIntervalSeconds")
                .orElse(current.captureIntervalSeconds());

        int retentionSnapshotCount = readIntegerField(body, "retentionSnapshotCount")
                .orElse(current.retentionSnapshotCount());

        boolean analysisEnabled = readBooleanField(body, "analysisEnabled")
                .orElse(current.analysisEnabled());

        boolean safetyEnabled = readBooleanField(body, "safetyEnabled")
                .orElse(current.safetyEnabled());

        boolean pauseOnConfirmedSpaghetti = readBooleanField(body, "pauseOnConfirmedSpaghetti")
                .orElse(current.pauseOnConfirmedSpaghetti());

        double confidenceThreshold = readDoubleField(body, "confidenceThreshold")
                .orElse(current.confidenceThreshold());

        int confirmationsRequired = readIntegerField(body, "confirmationsRequired")
                .orElse(current.confirmationsRequired());

        String ffmpegCommand = readStringField(body, "ffmpegCommand")
                .orElse(current.ffmpegCommand());

        String ffmpegInputFormat = readStringField(body, "ffmpegInputFormat")
                .orElse(current.ffmpegInputFormat().orElse(null));

        String ffmpegVideoSize = readStringField(body, "ffmpegVideoSize")
                .orElse(current.ffmpegVideoSize().orElse(null));

        int ffmpegTimeoutMs = readIntegerField(body, "ffmpegTimeoutMs")
                .orElse(current.ffmpegTimeoutMs());

        int ffmpegJpegQuality = readIntegerField(body, "ffmpegJpegQuality")
                .orElse(current.ffmpegJpegQuality());

        String storageDirectory = readStringField(body, "storageDirectory")
                .orElse(current.storageDirectory());

        boolean diagnosticLoggingEnabled = readBooleanField(body, "diagnosticLoggingEnabled")
                .orElse(current.diagnosticLoggingEnabled());

        boolean purgeAutomatically = readBooleanField(body, "purgeAutomatically")
                .orElse(current.purgeAutomatically());

        int purgeRetentionFrequency = readIntegerField(body, "purgeRetentionFrequency")
                .orElse(current.purgeRetentionFrequency());

        return new CameraSettings(
                current.printerId(),
                enabled,
                sourceType,
                sourceValue,
                captureIntervalSeconds,
                retentionSnapshotCount,
                analysisEnabled,
                safetyEnabled,
                pauseOnConfirmedSpaghetti,
                confidenceThreshold,
                confirmationsRequired,
                ffmpegCommand,
                ffmpegInputFormat,
                ffmpegVideoSize,
                ffmpegTimeoutMs,
                ffmpegJpegQuality,
                storageDirectory,
                diagnosticLoggingEnabled,
                purgeAutomatically,
                purgeRetentionFrequency,
                Instant.now());
    }

    private static String statusJson(CameraStatus status) {
        return "{"
                + jsonField("printerId", status.printerId()) + ","
                + jsonField("enabled", status.enabled()) + ","
                + jsonField("available", status.available()) + ","
                + jsonField("sourceType", status.sourceType().wireValue()) + ","
                + jsonField("sourceValue", status.sourceValue().orElse(null)) + ","
                + jsonField("sourceDescription", status.sourceDescription().orElse(null)) + ","
                + jsonField("lastCaptureAt", status.lastCaptureAt().map(Instant::toString).orElse(null)) + ","
                + jsonField("lastError", status.lastError().orElse(null))
                + "}";
    }

    private static String settingsJson(CameraSettings settings) {
        return "{"
                + jsonField("printerId", settings.printerId()) + ","
                + jsonField("enabled", settings.enabled()) + ","
                + jsonField("sourceType", settings.sourceType().wireValue()) + ","
                + jsonField("sourceValue", settings.sourceValue().orElse(null)) + ","
                + jsonField("captureIntervalSeconds", settings.captureIntervalSeconds()) + ","
                + jsonField("retentionSnapshotCount", settings.retentionSnapshotCount()) + ","
                + jsonField("analysisEnabled", settings.analysisEnabled()) + ","
                + jsonField("safetyEnabled", settings.safetyEnabled()) + ","
                + jsonField("pauseOnConfirmedSpaghetti", settings.pauseOnConfirmedSpaghetti()) + ","
                + jsonField("confidenceThreshold", settings.confidenceThreshold()) + ","
                + jsonField("confirmationsRequired", settings.confirmationsRequired()) + ","
                + jsonField("ffmpegCommand", settings.ffmpegCommand()) + ","
                + jsonField("ffmpegInputFormat", settings.ffmpegInputFormat().orElse(null)) + ","
                + jsonField("ffmpegVideoSize", settings.ffmpegVideoSize().orElse(null)) + ","
                + jsonField("ffmpegTimeoutMs", settings.ffmpegTimeoutMs()) + ","
                + jsonField("ffmpegJpegQuality", settings.ffmpegJpegQuality()) + ","
                + jsonField("storageDirectory", settings.storageDirectory()) + ","
                + jsonField("diagnosticLoggingEnabled", settings.diagnosticLoggingEnabled()) + ","
                + jsonField("purgeAutomatically", settings.purgeAutomatically()) + ","
                + jsonField("purgeRetentionFrequency", settings.purgeRetentionFrequency()) + ","
                + jsonField("updatedAt", settings.updatedAt().toString())
                + "}";
    }

    private static String captureResultJson(CameraCaptureResult result) {
        return "{"
                + jsonField("success", result.success()) + ","
                + jsonField("error", result.success() ? null : "camera_capture_failed") + ","
                + jsonField("hasFrame", result.hasFrame()) + ","
                + jsonField("message", result.message().orElse(null)) + ","
                + "\"frame\":"
                + result.frame()
                        .map(frame -> "{"
                                + jsonField("printerId", frame.printerId()) + ","
                                + jsonField("capturedAt", frame.capturedAt().toString()) + ","
                                + jsonField("contentType", frame.contentType()) + ","
                                + jsonField("byteCount", frame.byteCount()) + ","
                                + jsonField("width", frame.width().isPresent() ? frame.width().getAsInt() : null) + ","
                                + jsonField("height", frame.height().isPresent() ? frame.height().getAsInt() : null)
                                + ","
                                + jsonField("sourceDescription", frame.sourceDescription().orElse(null))
                                + "}")
                        .orElse("null")
                + "}";
    }

    private static String eventsJson(List<CameraEvent> events) {
        StringBuilder builder = new StringBuilder();
        builder.append("[");

        for (int index = 0; index < events.size(); index++) {
            CameraEvent event = events.get(index);

            if (index > 0) {
                builder.append(",");
            }

            builder.append("{")
                    .append(jsonField("id", event.id().orElse(null))).append(",")
                    .append(jsonField("printerId", event.printerId())).append(",")
                    .append(jsonField("cameraJobId", event.cameraJobId().orElse(null))).append(",")
                    .append(jsonField("eventType", event.eventType())).append(",")
                    .append(jsonField("message", event.message())).append(",")
                    .append(jsonField("confidence", event.confidence().isPresent()
                            ? event.confidence().getAsDouble()
                            : null))
                    .append(",")
                    .append(jsonField("createdAt", event.createdAt().toString()))
                    .append("}");
        }

        builder.append("]");
        return builder.toString();
    }

    private static String sessionsJson(List<CameraAnalysisSession> sessions) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"sessions\":[");
        for (int index = 0; index < sessions.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            builder.append(sessionJson(sessions.get(index)));
        }
        builder.append("]}");
        return builder.toString();
    }

    private static String sessionJson(CameraAnalysisSession session) {
        return "{"
                + jsonField("id", session.id()) + ","
                + jsonField("printerId", session.printerId()) + ","
                + jsonField("state", session.state().name()) + ","
                + jsonField("startedAt", session.startedAt().map(Instant::toString).orElse(null)) + ","
                + jsonField("stoppedAt", session.stoppedAt().map(Instant::toString).orElse(null)) + ","
                + jsonField("createdAt", session.createdAt().toString()) + ","
                + jsonField("updatedAt", session.updatedAt().toString()) + ","
                + jsonField("message", session.message().orElse(null))
                + "}";
    }

    private static String samplesJson(List<CameraAnalysisSample> samples) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"samples\":[");
        for (int index = 0; index < samples.size(); index++) {
            CameraAnalysisSample sample = samples.get(index);
            if (index > 0) {
                builder.append(",");
            }
            builder.append(sampleJson(sample));
        }
        builder.append("]}");
        return builder.toString();
    }

    private static String snapshotFilesJson(List<CameraSnapshotFile> files) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"files\":[");
        for (int index = 0; index < files.size(); index++) {
            CameraSnapshotFile file = files.get(index);
            if (index > 0) {
                builder.append(",");
            }
            builder.append("{")
                    .append(jsonField("id", file.id())).append(",")
                    .append(jsonField("type", file.type())).append(",")
                    .append(jsonField("fileName", file.fileName())).append(",")
                    .append(jsonField("relativePath", file.relativePath())).append(",")
                    .append(jsonField("contentType", file.contentType())).append(",")
                    .append(jsonField("sizeBytes", file.sizeBytes())).append(",")
                    .append(jsonField("modifiedAt", file.modifiedAt().toString()))
                    .append("}");
        }
        builder.append("]}");
        return builder.toString();
    }

    private static String sampleJson(CameraAnalysisSample sample) {
        return "{"
                + jsonField("id", sample.id().orElse(null)) + ","
                + jsonField("sessionId", sample.sessionId()) + ","
                + jsonField("printerId", sample.printerId()) + ","
                + jsonField("capturedAt", sample.capturedAt().toString()) + ","
                + jsonField("analyzedAt", sample.analyzedAt().toString()) + ","
                + jsonField("latestSnapshotPath", sample.latestSnapshotPath().orElse(null)) + ","
                + jsonField("previousSnapshotPath", sample.previousSnapshotPath().orElse(null)) + ","
                + jsonField("deltaSnapshotPath", sample.deltaSnapshotPath().orElse(null)) + ","
                + jsonField("deltaScore", sample.deltaScore()) + ","
                + jsonField("changedPixelRatio", sample.changedPixelRatio()) + ","
                + jsonField("averagePixelDelta", sample.averagePixelDelta()) + ","
                + jsonField("confidence", sample.confidence()) + ","
                + jsonField("suspected", sample.suspected()) + ","
                + jsonField("reasonCodes", sample.reasonCodes().orElse(null)) + ","
                + jsonField("message", sample.message().orElse(null))
                + "}";
    }

    private static Optional<String> readStringField(String json, String fieldName) {
        String marker = "\"" + fieldName + "\"";
        int markerIndex = json.indexOf(marker);

        if (markerIndex < 0) {
            return Optional.empty();
        }

        int colonIndex = json.indexOf(':', markerIndex);
        if (colonIndex < 0) {
            return Optional.empty();
        }

        int valueStart = colonIndex + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }

        if (valueStart >= json.length()) {
            return Optional.empty();
        }

        if (json.startsWith("null", valueStart)) {
            return Optional.of("");
        }

        if (json.charAt(valueStart) != '"') {
            return Optional.empty();
        }

        StringBuilder value = new StringBuilder();
        boolean escaped = false;

        for (int index = valueStart + 1; index < json.length(); index++) {
            char character = json.charAt(index);

            if (escaped) {
                value.append(character);
                escaped = false;
                continue;
            }

            if (character == '\\') {
                escaped = true;
                continue;
            }

            if (character == '"') {
                return Optional.of(value.toString());
            }

            value.append(character);
        }

        return Optional.empty();
    }

    private static Optional<Boolean> readBooleanField(String json, String fieldName) {
        return readRawField(json, fieldName).flatMap(value -> {
            if ("true".equalsIgnoreCase(value)) {
                return Optional.of(Boolean.TRUE);
            }
            if ("false".equalsIgnoreCase(value)) {
                return Optional.of(Boolean.FALSE);
            }
            return Optional.empty();
        });
    }

    private static Optional<Integer> readIntegerField(String json, String fieldName) {
        return readRawField(json, fieldName).flatMap(value -> {
            try {
                return Optional.of(Integer.parseInt(value));
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
        });
    }

    private static Optional<Double> readDoubleField(String json, String fieldName) {
        return readRawField(json, fieldName).flatMap(value -> {
            try {
                return Optional.of(Double.parseDouble(value));
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
        });
    }

    private static Optional<String> queryParameter(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }

        String marker = name + "=";
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            if (pair.startsWith(marker)) {
                return Optional.of(java.net.URLDecoder.decode(
                        pair.substring(marker.length()),
                        StandardCharsets.UTF_8));
            }
        }

        return Optional.empty();
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException("invalid timestamp: " + value, exception);
        }
    }

    private static int parsePositiveInteger(String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid positive integer: " + value, exception);
        }

        throw new IllegalArgumentException("invalid positive integer: " + value);
    }

    private static Optional<String> readRawField(String json, String fieldName) {
        String marker = "\"" + fieldName + "\"";
        int markerIndex = json.indexOf(marker);

        if (markerIndex < 0) {
            return Optional.empty();
        }

        int colonIndex = json.indexOf(':', markerIndex);
        if (colonIndex < 0) {
            return Optional.empty();
        }

        int valueStart = colonIndex + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }

        int valueEnd = valueStart;
        while (valueEnd < json.length()) {
            char character = json.charAt(valueEnd);

            if (character == ',' || character == '}') {
                break;
            }

            valueEnd++;
        }

        if (valueStart >= valueEnd) {
            return Optional.empty();
        }

        return Optional.of(json.substring(valueStart, valueEnd).trim());
    }

    private static boolean isMethod(HttpExchange exchange, String expectedMethod) {
        return expectedMethod.equalsIgnoreCase(exchange.getRequestMethod());
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        Object cachedBody = exchange.getAttribute("cachedBody");
        if (cachedBody instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }

        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void sendMethodNotAllowed(HttpExchange exchange, String allowedMethod) throws IOException {
        exchange.getResponseHeaders().set("Allow", allowedMethod);
        sendError(exchange, 405, "method_not_allowed", "Method not allowed");
    }

    private static void sendError(HttpExchange exchange, int statusCode, String error, String message)
            throws IOException {
        sendJson(exchange, statusCode, "{"
                + jsonField("error", error) + ","
                + jsonField("message", message)
                + "}");
    }

    private static void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Cache-Control", "no-store");

        exchange.sendResponseHeaders(statusCode, bytes.length);

        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String jsonField(String name, String value) {
        return "\"" + escapeJson(name) + "\":" + jsonString(value);
    }

    private static String jsonField(String name, boolean value) {
        return "\"" + escapeJson(name) + "\":" + value;
    }

    private static String jsonField(String name, int value) {
        return "\"" + escapeJson(name) + "\":" + value;
    }

    private static String jsonField(String name, long value) {
        return "\"" + escapeJson(name) + "\":" + value;
    }

    private static String jsonField(String name, double value) {
        return "\"" + escapeJson(name) + "\":" + value;
    }

    private static String jsonField(String name, Integer value) {
        if (value == null) {
            return "\"" + escapeJson(name) + "\":null";
        }
        return "\"" + escapeJson(name) + "\":" + value;
    }

    private static String jsonField(String name, Long value) {
        if (value == null) {
            return "\"" + escapeJson(name) + "\":null";
        }
        return "\"" + escapeJson(name) + "\":" + value;
    }

    private static String jsonField(String name, Double value) {
        if (value == null) {
            return "\"" + escapeJson(name) + "\":null";
        }
        return "\"" + escapeJson(name) + "\":" + value;
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }

        return "\"" + escapeJson(value) + "\"";
    }

    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}
