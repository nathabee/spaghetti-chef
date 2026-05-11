package printerhub.serial;

import printerhub.OperationMessages;
import printerhub.PrinterPort;
import printerhub.SerialIOMode;
import printerhub.config.PrinterProtocolDefaults;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SimulatedPrinterPort implements PrinterPort {

    private final String portName;
    private final String mode;
    private final List<String> pendingRawResponses = new ArrayList<>();
    private boolean connected;

    public SimulatedPrinterPort(String portName) {
        this(portName, PrinterProtocolDefaults.SIM_MODE);
    }

    public SimulatedPrinterPort(String portName, String mode) {
        if (portName == null || portName.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PORT_NAME_MUST_NOT_BE_BLANK);
        }

        this.portName = portName.trim();
        this.mode = (mode == null || mode.isBlank())
                ? PrinterProtocolDefaults.SIM_MODE
                : mode.trim();
    }

    @Override
    public void connect() {
        if (isDisconnectedMode()) {
            connected = false;
            throw new IllegalStateException(OperationMessages.simulatedPrinterDisconnected(portName));
        }

        connected = true;
    }

    @Override
    public String sendCommand(String command) {
        ensureConnected();

        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.COMMAND_MUST_NOT_BE_BLANK);
        }

        if (isTimeoutMode()) {
            return "";
        }

        if (isErrorMode()) {
            return OperationMessages.SIMULATED_PRINTER_FAILURE_RESPONSE;
        }

        return defaultResponseFor(command);
    }

    @Override
    public String sendRawLine(String line) {
        return sendRawLine(line, SerialIOMode.COMMAND_RESPONSE);
    }

    @Override
    public String sendRawLine(String line, SerialIOMode mode) {
        ensureConnected();

        if (line == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("line"));
        }
        if (mode == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("mode"));
        }

        if (isErrorMode()) {
            throw new IllegalStateException(OperationMessages.SIMULATED_PRINTER_FAILURE_RESPONSE);
        }

        if (isDisconnectedMode()) {
            connected = false;
            throw new IllegalStateException(OperationMessages.simulatedPrinterDisconnected(portName));
        }

        if (isTimeoutMode()) {
            return "";
        }

        return defaultRawResponseFor(line, mode);
    }

    @Override
    public void writeRawLine(String line, SerialIOMode mode) {
        ensureConnected();

        if (line == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("line"));
        }
        if (mode == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("mode"));
        }

        if (isErrorMode()) {
            throw new IllegalStateException(OperationMessages.SIMULATED_PRINTER_FAILURE_RESPONSE);
        }

        if (isDisconnectedMode()) {
            connected = false;
            throw new IllegalStateException(OperationMessages.simulatedPrinterDisconnected(portName));
        }

        if (isTimeoutMode()) {
            pendingRawResponses.add("");
            return;
        }

        pendingRawResponses.add(defaultRawResponseFor(line, mode));
    }

    @Override
    public String readRawResponse(SerialIOMode mode) {
        ensureConnected();

        if (mode == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("mode"));
        }

        if (isErrorMode()) {
            throw new IllegalStateException(OperationMessages.SIMULATED_PRINTER_FAILURE_RESPONSE);
        }

        if (isDisconnectedMode()) {
            connected = false;
            throw new IllegalStateException(OperationMessages.simulatedPrinterDisconnected(portName));
        }

        if (!pendingRawResponses.isEmpty()) {
            return pendingRawResponses.remove(0);
        }

        if (isTimeoutMode()) {
            return "";
        }

        return PrinterProtocolDefaults.SIMULATED_RESPONSE_DEFAULT_OK;
    }

    @Override
    public List<String> sendRawLinesPipelined(List<String> lines, SerialIOMode mode) {
        ensureConnected();

        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        if (mode == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("mode"));
        }

        List<String> responses = new ArrayList<>(lines.size());
        for (String line : lines) {
            responses.add(sendRawLine(line, mode));
        }
        return responses;
    }

    @Override
    public void discardPendingInput(int quietPeriodMs, int maxDrainMs) {
        ensureConnected();
        pendingRawResponses.clear();
    }

    @Override
    public void disconnect() {
        connected = false;
        pendingRawResponses.clear();
    }

    private String defaultResponseFor(String command) {
        String normalized = command.trim().toUpperCase(Locale.ROOT);

        return switch (normalized) {
            case "M105" -> PrinterProtocolDefaults.SIMULATED_RESPONSE_M105;
            case "M114" -> PrinterProtocolDefaults.SIMULATED_RESPONSE_M114;
            case "M115" -> PrinterProtocolDefaults.SIMULATED_RESPONSE_M115;
            case "M20" -> PrinterProtocolDefaults.SIMULATED_RESPONSE_M20;
            case "M27" -> PrinterProtocolDefaults.SIMULATED_RESPONSE_M27;
            default -> PrinterProtocolDefaults.SIMULATED_RESPONSE_DEFAULT_OK;
        };
    }

    private String defaultRawResponseFor(String line, SerialIOMode mode) {
        String normalized = line.trim().toUpperCase(Locale.ROOT);

        if (normalized.contains("M20")) {
            return PrinterProtocolDefaults.SIMULATED_RESPONSE_M20;
        }

        if (normalized.contains("M27")) {
            return PrinterProtocolDefaults.SIMULATED_RESPONSE_M27;
        }

        return PrinterProtocolDefaults.SIMULATED_RESPONSE_DEFAULT_OK;
    }

    private void ensureConnected() {
        if (!connected) {
            throw new IllegalStateException(OperationMessages.simulatedPrinterNotConnected(portName));
        }
    }

    private boolean isDisconnectedMode() {
        return normalizedMode().equals(PrinterProtocolDefaults.SIM_DISCONNECTED_MODE);
    }

    private boolean isTimeoutMode() {
        return normalizedMode().equals(PrinterProtocolDefaults.SIM_TIMEOUT_MODE);
    }

    private boolean isErrorMode() {
        return normalizedMode().equals(PrinterProtocolDefaults.SIM_ERROR_MODE);
    }

    private String normalizedMode() {
        return mode.trim().toLowerCase(Locale.ROOT);
    }
}