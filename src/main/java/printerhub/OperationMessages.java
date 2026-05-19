package printerhub;

import printerhub.security.LocalRole;
import printerhub.security.Permission;

public final class OperationMessages {

    private OperationMessages() {
    }

    public static final String ERROR_PREFIX = "[ERROR] ";
    public static final String INFO_PREFIX = "[INFO] ";

    public static final String RESEND_OUTSIDE_RECOVERY_WINDOW_DETAIL = "Printer requested resend for line %d but that line was outside the recoverable resend window.";

    public static final String MONITORING_SCHEDULER_MUST_NOT_BE_NULL = "monitoringScheduler must not be null";
    public static final String MONITORING_RULES_STORE_MUST_NOT_BE_NULL = "monitoringRulesStore must not be null";
    public static final String COMMAND_SERVICE_MUST_NOT_BE_NULL = "printerCommandService must not be null";

    public static final String FAILED_TO_SAVE_PRINTER_CONFIGURATION = "Failed to save printer configuration";
    public static final String FAILED_TO_LOAD_PRINTER_CONFIGURATION = "Failed to load printer configuration";
    public static final String FAILED_TO_CHECK_PRINTER_CONFIGURATION = "Failed to check printer configuration";
    public static final String FAILED_TO_DELETE_PRINTER_CONFIGURATION = "Failed to delete printer configuration";
    public static final String FAILED_TO_UPDATE_PRINTER_ENABLED_FLAG = "Failed to update printer enabled flag";

    public static final String DATABASE_INITIALIZER_MUST_NOT_BE_NULL = "databaseInitializer must not be null";
    public static final String API_SERVER_MUST_NOT_BE_NULL = "apiServer must not be null";

    public static final String NODE_MUST_NOT_BE_NULL = "node must not be null";
    public static final String STATE_CACHE_MUST_NOT_BE_NULL = "stateCache must not be null";
    public static final String SNAPSHOT_STORE_MUST_NOT_BE_NULL = "snapshotStore must not be null";
    public static final String EVENT_STORE_MUST_NOT_BE_NULL = "eventStore must not be null";
    public static final String CLOCK_MUST_NOT_BE_NULL = "clock must not be null";
    public static final String STATUS_COMMAND_MUST_NOT_BE_BLANK = "statusCommand must not be blank";
    public static final String PRINTER_REGISTRY_MUST_NOT_BE_NULL = "printerRegistry must not be null";
    public static final String PRINTER_CONFIGURATION_STORE_MUST_NOT_BE_NULL = "printerConfigurationStore must not be null";
    public static final String PRINTER_ID_MUST_NOT_BE_BLANK = "printerId must not be blank";
    public static final String SNAPSHOT_MUST_NOT_BE_NULL = "snapshot must not be null";
    public static final String PORT_MUST_BE_IN_VALID_RANGE = "port must be between 1 and 65535";
    public static final String PORT_NAME_MUST_NOT_BE_BLANK = "portName must not be blank";
    public static final String PORT_ADAPTER_MUST_NOT_BE_NULL = "portAdapter must not be null";
    public static final String COMMAND_MUST_NOT_BE_BLANK = "command must not be blank";
    public static final String PRINTER_EVENT_MUST_NOT_BE_NULL = "printer event must not be null";
    public static final String MONITORING_RULES_MUST_NOT_BE_NULL = "monitoringRules must not be null";
    public static final String INTERVAL_SECONDS_MUST_BE_GREATER_THAN_ZERO = "intervalSeconds must be greater than zero";

    public static final String POLL_INTERVAL_SECONDS_MUST_BE_GREATER_THAN_ZERO = "pollIntervalSeconds must be greater than zero";
    public static final String SNAPSHOT_MINIMUM_INTERVAL_SECONDS_MUST_NOT_BE_NEGATIVE = "snapshotMinimumIntervalSeconds must not be negative";
    public static final String TEMPERATURE_DELTA_THRESHOLD_MUST_NOT_BE_NEGATIVE = "temperatureDeltaThreshold must not be negative";
    public static final String EVENT_DEDUPLICATION_WINDOW_SECONDS_MUST_NOT_BE_NEGATIVE = "eventDeduplicationWindowSeconds must not be negative";
    public static final String ERROR_PERSISTENCE_BEHAVIOR_MUST_NOT_BE_NULL = "errorPersistenceBehavior must not be null";
    public static final String TARGET_TEMPERATURE_MUST_NOT_BE_NEGATIVE = "targetTemperature must not be negative";

