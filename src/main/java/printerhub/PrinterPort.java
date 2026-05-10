package printerhub;

public interface PrinterPort {

    void connect();

    String sendCommand(String command);

    String sendRawLine(String line);

    /**
     * Send a raw line with a specified communication mode.
     * Mode determines timeout and polling strategy for the response.
     * 
     * Default implementation delegates to sendRawLine(line) for backward compatibility.
     * Override to support mode-specific optimizations.
     * 
     * @param line the line to send
     * @param mode the communication mode (COMMAND_RESPONSE or FILE_STREAMING)
     * @return the printer response
     */
    default String sendRawLine(String line, SerialIOMode mode) {
        return sendRawLine(line);
    }

    void disconnect();
}