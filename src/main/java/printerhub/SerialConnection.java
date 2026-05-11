package printerhub;

import printerhub.config.SerialDefaults;
import printerhub.serial.JSerialCommPortAdapter;
import printerhub.serial.SerialPortAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

public final class SerialConnection implements PrinterPort {

    private final String portName;
    private final int baudRate;

    private SerialPortAdapter port;
    private InputStream in;
    private OutputStream out;
    private volatile int lastSleepCycles;

    public SerialConnection(String portName) {
        this(portName, SerialDefaults.DEFAULT_BAUD_RATE);
    }

    public SerialConnection(String portName, int baudRate) {
        if (portName == null || portName.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PORT_NAME_MUST_NOT_BE_BLANK);
        }

        this.portName = portName.trim();
        this.baudRate = baudRate;
        this.port = null;
    }

    public SerialConnection(String portName, int baudRate, SerialPortAdapter portAdapter) {
        if (portName == null || portName.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PORT_NAME_MUST_NOT_BE_BLANK);
        }
        if (portAdapter == null) {
            throw new IllegalArgumentException(OperationMessages.PORT_ADAPTER_MUST_NOT_BE_NULL);
        }

        this.portName = portName.trim();
        this.baudRate = baudRate;
        this.port = portAdapter;
    }

    @Override
    public synchronized void connect() {
        if (isConnected()) {
            return;
        }

        disconnect();

        SerialPortAdapter activePort = port();

        activePort.setBaudRate(baudRate);
        activePort.setNumDataBits(SerialPortAdapter.EIGHT_DATA_BITS);
        activePort.setNumStopBits(SerialPortAdapter.ONE_STOP_BIT);
        activePort.setParity(SerialPortAdapter.NO_PARITY);
        activePort.setComPortTimeouts(SerialPortAdapter.TIMEOUT_NONBLOCKING, 0, 0);

        if (!activePort.openPort()) {
            throw new IllegalStateException(OperationMessages.failedToOpenSerialPort(portName));
        }

        try {
            in = activePort.getInputStream();
            out = activePort.getOutputStream();
        } catch (Exception exception) {
            safelyClosePortOnly();
            in = null;
            out = null;
            throw new IllegalStateException(
                    OperationMessages.failedToInitializeSerialStreams(portName),
                    exception);
        }
    }

    @Override
    public synchronized String sendCommand(String command) {
        ensureConnected();

        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.COMMAND_MUST_NOT_BE_BLANK);
        }

        String trimmedCommand = command.trim();
        int readTimeoutMs = readTimeoutMsForCommand(trimmedCommand);

        try {
            out.write((trimmedCommand + SerialDefaults.DEFAULT_COMMAND_TERMINATOR)
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
            return readLine(readTimeoutMs);
        } catch (IOException exception) {
            disconnect();
            throw new IllegalStateException(
                    OperationMessages.failedToSendCommand(trimmedCommand, portName),
                    exception);
        } catch (TimeoutException exception) {
            disconnect();
            throw new IllegalStateException(
                    OperationMessages.noResponseForCommandOnPort(trimmedCommand, portName),
                    exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            disconnect();
            throw new IllegalStateException(
                    OperationMessages.interruptedWhileReadingResponse(portName),
                    exception);
        } catch (RuntimeException exception) {
            disconnect();
            throw exception;
        }
    }

    @Override
    public synchronized void disconnect() {
        closeQuietly(in, "serial input stream");
        in = null;

        closeQuietly(out, "serial output stream");
        out = null;

        try {
            if (port != null && port.isOpen()) {
                port.closePort();
            }
        } catch (Exception exception) {
            System.err.println(OperationMessages.failedToCloseSerialPort(
                    portName,
                    OperationMessages.safeDetail(
                            exception.getMessage(),
                            OperationMessages.UNKNOWN_RUNTIME_CLOSE_ERROR)));
        }
    }

    @Override
    public synchronized String sendRawLine(String line) {
        return sendRawLine(line, SerialIOMode.COMMAND_RESPONSE);
    }

    @Override
    public synchronized String sendRawLine(String line, SerialIOMode mode) {
        ensureConnected();

        if (line == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("line"));
        }
        if (mode == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("mode"));
        }

        try {
            writeRawLineInternal(line);
            return readRawResponseInternal(mode);
        } catch (IOException exception) {
            disconnect();
            throw new IllegalStateException(
                    OperationMessages.failedToSendCommand(line, portName),
                    exception);
        } catch (TimeoutException exception) {
            disconnect();
            throw new IllegalStateException(
                    OperationMessages.noResponseForCommandOnPort(line, portName),
                    exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            disconnect();
            throw new IllegalStateException(
                    OperationMessages.interruptedWhileReadingResponse(portName),
                    exception);
        } catch (RuntimeException exception) {
            disconnect();
            throw exception;
        }
    }

    @Override
    public synchronized void writeRawLine(String line, SerialIOMode mode) {
        ensureConnected();

        if (line == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("line"));
        }
        if (mode == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("mode"));
        }

        try {
            writeRawLineInternal(line);
        } catch (IOException exception) {
            disconnect();
            throw new IllegalStateException(
                    OperationMessages.failedToSendCommand(line, portName),
                    exception);
        } catch (RuntimeException exception) {
            disconnect();
            throw exception;
        }
    }

    @Override
    public synchronized String readRawResponse(SerialIOMode mode) {
        ensureConnected();

        if (mode == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("mode"));
        }

        try {
            return readRawResponseInternal(mode);
        } catch (TimeoutException exception) {
            disconnect();
            throw new IllegalStateException(
                    OperationMessages.noResponseWithinTimeout(
                            mode == SerialIOMode.FILE_STREAMING
                                    ? SerialDefaults.FILE_STREAMING_READ_TIMEOUT_MS
                                    : SerialDefaults.LONG_RUNNING_COMMAND_READ_TIMEOUT_MS),
                    exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            disconnect();
            throw new IllegalStateException(
                    OperationMessages.interruptedWhileReadingResponse(portName),
                    exception);
        } catch (IOException exception) {
            disconnect();
            throw new IllegalStateException(
                    OperationMessages.failedToSendCommand("readRawResponse", portName),
                    exception);
        } catch (RuntimeException exception) {
            disconnect();
            throw exception;
        }
    }

    @Override
    public synchronized List<String> sendRawLinesPipelined(List<String> lines, SerialIOMode mode) {
        ensureConnected();

        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        if (mode == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("mode"));
        }

        try {
            for (String line : lines) {
                if (line == null) {
                    throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("line"));
                }
                writeRawLineInternal(line);
            }

            List<String> responses = new ArrayList<>(lines.size());
            for (int i = 0; i < lines.size(); i++) {
                responses.add(readRawResponseInternal(mode));
            }

            return responses;
        } catch (IOException exception) {
            disconnect();
            throw new IllegalStateException(
                    OperationMessages.failedToSendCommand(lines.toString(), portName),
                    exception);
        } catch (TimeoutException exception) {
            disconnect();
            throw new IllegalStateException(
                    OperationMessages.noResponseForCommandOnPort(lines.toString(), portName),
                    exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            disconnect();
            throw new IllegalStateException(
                    OperationMessages.interruptedWhileReadingResponse(portName),
                    exception);
        } catch (RuntimeException exception) {
            disconnect();
            throw exception;
        }
    }

    @Override
    public synchronized void discardPendingInput(int quietPeriodMs, int maxDrainMs) {
        ensureConnected();

        int effectiveQuietPeriodMs = Math.max(1, quietPeriodMs);
        int effectiveMaxDrainMs = Math.max(effectiveQuietPeriodMs, maxDrainMs);

        long start = System.currentTimeMillis();
        long lastReadAt = start;

        try {
            while (System.currentTimeMillis() - start < effectiveMaxDrainMs) {
                boolean drainedAny = false;

                while (in.available() > 0) {
                    int value = in.read();
                    if (value < 0) {
                        continue;
                    }
                    drainedAny = true;
                    lastReadAt = System.currentTimeMillis();
                }

                if (!drainedAny && (System.currentTimeMillis() - lastReadAt) >= effectiveQuietPeriodMs) {
                    return;
                }

                Thread.sleep(2L);
            }
        } catch (IOException exception) {
            disconnect();
            throw new IllegalStateException(
                    OperationMessages.failedToSendCommand("discardPendingInput", portName),
                    exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            disconnect();
            throw new IllegalStateException(
                    OperationMessages.interruptedWhileReadingResponse(portName),
                    exception);
        }
    }

    public String portName() {
        return portName;
    }

    public synchronized boolean isConnected() {
        return port != null && port.isOpen() && in != null && out != null;
    }

    public int getLastSleepCycles() {
        return lastSleepCycles;
    }

    static int readTimeoutMsForCommand(String command) {
        if (command == null) {
            return SerialDefaults.READ_TIMEOUT_MS;
        }

        String normalizedCommand = command.trim().toUpperCase(Locale.ROOT);

        if ("G28".equals(normalizedCommand) || normalizedCommand.startsWith("G28 ")) {
            return SerialDefaults.LONG_RUNNING_COMMAND_READ_TIMEOUT_MS;
        }

        return SerialDefaults.READ_TIMEOUT_MS;
    }

    private String readLine(int readTimeoutMs) throws IOException, TimeoutException, InterruptedException {
        return readResponseBlock(readTimeoutMs, SerialIOMode.COMMAND_RESPONSE);
    }

    private void writeRawLineInternal(String line) throws IOException {
        out.write((line + SerialDefaults.DEFAULT_COMMAND_TERMINATOR)
                .getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private String readRawResponseInternal(SerialIOMode mode)
            throws IOException, TimeoutException, InterruptedException {
        int readTimeoutMs = mode == SerialIOMode.FILE_STREAMING
                ? SerialDefaults.FILE_STREAMING_READ_TIMEOUT_MS
                : SerialDefaults.LONG_RUNNING_COMMAND_READ_TIMEOUT_MS;

        return readResponseBlock(readTimeoutMs, mode);
    }

    private String readResponseBlock(int readTimeoutMs, SerialIOMode mode)
            throws IOException, TimeoutException, InterruptedException {
        int activitySleepMs = mode == SerialIOMode.FILE_STREAMING
                ? SerialDefaults.FILE_STREAMING_READ_ACTIVITY_SLEEP_MS
                : SerialDefaults.READ_ACTIVITY_SLEEP_MS;

        int idleSleepMs = mode == SerialIOMode.FILE_STREAMING
                ? SerialDefaults.FILE_STREAMING_READ_IDLE_SLEEP_MS
                : SerialDefaults.READ_IDLE_SLEEP_MS;

        int quietPeriodMs = mode == SerialIOMode.FILE_STREAMING
                ? SerialDefaults.FILE_STREAMING_QUIET_PERIOD_MS
                : SerialDefaults.QUIET_PERIOD_MS;

        StringBuilder currentLine = new StringBuilder();
        StringBuilder responseBlock = new StringBuilder();
        long start = System.currentTimeMillis();
        long lastDataTime = start;
        int sleepCycles = 0;

        while (System.currentTimeMillis() - start < readTimeoutMs) {
            boolean readSomething = false;

            while (in.available() > 0) {
                int value = in.read();
                if (value < 0) {
                    continue;
                }

                readSomething = true;
                lastDataTime = System.currentTimeMillis();
                char ch = (char) value;

                if (ch == '\r') {
                    continue;
                }

                if (ch == '\n') {
                    String line = currentLine.toString().trim();
                    currentLine.setLength(0);

                    if (!line.isEmpty()) {
                        if (responseBlock.length() > 0) {
                            responseBlock.append('\n');
                        }
                        responseBlock.append(line);

                        if (isResponseBlockTerminator(line, mode, responseBlock.toString())) {
                            lastSleepCycles = sleepCycles;
                            return responseBlock.toString();
                        }
                    }
                } else {
                    currentLine.append(ch);
                }
            }

            if (readSomething) {
                Thread.sleep(activitySleepMs);
                sleepCycles++;
                continue;
            }

            boolean quietPeriodElapsed = System.currentTimeMillis() - lastDataTime >= quietPeriodMs;

            if (quietPeriodElapsed) {
                StringBuilder candidate = new StringBuilder(responseBlock);

                if (currentLine.length() > 0) {
                    String line = currentLine.toString().trim();
                    if (!line.isEmpty()) {
                        if (candidate.length() > 0) {
                            candidate.append('\n');
                        }
                        candidate.append(line);
                    }
                }

                String candidateResponse = candidate.toString().trim();
                if (!candidateResponse.isEmpty() && containsCompletionSignal(candidateResponse, mode)) {
                    lastSleepCycles = sleepCycles;
                    return candidateResponse;
                }
            }

            Thread.sleep(idleSleepMs);
            sleepCycles++;
        }

        if (currentLine.length() > 0) {
            String line = currentLine.toString().trim();
            if (!line.isEmpty()) {
                if (responseBlock.length() > 0) {
                    responseBlock.append('\n');
                }
                responseBlock.append(line);
            }
        }

        lastSleepCycles = sleepCycles;

        String cleaned = responseBlock.toString().trim();
        if (cleaned.isEmpty()) {
            throw new TimeoutException(OperationMessages.noResponseWithinTimeout(readTimeoutMs));
        }

        return cleaned;
    }

    private boolean isResponseBlockTerminator(String line, SerialIOMode mode, String responseSoFar) {
        if (line == null || line.isBlank()) {
            return false;
        }

        String normalizedLine = line.trim().toLowerCase(Locale.ROOT);
        String normalizedResponse = responseSoFar == null ? "" : responseSoFar.toLowerCase(Locale.ROOT);

        if ("ok".equals(normalizedLine) || normalizedLine.startsWith("ok ")) {
            return true;
        }

        if (normalizedLine.startsWith("resend:") || normalizedLine.startsWith("rs ")) {
            return true;
        }

        if (normalizedLine.contains("halted") || normalizedLine.contains("kill() called")) {
            return true;
        }

        if (normalizedLine.startsWith("error:")) {
            if (mode != SerialIOMode.FILE_STREAMING) {
                return true;
            }

            return normalizedResponse.contains("resend:")
                    || normalizedResponse.contains("\nrs ")
                    || normalizedResponse.startsWith("rs ")
                    || normalizedResponse.contains("\nok")
                    || normalizedResponse.startsWith("ok")
                    || normalizedResponse.contains("halted")
                    || normalizedResponse.contains("kill() called");
        }

        return false;
    }

    private boolean containsCompletionSignal(String response, SerialIOMode mode) {
        if (response == null || response.isBlank()) {
            return false;
        }

        String normalized = response.toLowerCase(Locale.ROOT);

        if (normalized.contains("\nok") || normalized.startsWith("ok")) {
            return true;
        }

        if (normalized.contains("resend:") || normalized.contains("\nrs ") || normalized.startsWith("rs ")) {
            return true;
        }

        if (normalized.contains("halted") || normalized.contains("kill() called")) {
            return true;
        }

        if (normalized.contains("error:")) {
            if (mode != SerialIOMode.FILE_STREAMING) {
                return true;
            }

            return normalized.contains("resend:")
                    || normalized.contains("\nrs ")
                    || normalized.startsWith("rs ")
                    || normalized.contains("\nok")
                    || normalized.startsWith("ok")
                    || normalized.contains("halted")
                    || normalized.contains("kill() called");
        }

        return false;
    }

    private void ensureConnected() {
        if (!isConnected()) {
            throw new IllegalStateException(
                    OperationMessages.SERIAL_CONNECTION_IS_NOT_OPEN + " " + portName);
        }
    }

    private SerialPortAdapter port() {
        if (port == null) {
            port = new JSerialCommPortAdapter(portName);
        }

        return port;
    }

    private void safelyClosePortOnly() {
        try {
            if (port != null && port.isOpen()) {
                port.closePort();
            }
        } catch (Exception exception) {
            System.err.println(OperationMessages.failedToCloseSerialPort(
                    portName,
                    OperationMessages.safeDetail(
                            exception.getMessage(),
                            OperationMessages.UNKNOWN_RUNTIME_CLOSE_ERROR)));
        }
    }

    private void closeQuietly(AutoCloseable closeable, String resourceName) {
        if (closeable == null) {
            return;
        }

        try {
            closeable.close();
        } catch (Exception exception) {
            System.err.println(OperationMessages.failedToCloseResource(
                    resourceName,
                    OperationMessages.safeDetail(
                            exception.getMessage(),
                            OperationMessages.UNKNOWN_RUNTIME_CLOSE_ERROR)));
        }
    }
}