    public static final String SERIAL_TRANSFER_SETTINGS_MUST_NOT_BE_NULL = "serialTransferSettings must not be null";
    public static final String LOCAL_ROLE_MUST_NOT_BE_NULL = "localRole must not be null";
    public static final String ROLE_DISPLAY_NAME_MUST_NOT_BE_BLANK = "role displayName must not be blank";
    public static final String ROLE_PROFILES_MUST_NOT_BE_NULL = "roleProfiles must not be null";
    public static final String PERMISSIONS_MUST_NOT_BE_NULL = "permissions must not be null";

    public static final String SD_UPLOAD_BATCH_SIZE_MUST_BE_IN_RANGE = "sdUploadBatchSize must be between 1 and 100";
    public static final String SD_UPLOAD_RECOVERY_WINDOW_MULTIPLIER_MUST_BE_IN_RANGE = "sdUploadRecoveryWindowMultiplier must be between 1 and 100";
    public static final String SD_UPLOAD_MAX_ERRORS_MUST_BE_IN_RANGE = "sdUploadMaxErrors must be between 1 and 1000000";
    public static final String SD_UPLOAD_MAX_CONSECUTIVE_IDENTICAL_RESENDS_MUST_BE_IN_RANGE = "sdUploadMaxConsecutiveIdenticalResends must be between 1 and 1000";
    public static final String SD_UPLOAD_MIN_PERFORMANCE_PERCENT_MUST_BE_IN_RANGE = "sdUploadMinPerformancePercent must be between 0 and 100";
    public static final String SD_UPLOAD_MAX_RETRIES_PER_LINE_MUST_BE_IN_RANGE = "sdUploadMaxRetriesPerLine must be between 1 and 100";
    public static final String FILE_STREAMING_READ_TIMEOUT_MS_MUST_BE_IN_RANGE = "fileStreamingReadTimeoutMs must be between 1 and 600000";
    public static final String FILE_STREAMING_QUIET_PERIOD_MS_MUST_BE_IN_RANGE = "fileStreamingQuietPeriodMs must be between 0 and 60000";
    public static final String FILE_STREAMING_READ_ACTIVITY_SLEEP_MS_MUST_BE_IN_RANGE = "fileStreamingReadActivitySleepMs must be between 0 and 60000";
    public static final String FILE_STREAMING_READ_IDLE_SLEEP_MS_MUST_BE_IN_RANGE = "fileStreamingReadIdleSleepMs must be between 0 and 60000";
    public static final String FILE_STREAMING_RECOVERY_REPLAY_DELAY_MS_MUST_BE_IN_RANGE = "fileStreamingRecoveryReplayDelayMs must be between 0 and 60000";

