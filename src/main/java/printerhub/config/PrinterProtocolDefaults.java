package printerhub.config;

public final class PrinterProtocolDefaults {

    private PrinterProtocolDefaults() {
    }

    public static final String COMMAND_READ_TEMPERATURE = "M105";
    public static final String COMMAND_READ_POSITION = "M114";
    public static final String COMMAND_READ_FIRMWARE_INFO = "M115";
    public static final String COMMAND_HOME_AXES = "G28";
    public static final String COMMAND_SET_NOZZLE_TEMPERATURE = "M104";
    public static final String COMMAND_SET_BED_TEMPERATURE = "M140";
    public static final String COMMAND_SET_FAN_SPEED = "M106";
    public static final String COMMAND_TURN_FAN_OFF = "M107";
    public static final String COMMAND_LIST_SD_FILES = "M20";
    public static final String COMMAND_SELECT_SD_FILE = "M23";
    public static final String COMMAND_START_SD_PRINT = "M24";
    public static final String COMMAND_READ_SD_PRINT_STATUS = "M27";
    public static final String COMMAND_PAUSE_SD_PRINT = "M25";
    public static final String COMMAND_ABORT_SD_PRINT = "M524";
    public static final String COMMAND_DELETE_SD_FILE = "M30";
    public static final int SD_UPLOAD_MAX_RETRIES_PER_LINE = 3;
    public static final String DEFAULT_STATUS_COMMAND = "M105";
    public static final double DEFAULT_HEATING_TEMPERATURE_THRESHOLD = 45.0;

    public static final String SIM_MODE = "sim";
    public static final String SIM_DISCONNECTED_MODE = "sim-disconnected";
    public static final String SIM_TIMEOUT_MODE = "sim-timeout";
    public static final String SIM_ERROR_MODE = "sim-error";

    public static final String SIMULATED_RESPONSE_DEFAULT_OK = "ok";
    public static final String SIMULATED_RESPONSE_M105 = "ok T:21.80 /0.00 B:21.52 /0.00 @:0 B@:0";
    public static final String SIMULATED_RESPONSE_M114 = "X:0.00 Y:0.00 Z:0.00 E:0.00 Count X:0 Y:0 Z:0";
    public static final String SIMULATED_RESPONSE_M115 = "FIRMWARE_NAME:Marlin SIMULATED PROTOCOL_VERSION:1.0 MACHINE_TYPE:Ender-3 V2 Neo EXTRUDER_COUNT:1";
    public static final String SIMULATED_RESPONSE_M20 = """
            Begin file list
            CUBE.GCO 12345
            BENCHY.GCO 67890
            End file list
            ok
            """;
    public static final String SIMULATED_RESPONSE_M27 = "Not SD printing";
}
