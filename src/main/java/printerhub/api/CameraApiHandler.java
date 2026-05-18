package printerhub.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import printerhub.camera.CameraCaptureResult;
import printerhub.camera.CameraCaptureService;
import printerhub.camera.CameraSourceType;
import printerhub.camera.CameraStatus;
import printerhub.persistence.CameraEvent;
import printerhub.persistence.CameraEventStore;
import printerhub.persistence.CameraSettings;
import printerhub.persistence.CameraSnapshotMetadata;
import printerhub.persistence.CameraSnapshotMetadataStore;

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

    private final CameraCaptureService captureService;
    private final printerhub.camera.CameraSettingsService settingsService;
    private final CameraEventStore eventStore;
    private final CameraSnapshotMetadataStore snapshotMetadataStore;

    public CameraApiHandler(
            CameraCaptureService captureService,
            printerhub.camera.CameraSettingsService settingsService,
            CameraEventStore eventStore,
            CameraSnapshotMetadataStore snapshotMetadataStore) {
        this.captureService = Objects.requireNonNull(captureService, "captureService");
        this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore");
        this.snapshotMetadataStore = Objects.requireNonNull(snapshotMetadataStore, "snapshotMetadataStore");
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
            CameraCaptureResult result = captureService.capture(printerId);
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
                + jsonField("updatedAt", settings.updatedAt().toString())
                + "}";
    }

    private static String captureResultJson(CameraCaptureResult result) {
        return "{"
                + jsonField("success", result.success()) + ","
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