    public static final String EVENT_PRINTER_POLLED = "PRINTER_POLLED";
    public static final String EVENT_PRINTER_DISABLED = "PRINTER_DISABLED";
    public static final String EVENT_PRINTER_TIMEOUT = "PRINTER_TIMEOUT";
    public static final String EVENT_PRINTER_DISCONNECTED = "PRINTER_DISCONNECTED";
    public static final String EVENT_PRINTER_ERROR = "PRINTER_ERROR";
    public static final String EVENT_COMMAND_EXECUTED = "COMMAND_EXECUTED";
    public static final String EVENT_COMMAND_FAILED = "COMMAND_FAILED";
    public static final String EVENT_CAMERA_FRAME_CAPTURED = "CAMERA_FRAME_CAPTURED";
    public static final String EVENT_CAMERA_CAPTURE_SKIPPED = "CAMERA_CAPTURE_SKIPPED";
    public static final String EVENT_CAMERA_CAPTURE_FAILED = "CAMERA_CAPTURE_FAILED";
    public static final String EVENT_CAMERA_AVAILABLE = "CAMERA_AVAILABLE";
    public static final String EVENT_CAMERA_UNAVAILABLE = "CAMERA_UNAVAILABLE";
    public static final String EVENT_CAMERA_ANALYSIS_COMPLETED = "CAMERA_ANALYSIS_COMPLETED";
    public static final String EVENT_CAMERA_ANALYSIS_SKIPPED = "CAMERA_ANALYSIS_SKIPPED";
    public static final String EVENT_CAMERA_ANALYSIS_FAILED = "CAMERA_ANALYSIS_FAILED";
    public static final String EVENT_SPAGHETTI_SUSPECTED = "SPAGHETTI_SUSPECTED";
    public static final String EVENT_SPAGHETTI_CONFIRMED = "SPAGHETTI_CONFIRMED";
    public static final String EVENT_CAMERA_SAFETY_ACTION_SKIPPED = "CAMERA_SAFETY_ACTION_SKIPPED";
    public static final String EVENT_CAMERA_SAFETY_ACTION_SUCCEEDED = "CAMERA_SAFETY_ACTION_SUCCEEDED";
    public static final String EVENT_CAMERA_SAFETY_ACTION_FAILED = "CAMERA_SAFETY_ACTION_FAILED";
    public static final String EVENT_SD_CARD_FILES_LISTED = "SD_CARD_FILES_LISTED";
    public static final String EVENT_SD_CARD_FILE_LIST_FAILED = "SD_CARD_FILE_LIST_FAILED";

    
    public static final String CAMERA_AVAILABLE = "Camera available";
    public static final String CAMERA_UNAVAILABLE = "Camera unavailable";
    public static final String CAMERA_DISABLED = "Camera disabled";
    public static final String CAMERA_RETURNED_NO_FRAME = "Camera returned no frame";
    public static final String CAMERA_FRAME_CAPTURED = "Camera frame captured";
    public static final String CAMERA_CAPTURE_FAILED = "Camera capture failed";
    public static final String CAMERA_ANALYSIS_SKIPPED = "Camera frame analysis skipped";
    public static final String CAMERA_ANALYSIS_COMPLETED = "Camera frame analysis completed";
    public static final String CAMERA_ANALYSIS_FAILED = "Camera frame analysis failed";
    public static final String POSSIBLE_SPAGHETTI_FAILURE_DETECTED = "Possible spaghetti failure detected";
    public static final String SPAGHETTI_FAILURE_CONFIRMED = "Spaghetti failure confirmed";
    public static final String CAMERA_SAFETY_ACTION_SKIPPED = "Camera safety action skipped";
    public static final String CAMERA_SAFETY_ACTION_SUCCEEDED = "Camera safety action succeeded";
    public static final String CAMERA_SAFETY_ACTION_FAILED = "Camera safety action failed";

    public static final String PRINTER_NODE_DISABLED = "Printer node is disabled.";
    public static final String PRINTER_POLL_COMPLETED_SUCCESSFULLY = "Printer poll completed successfully.";
    public static final String PRINTER_RETURNED_ERROR_RESPONSE = "Printer returned error response.";
    public static final String UNKNOWN_PRINTER_MONITORING_ERROR = "Unknown printer monitoring error.";
    public static final String UNKNOWN_API_ERROR = "Unexpected API error.";
    public static final String INTERNAL_SERVER_ERROR = "internal_server_error";
    public static final String UNKNOWN_COMMAND_EXECUTION_ERROR = "Unknown command execution error.";

    public static final String METHOD_NOT_ALLOWED = "method_not_allowed";
    public static final String PRINTER_NOT_FOUND = "printer_not_found";
    public static final String PRINTER_ENDPOINT_NOT_FOUND = "printer_endpoint_not_found";
    public static final String DASHBOARD_RESOURCE_NOT_FOUND = "dashboard_resource_not_found";

