package printerhub.config;

public final class RuntimeDefaults {

    private RuntimeDefaults() {
    }

    public static final int MIN_PORT = 1;
    public static final int MAX_PORT = 65535;
    public static final int DEFAULT_API_THREAD_POOL_SIZE = 8;
    public static final int DEFAULT_JOB_EXECUTOR_POOL_SIZE = 8;
    public static final int MIN_MONITORING_EXECUTOR_POOL_SIZE = 1;
    public static final int MONITORING_EXECUTOR_EXTRA_THREADS = 4;
    public static final int DEFAULT_MONITORING_EXECUTOR_POOL_SIZE = 8;
    public static final int DEFAULT_RECENT_JOB_LIMIT = 20;
    public static final int DEFAULT_RECENT_SNAPSHOT_LIMIT = 20;

    public static final String API_PORT_PROPERTY = "printerhub.api.port";
    public static final int DEFAULT_API_PORT = 8080;

    public static final String MONITORING_INTERVAL_SECONDS_PROPERTY = "printerhub.monitoring.intervalSeconds";
    public static final long DEFAULT_MONITORING_INTERVAL_SECONDS = 5L;
    public static final int ERROR_EXIT_CODE = 1;

    public static final String DATABASE_FILE_PROPERTY = "printerhub.databaseFile";
    public static final String DEFAULT_DATABASE_FILE = "printerhub.db";
    public static final String SQLITE_JDBC_PREFIX = "jdbc:sqlite:";
    public static final String PRINT_FILE_STORAGE_DIRECTORY_PROPERTY = "printerhub.printFileStorageDirectory";
    public static final String DEFAULT_PRINT_FILE_STORAGE_DIRECTORY = "printerhub-print-files";

    public static final boolean DEFAULT_SNAPSHOT_ON_STATE_CHANGE = true;
    public static final double DEFAULT_TEMPERATURE_THRESHOLD = 1.0;
    public static final long DEFAULT_MIN_SNAPSHOT_INTERVAL_SECONDS = 30L;
    public static final long DEFAULT_MONITORING_EVENT_DEDUP_WINDOW_SECONDS = 60L;
    public static final String DEFAULT_ERROR_PERSISTENCE_BEHAVIOR = "DEDUPLICATED";
    public static final boolean DEFAULT_TRACE = false;


    /* Camera analysis */
    public static final boolean DEFAULT_CAMERA_ANALYSIS_ENABLED = true;
    public static final boolean DEFAULT_CAMERA_DELTA_IMAGE_ENABLED = true;
    public static final int DEFAULT_CAMERA_DELTA_PIXEL_THRESHOLD = 35;
    public static final double DEFAULT_CAMERA_DELTA_SCORE_THRESHOLD = 0.08;
    public static final double DEFAULT_CAMERA_SPAGHETTI_CONFIDENCE_THRESHOLD = 0.65;
    public static final long DEFAULT_CAMERA_MIN_ANALYSIS_INTERVAL_SECONDS = 5L;
    public static final String DEFAULT_CAMERA_FFMPEG_COMMAND = "ffmpeg";
    public static final String DEFAULT_CAMERA_FFMPEG_INPUT_FORMAT = "";
    public static final String DEFAULT_CAMERA_FFMPEG_VIDEO_SIZE = "640x480";
    public static final int DEFAULT_CAMERA_FFMPEG_TIMEOUT_MS = 5000;
    public static final int DEFAULT_CAMERA_FFMPEG_JPEG_QUALITY = 3;



    /* SD upload */
    public static final int DEFAULT_SD_UPLOAD_BATCH_SIZE = 5;

    /**
     * Retained resend-history window multiplier for SD upload recovery.
     * Effective retained history = sdUploadBatchSize *
     * sdUploadRecoveryWindowMultiplier.
     */
    public static final int DEFAULT_SD_UPLOAD_RECOVERY_WINDOW_MULTIPLIER = 2;

    /**
     * Hard stop for cumulative SD-upload protocol problems
     * such as resend requests, recovery jumps, or unexpected error responses.
     */
    public static final int DEFAULT_SD_UPLOAD_MAX_ERRORS = 100;

    /**
     * Hard stop for pathological loops where the printer repeatedly asks
     * for the same resend line and forward progress is not achieved.
     */
    public static final int DEFAULT_SD_UPLOAD_MAX_CONSECUTIVE_IDENTICAL_RESENDS = 10;

    /**
     * Minimum acceptable effective upload performance as percent of
     * theoretical maximum, evaluated only after enough runtime/progress.
     */
    public static final int DEFAULT_SD_UPLOAD_MIN_PERFORMANCE_PERCENT = 5;
}
