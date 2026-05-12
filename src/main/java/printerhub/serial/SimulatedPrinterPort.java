package printerhub.serial;

import printerhub.OperationMessages;
import printerhub.PrinterPort;
import printerhub.SerialIOMode;
import printerhub.config.PrinterProtocolDefaults;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SimulatedPrinterPort implements PrinterPort {

    private final String portName;
    private final String mode;
    private final List<String> pendingRawResponses = new ArrayList<>();
    private final Map<String, Long> simulatedSdFiles = new LinkedHashMap<>();

    private boolean connected;
    private boolean sdWriteSessionOpen;
    private String currentSdWriteFilename;
    private long currentSdWriteByteCount;

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
        sdWriteSessionOpen = false;
        currentSdWriteFilename = null;
        currentSdWriteByteCount = 0L;
    }

    private String defaultResponseFor(String command) {
        String normalized = command.trim().toUpperCase(Locale.ROOT);

        return switch (normalized) {
            case "M105" -> PrinterProtocolDefaults.SIMULATED_RESPONSE_M105;
            case "M114" -> PrinterProtocolDefaults.SIMULATED_RESPONSE_M114;
            case "M115" -> PrinterProtocolDefaults.SIMULATED_RESPONSE_M115;
            case "M20" -> buildSimulatedSdCardListing();
            case "M27" -> PrinterProtocolDefaults.SIMULATED_RESPONSE_M27;
            default -> PrinterProtocolDefaults.SIMULATED_RESPONSE_DEFAULT_OK;
        };
    }

    private String defaultRawResponseFor(String line, SerialIOMode mode) {
        String trimmed = line.trim();
        String normalized = trimmed.toUpperCase(Locale.ROOT);

        if (normalized.startsWith("N0 M110")) {
            return PrinterProtocolDefaults.SIMULATED_RESPONSE_DEFAULT_OK;
        }

        if (normalized.contains(" M28 ")) {
            String filename = extractSdWriteFilename(trimmed);
            openSimulatedSdWriteSession(filename);
            return "echo:Now fresh file: " + filename + "\n"
                    + "Writing to file: " + filename + "\n"
                    + "ok";
        }

        if (normalized.contains(" M29")) {
            closeSimulatedSdWriteSession();
            return PrinterProtocolDefaults.SIMULATED_RESPONSE_DEFAULT_OK;
        }

        if (normalized.contains(" M20")) {
            return buildSimulatedSdCardListing();
        }

        if (normalized.contains(" M27")) {
            return PrinterProtocolDefaults.SIMULATED_RESPONSE_M27;
        }

        if (sdWriteSessionOpen && looksLikeChecksummedProtocolLine(trimmed)) {
            currentSdWriteByteCount += estimatePayloadByteCount(trimmed);
            return PrinterProtocolDefaults.SIMULATED_RESPONSE_DEFAULT_OK;
        }

        return PrinterProtocolDefaults.SIMULATED_RESPONSE_DEFAULT_OK;
    }

    private void openSimulatedSdWriteSession(String filename) {
        sdWriteSessionOpen = true;
        currentSdWriteFilename = filename;
        currentSdWriteByteCount = 0L;
    }

    private void closeSimulatedSdWriteSession() {
        if (sdWriteSessionOpen && currentSdWriteFilename != null && !currentSdWriteFilename.isBlank()) {
            simulatedSdFiles.put(currentSdWriteFilename, currentSdWriteByteCount);
        }

        sdWriteSessionOpen = false;
        currentSdWriteFilename = null;
        currentSdWriteByteCount = 0L;
    }

    private String buildSimulatedSdCardListing() {
        if (simulatedSdFiles.isEmpty()) {
            return PrinterProtocolDefaults.SIMULATED_RESPONSE_M20;
        }

        StringBuilder response = new StringBuilder();
        response.append("Begin file list\n");

        for (Map.Entry<String, Long> entry : simulatedSdFiles.entrySet()) {
            response.append(entry.getKey())
                    .append(" ")
                    .append(entry.getValue())
                    .append("\n");
        }

        response.append("End file list\nok");
        return response.toString();
    }

    private String extractSdWriteFilename(String line) {
        int commandIndex = line.toUpperCase(Locale.ROOT).indexOf("M28 ");
        if (commandIndex < 0) {
            return "SIMULATED.GCO";
        }

        String filename = line.substring(commandIndex + 4).trim();
        int checksumIndex = filename.indexOf('*');
        if (checksumIndex >= 0) {
            filename = filename.substring(0, checksumIndex).trim();
        }

        if (filename.isBlank()) {
            return "SIMULATED.GCO";
        }

        return filename;
    }

    private boolean looksLikeChecksummedProtocolLine(String line) {
        return line.startsWith("N") && line.contains("*");
    }

    private long estimatePayloadByteCount(String line) {
        int firstSpace = line.indexOf(' ');
        int checksumIndex = line.lastIndexOf('*');

        if (firstSpace < 0 || checksumIndex <= firstSpace) {
            return 0L;
        }

        String payload = line.substring(firstSpace + 1, checksumIndex).trim();
        return payload.length() + 1L;
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