    public static final String API_SERVER_STOPPED = "API server stopped";
    public static final String API_OPERATION_FAILED = "API operation failed";
    public static final String UNEXPECTED_API_ERROR = "Unexpected API error";

    public static final String FAILED_TO_SAVE_PRINTER_SNAPSHOT = "Failed to save printer snapshot";
    public static final String FAILED_TO_LOAD_PRINTER_SNAPSHOTS = "Failed to load printer snapshots";
    public static final String FAILED_TO_CHECK_LATEST_PRINTER_SNAPSHOT = "Failed to check latest printer snapshot";
    public static final String INVALID_STORED_PRINTER_SNAPSHOT_STATE = "Invalid stored printer snapshot state";
    public static final String INVALID_STORED_PRINTER_SNAPSHOT_TIMESTAMP = "Invalid stored printer snapshot timestamp";
    public static final String FAILED_TO_SAVE_PRINTER_EVENT = "Failed to save printer event";
    public static final String FAILED_TO_LOAD_PRINTER_EVENTS = "Failed to load printer events";

    public static final String FAILED_TO_LOAD_MONITORING_RULES = "Failed to load monitoring rules";
    public static final String FAILED_TO_SAVE_MONITORING_RULES = "Failed to save monitoring rules";
    public static final String FAILED_TO_LOAD_PRINT_FILE_SETTINGS = "Failed to load print file settings";
    public static final String FAILED_TO_SAVE_PRINT_FILE_SETTINGS = "Failed to save print file settings";
    public static final String FAILED_TO_LOAD_SERIAL_TRANSFER_SETTINGS = "Failed to load serial transfer settings";
    public static final String FAILED_TO_SAVE_SERIAL_TRANSFER_SETTINGS = "Failed to save serial transfer settings";
    public static final String FAILED_TO_LOAD_SECURITY_SETTINGS = "Failed to load security settings";
    public static final String FAILED_TO_SAVE_SECURITY_SETTINGS = "Failed to save security settings";
    public static final String FAILED_TO_LOAD_ROLE_PROFILES = "Failed to load role profiles";
    public static final String FAILED_TO_SAVE_ROLE_PROFILES = "Failed to save role profiles";

    public static final String SERIAL_CONNECTION_IS_NOT_OPEN = "Serial port is not open.";
    public static final String INTERRUPTED_WHILE_READING_RESPONSE = "Interrupted while reading response from serial port.";

    public static final String PRINTER_PORT_MUST_NOT_BE_NULL = "printerPort must not be null";
    public static final String UNKNOWN_RUNTIME_CLOSE_ERROR = "Unknown runtime close error";

    public static final String UNKNOWN_STARTUP_ERROR = "Unknown startup error";
    public static final String UPDATED_AT_MUST_NOT_BE_NULL = "updatedAt must not be null";

    public static final String DATABASE_FILE_MUST_NOT_BE_BLANK = "database file must not be blank";
    public static final String FAILED_TO_INITIALIZE_DATABASE_SCHEMA = "Failed to initialize database schema";

    public static final String TEMPERATURE_THRESHOLD_MUST_NOT_BE_NEGATIVE = "temperatureThreshold must not be negative";
    public static final String MIN_INTERVAL_SECONDS_MUST_NOT_BE_NEGATIVE = "minIntervalSeconds must not be negative";

    public static final String INVALID_PRINTER_MODE = "mode must be one of: real, sim, simulated, sim-disconnected, sim-timeout, sim-error";

    public static final String SIMULATED_PRINTER_FAILURE_RESPONSE = "Error: Simulated printer failure";

    public static final String EVENT_TYPE_MUST_NOT_BE_BLANK = "eventType must not be blank";
    public static final String CREATED_AT_MUST_NOT_BE_NULL = "createdAt must not be null";
    public static final String SHUTDOWN_SIGNAL_MUST_NOT_BE_NULL = "shutdownSignal must not be null";

