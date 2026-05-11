package printerhub;

import java.util.List;

public interface PrinterPort {

    void connect();

    String sendCommand(String command);

    String sendRawLine(String line);

    default String sendRawLine(String line, SerialIOMode mode) {
        return sendRawLine(line);
    }

    default void writeRawLine(String line, SerialIOMode mode) {
        throw new UnsupportedOperationException("writeRawLine is not supported");
    }

    default String readRawResponse(SerialIOMode mode) {
        throw new UnsupportedOperationException("readRawResponse is not supported");
    }

    default List<String> sendRawLinesPipelined(List<String> lines, SerialIOMode mode) {
        throw new UnsupportedOperationException("sendRawLinesPipelined is not supported");
    }

    void discardPendingInput(int quietPeriodMs, int maxDrainMs);

    void disconnect();
}