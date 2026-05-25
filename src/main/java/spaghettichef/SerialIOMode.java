package spaghettichef;

/**
 * Describes the communication pattern for serial I/O operations.
 * 
 * Different communication patterns require different timeout and polling strategies:
 * - COMMAND_RESPONSE: Single command, response may be slow (G28 can take 10+ seconds)
 * - FILE_STREAMING: Fast request/response cycle (SD upload, print streaming)
 * 
 * This allows SerialConnection to optimize polling behavior without requiring
 * separate implementations or excessive configuration.
 */
public enum SerialIOMode {
    /**
     * Command-response pattern: We send a command and wait for a response.
     * The response can be slow (heating, homing, status queries).
     * 
     * Uses tolerant polling: slower feedback loops, longer quiet periods.
     * Timeout: 60 seconds (allow slow commands like G28 to complete)
     */
    COMMAND_RESPONSE,

    /**
     * File streaming pattern: We send data lines and expect rapid acknowledgment.
     * Used for SD card upload, print file streaming (0.2.8), and other bulk transfers.
     * 
     * Printer responds immediately (typically <10ms for "ok").
     * Uses aggressive polling: minimal sleep, fast feedback.
     * Timeout: 5 seconds (any delay > 5s indicates communication failure)
     */
    FILE_STREAMING
}