    public static final String DEDUP_WINDOW_MUST_NOT_BE_NULL = "dedupWindow must not be null";
    public static final String DEDUP_WINDOW_MUST_NOT_BE_NEGATIVE = "dedupWindow must not be negative";
    public static final String EVENT_MESSAGE_MUST_NOT_BE_BLANK = "eventMessage must not be blank";
    public static final String MONITORING_EVENT_POLICY_MUST_NOT_BE_NULL = "monitoringEventPolicy must not be null";

    public static final String PRINT_JOB_STORE_MUST_NOT_BE_NULL = "printJobStore must not be null";
    public static final String PRINT_JOB_SERVICE_MUST_NOT_BE_NULL = "printJobService must not be null";
    public static final String PRINT_JOB_EXECUTION_SERVICE_MUST_NOT_BE_NULL = "printJobExecutionService must not be null";
    public static final String PRINTER_ACTION_GUARD_MUST_NOT_BE_NULL = "printerActionGuard must not be null";
    public static final String PRINTER_ACTION_MAPPER_MUST_NOT_BE_NULL = "printerActionMapper must not be null";

    public static final String JOB_ID_MUST_NOT_BE_BLANK = "jobId must not be blank";
    public static final String JOB_NAME_MUST_NOT_BE_BLANK = "jobName must not be blank";
    public static final String JOB_TYPE_MUST_NOT_BE_NULL = "jobType must not be null";
    public static final String JOB_STATE_MUST_NOT_BE_NULL = "jobState must not be null";
    public static final String ACTION_TYPE_MUST_NOT_BE_NULL = "actionType must not be null";

    public static final String JOB_NOT_FOUND = "job_not_found";
    public static final String JOB_ENDPOINT_NOT_FOUND = "job_endpoint_not_found";
    public static final String INVALID_JOB_STATE = "invalid_job_state";
    public static final String PRINTER_BUSY = "printer_busy";
    public static final String PRECONDITION_FAILED = "precondition_failed";

    public static final String FAILED_TO_SAVE_PRINT_JOB = "Failed to save print job";
    public static final String FAILED_TO_LOAD_PRINT_JOB = "Failed to load print job";
    public static final String FAILED_TO_LOAD_PRINT_JOBS = "Failed to load print jobs";
    public static final String FAILED_TO_UPDATE_PRINT_JOB = "Failed to update print job";
    public static final String FAILED_TO_DELETE_PRINT_JOB = "Failed to delete print job";
    public static final String FAILED_TO_SAVE_PRINT_FILE = "Failed to save print file";
    public static final String FAILED_TO_LOAD_PRINT_FILE = "Failed to load print file";
    public static final String FAILED_TO_LOAD_PRINT_FILES = "Failed to load print files";
    public static final String FAILED_TO_SAVE_PRINTER_SD_FILE = "Failed to save printer SD file";
    public static final String FAILED_TO_LOAD_PRINTER_SD_FILE = "Failed to load printer SD file";
    public static final String FAILED_TO_LOAD_PRINTER_SD_FILES = "Failed to load printer SD files";
    public static final String FAILED_TO_STORE_UPLOADED_PRINT_FILE = "failed_to_store_uploaded_print_file";
    public static final String FAILED_TO_READ_PRINT_FILE_CONTENT = "failed_to_read_print_file_content";
    public static final String PRINT_FILE_NOT_FOUND = "print_file_not_found";
    public static final String PRINTER_SD_FILE_NOT_FOUND = "printer_sd_file_not_found";
    public static final String PRINTER_SD_FILE_DISABLED = "printer_sd_file_disabled";
    public static final String PRINTER_SD_FILE_DELETED = "printer_sd_file_deleted";
    public static final String PRINTER_SD_FILE_ID_MUST_NOT_BE_BLANK = "printerSdFileId must not be blank";
    public static final String PRINTER_SD_FILE_PATH_MUST_NOT_BE_BLANK = "firmwarePath must not be blank";
    public static final String PRINT_FILE_ID_MUST_NOT_BE_BLANK = "printFileId must not be blank";
    public static final String PRINT_FILE_PATH_MUST_NOT_BE_BLANK = "path must not be blank";
    public static final String PRINT_FILE_STORAGE_DIRECTORY_MUST_NOT_BE_BLANK = "printFileStorageDirectory must not be blank";
    public static final String PRINT_FILE_FILENAME_MUST_NOT_BE_BLANK = "filename must not be blank";
    public static final String UNSUPPORTED_PRINT_FILE_TYPE = "unsupported_print_file_type";
    public static final String PRINT_FILE_MUST_EXIST = "print_file_must_exist";
    public static final String PRINT_FILE_MUST_BE_READABLE = "print_file_must_be_readable";
    public static final String PRINT_FILE_MUST_BE_REGULAR_FILE = "print_file_must_be_regular_file";
    public static final String FAILED_TO_LIST_SD_CARD_FILES = "failed_to_list_sd_card_files";

