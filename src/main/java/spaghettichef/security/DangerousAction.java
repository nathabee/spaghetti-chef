package spaghettichef.security;

public enum DangerousAction {
    HEATING,
    MOVEMENT,
    HOMING,
    SD_DELETE,
    FILE_UPLOAD_OVERWRITE,
    PRINT_START,
    PRINT_CANCEL,
    RECOVERY_CLOSE_UPLOAD,
    RAW_COMMAND,
    STREAMED_GCODE_EXECUTION
}
