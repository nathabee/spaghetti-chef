package spaghettichef.config;

public final class SerialDefaults {

    private SerialDefaults() {
    }

    public static final int DEFAULT_BAUD_RATE = 115200;
    
    // Command-Response mode: tolerant of slow responses (G28, heating, etc)
    public static final int READ_TIMEOUT_MS = 2000;
    public static final int LONG_RUNNING_COMMAND_READ_TIMEOUT_MS = 60000;
    public static final int QUIET_PERIOD_MS = 200;
    public static final int READ_ACTIVITY_SLEEP_MS = 50;
    public static final int READ_IDLE_SLEEP_MS = 25;
    
    // File Streaming mode: aggressive polling for rapid acknowledgment
    // Used for SD card upload, print streaming, and bulk transfers
    // Printer responds immediately (typically <10ms for "ok")
    public static final int FILE_STREAMING_READ_TIMEOUT_MS = 5000;
    public static final int FILE_STREAMING_QUIET_PERIOD_MS = 10;
    public static final int FILE_STREAMING_READ_ACTIVITY_SLEEP_MS = 1;
    public static final int FILE_STREAMING_READ_IDLE_SLEEP_MS = 1;
    public static final int FILE_STREAMING_RECOVERY_REPLAY_DELAY_MS = 15;
    
    public static final String DEFAULT_COMMAND_TERMINATOR = "\n";


}