    public static final String EVENT_JOB_CREATED = "JOB_CREATED";
    public static final String EVENT_JOB_ASSIGNED = "JOB_ASSIGNED";
    public static final String EVENT_JOB_STARTED = "JOB_STARTED";
    public static final String EVENT_JOB_EXECUTION_QUEUED = "JOB_EXECUTION_QUEUED";
    public static final String EVENT_JOB_EXECUTION_STARTED = "JOB_EXECUTION_STARTED";
    public static final String EVENT_JOB_EXECUTION_IN_PROGRESS = "JOB_EXECUTION_IN_PROGRESS";
    public static final String EVENT_JOB_EXECUTION_SUCCEEDED = "JOB_EXECUTION_SUCCEEDED";
    public static final String EVENT_JOB_EXECUTION_FAILED = "JOB_EXECUTION_FAILED";
    public static final String EVENT_JOB_COMPLETED = "JOB_COMPLETED";
    public static final String EVENT_JOB_FAILED = "JOB_FAILED";
    public static final String EVENT_JOB_CANCELLED = "JOB_CANCELLED";
    public static final String EVENT_JOB_PAUSED = "JOB_PAUSED";
    public static final String EVENT_JOB_RESUMED = "JOB_RESUMED";
    public static final String EVENT_JOB_CANCELLING = "JOB_CANCELLING";
    public static final String EVENT_JOB_RESTARTED = "JOB_RESTARTED";

    public static final String SERIAL_TRANSFER_SETTINGS_STORE_MUST_NOT_BE_NULL = "serialTransferSettingsStore must not be null";

    public static String cameraCaptureFailed(String detail) {
        return CAMERA_CAPTURE_FAILED + ": " + safeDetail(detail, UNKNOWN_API_ERROR);
    }

    public static String cameraFfmpegCaptureStarting(
            String printerId,
            String sourceDescription,
            String command,
            String outputPath,
            int timeoutMs) {
        return "[PrinterHub] Camera ffmpeg capture starting printerId=" + safeDetail(printerId, "unknown")
                + " source=" + safeDetail(sourceDescription, "unknown")
                + " timeoutMs=" + timeoutMs
                + " output=" + safeDetail(outputPath, "unknown")
                + " command=" + safeDetail(command, "unknown");
    }

    public static String cameraFfmpegCaptureSucceeded(String printerId, int byteCount, String outputPath) {
        return "[PrinterHub] Camera ffmpeg capture succeeded printerId=" + safeDetail(printerId, "unknown")
                + " bytes=" + byteCount
                + " output=" + safeDetail(outputPath, "unknown");
    }

    public static String cameraFfmpegCaptureFailed(String printerId, String detail) {
        return "[PrinterHub] Camera ffmpeg capture failed printerId=" + safeDetail(printerId, "unknown")
                + ": " + safeDetail(detail, UNKNOWN_API_ERROR);
    }

    public static String cameraFfmpegTimedOut(int timeoutMs, String processOutput) {
        return "ffmpeg timed out after " + timeoutMs + " ms" + processOutputSuffix(processOutput);
    }

    public static String cameraFfmpegExited(int exitCode, String processOutput) {
        return "ffmpeg exited with code " + exitCode + processOutputSuffix(processOutput);
    }

