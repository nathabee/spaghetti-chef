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
    public static final int DEFAULT_MONITORING_UPLOAD_BATCH_SIZE = 5;
}