    public static String cameraFfmpegOutputMissing(String outputPath, String processOutput) {
        return "ffmpeg completed but output file was not created: "
                + safeDetail(outputPath, "unknown") + processOutputSuffix(processOutput);
    }

    public static String cameraFfmpegOutputEmpty(String outputPath, String processOutput) {
        return "ffmpeg completed but output file is empty: "
                + safeDetail(outputPath, "unknown") + processOutputSuffix(processOutput);
    }

    public static String cameraFfmpegIoFailed(String detail) {
        return "ffmpeg capture I/O failed: " + safeDetail(detail, UNKNOWN_API_ERROR);
    }

    public static String cameraFfmpegInterrupted() {
        return "ffmpeg capture was interrupted";
    }

    public static String cameraAnalysisFailed(String detail) {
        return CAMERA_ANALYSIS_FAILED + ": " + safeDetail(detail, UNKNOWN_API_ERROR);
    }

    public static String cameraAnalysisMessage(
            String prefix,
            String deltaScore,
            String changedPixelRatio,
            String averagePixelDelta,
            String reasons) {
        return prefix
                + ": deltaScore=" + deltaScore
                + ", changedPixelRatio=" + changedPixelRatio
                + ", averagePixelDelta=" + averagePixelDelta
                + ", reasons=" + reasons;
    }

    public static String spaghettiDetectionMessage(
            String message,
            String confidence,
            String reasons) {
        return safeDetail(message, POSSIBLE_SPAGHETTI_FAILURE_DETECTED)
                + ": confidence=" + confidence
                + ", reasons=" + reasons;
    }

    public static String simulatedPrinterDisconnected(String portName) {
        return "Simulated printer is disconnected: " + portName;
    }

    public static String simulatedPrinterNotConnected(String portName) {
        return "Simulated printer is not connected: " + portName;
    }

    public static String databaseInitialized(String databaseFile) {
        return "[PrinterHub] Database initialized: " + databaseFile;
    }

    public static String failedToOpenDatabaseConnection(String databaseFile) {
        return "Failed to open database connection for file '" + databaseFile + "'";
    }

    public static String runtimeStartupFailed(String detail) {
        return "[PrinterHub] Runtime startup failed: " + detail;
    }

    public static String localRuntimeStarted() {
        return "[PrinterHub] Local runtime started";
    }

    public static String healthEndpoint(int apiPort) {
        return "[PrinterHub] Health:   http://localhost:" + apiPort + "/health";
    }

    public static String printersEndpoint(int apiPort) {
        return "[PrinterHub] Printers: http://localhost:" + apiPort + "/printers";
    }

    public static String monitoringSettingsEndpoint(int apiPort) {
        return "[PrinterHub] Settings: http://localhost:" + apiPort + "/settings/monitoring";
    }

    public static String invalidIntegerSystemProperty(String key, String value) {
        return "Invalid integer system property '" + key + "': " + value;
    }

    public static String invalidLongSystemProperty(String key, String value) {
        return "Invalid long system property '" + key + "': " + value;
    }

    public static String failedToDisconnectPrinterNode(String printerId, String detail) {
        return "[PrinterHub] Failed to disconnect printer node '" + printerId + "': " + detail;
    }

    public static String failedToCloseSerialPort(String portName, String detail) {
        return "[PrinterHub] Failed to close serial port '" + portName + "': " + detail;
    }

    public static String failedToCloseResource(String resourceName, String detail) {
        return "[PrinterHub] Failed to close " + resourceName + ": " + detail;
    }

    public static String noResponseForCommand(String command) {
        return "No response for command " + command;
    }

    public static String failedToPersistSnapshot(String printerId, String detail) {
        return "[PrinterHub] Failed to persist snapshot for " + printerId + ": " + detail;
    }

    public static String failedToPersistEvent(String printerId, String detail) {
        return "[PrinterHub] Failed to persist event for " + printerId + ": " + detail;
    }

    public static String failedToStartApiServer(int port) {
        return "Failed to start API server on port " + port;
    }

    public static String apiServerStarted(int port) {
        return "[PrinterHub] API server started on port " + port;
    }

    public static String apiServerStopped() {
        return "[PrinterHub] " + API_SERVER_STOPPED;
    }

    public static String apiOperationFailed(String detail) {
        return "[PrinterHub] " + API_OPERATION_FAILED + ": " + detail;
    }

    public static String unexpectedApiError(String detail) {
        return "[PrinterHub] " + UNEXPECTED_API_ERROR + ": " + detail;
    }

    public static String resourceNotFound(String resourcePath) {
        return "resource_not_found: " + resourcePath;
    }

    public static String fieldMustNotBeBlank(String fieldName) {
        return fieldName + " must not be blank";
    }

    public static String invalidEnumField(String fieldName, String value) {
        return "Invalid value for " + fieldName + ": " + value;
    }

    public static String invalidPrinterCommand(String value) {
        return "Invalid printer command: " + value;
    }

    public static String targetTemperatureRequired(String commandName) {
        return "targetTemperature is required for command " + commandName;
    }

    public static String failedToOpenSerialPort(String portName) {
        return "Failed to open serial port '" + portName + "'. "
                + "Possible causes: device path is wrong, permission is missing, "
                + "or the port is already in use.";
    }

    public static String failedToInitializeSerialStreams(String portName) {
        return "Failed to initialize serial streams for port: " + portName;
    }

    public static String failedToSendCommand(String command, String portName) {
        return "Failed to send command '" + command + "' to " + portName;
    }

    public static String noResponseForCommandOnPort(String command, String portName) {
        return "No response for command '" + command + "' on " + portName;
    }

    public static String interruptedWhileReadingResponse(String portName) {
        return "Interrupted while reading response from " + portName;
    }

    public static String noResponseWithinTimeout(int timeoutMs) {
        return "No response received from printer within " + timeoutMs + " ms.";
    }

    public static String invalidStoredPrinterSnapshotState(String storedState) {
        return INVALID_STORED_PRINTER_SNAPSHOT_STATE + ": " + storedState;
    }

    public static String invalidStoredPrinterSnapshotTimestamp(String storedTimestamp) {
        return INVALID_STORED_PRINTER_SNAPSHOT_TIMESTAMP + ": " + storedTimestamp;
    }

    public static String printerUpdateRestoreFailed(String printerId) {
        return "Printer update failed and previous runtime state could not be restored for " + printerId + ".";
    }

    public static String failedToRollbackPrinterRegistration(String printerId, String detail) {
        return "[PrinterHub] Failed to roll back printer registration for "
                + printerId + ": " + detail;
    }

    public static String failedToRestorePrinterAfterPut(String printerId, String detail) {
        return "[PrinterHub] Failed to restore printer after PUT failure for "
                + printerId + ": " + detail;
    }

    public static String failedToRestorePrinterAfterDelete(String printerId, String detail) {
        return "[PrinterHub] Failed to restore printer after DELETE failure for "
                + printerId + ": " + detail;
    }

    public static String invalidErrorPersistenceBehavior(String value) {
        return "Invalid errorPersistenceBehavior: " + value;
    }

    public static String commandExecuted(String wireCommand) {
        return "Manual command executed: " + wireCommand;
    }

    public static String commandFailed(String wireCommand, String detail) {
        return "Manual command failed: " + wireCommand + " -> " + detail;
    }

    public static String sdCardFilesListed(int count) {
        return "SD card files listed: " + count;
    }

    public static String sdCardFileListFailed(String detail) {
        return "SD card file list failed: " + detail;
    }

    public static String safeDetail(String detail, String fallback) {
        if (detail == null || detail.isBlank()) {
            return fallback;
        }

        return detail.trim();
    }

    private static String processOutputSuffix(String processOutput) {
        if (processOutput == null || processOutput.isBlank()) {
            return "";
        }

        return ": " + processOutput.trim();
    }

    public static String permissionDenied(LocalRole role, Permission permission) {
        return "Permission denied for role "
                + (role == null ? "UNKNOWN" : role.name())
                + ": "
                + (permission == null ? "UNKNOWN" : permission.name());
    }
}
