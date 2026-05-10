package printerhub;

import printerhub.config.SerialDefaults;
import printerhub.serial.JSerialCommPortAdapter;
import printerhub.serial.SerialPortAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

public final class SerialConnection implements PrinterPort {

    private final String portName;
    private final int baudRate;

    private SerialPortAdapter port;
    private InputStream in;
    private OutputStream out;

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

    /**
     * Send a raw line to the printer and read the response.
     * 
     * @param line the line to send
     * @param mode the communication mode (affects timeout and polling strategy)
     * @return the printer response
     */
    public synchronized String sendRawLine(String line, SerialIOMode mode) {
        ensureConnected();

        if (line == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("line"));
        }
        if (mode == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("mode"));
        }

        try {
            out.write((line + SerialDefaults.DEFAULT_COMMAND_TERMINATOR)
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();

            int readTimeoutMs = mode == SerialIOMode.FILE_STREAMING
                    ? SerialDefaults.FILE_STREAMING_READ_TIMEOUT_MS
                    : SerialDefaults.LONG_RUNNING_COMMAND_READ_TIMEOUT_MS;

            return readLine(readTimeoutMs, mode);
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

    public String portName() {
        return portName;
    }

    public synchronized boolean isConnected() {
        return port != null && port.isOpen() && in != null && out != null;
    }

    static int readTimeoutMsForCommand(String command) {
        if (command == null) {
            return SerialDefaults.READ_TIMEOUT_MS;
        }

        String normalizedCommand = command.trim().toUpperCase(java.util.Locale.ROOT);

        if ("G28".equals(normalizedCommand) || normalizedCommand.startsWith("G28 ")) {
            return SerialDefaults.LONG_RUNNING_COMMAND_READ_TIMEOUT_MS;
        }

        return SerialDefaults.READ_TIMEOUT_MS;
    }

    private String readLine(int readTimeoutMs) throws IOException, TimeoutException, InterruptedException {
        return readLine(readTimeoutMs, SerialIOMode.COMMAND_RESPONSE);
    }

    private String readLine(int readTimeoutMs, SerialIOMode mode) throws IOException, TimeoutException, InterruptedException {
        int activitySleepMs = mode == SerialIOMode.FILE_STREAMING
                ? SerialDefaults.FILE_STREAMING_READ_ACTIVITY_SLEEP_MS
                : SerialDefaults.READ_ACTIVITY_SLEEP_MS;
        
        int idleSleepMs = mode == SerialIOMode.FILE_STREAMING
                ? SerialDefaults.FILE_STREAMING_READ_IDLE_SLEEP_MS
                : SerialDefaults.READ_IDLE_SLEEP_MS;
        
        int quietPeriodMs = mode == SerialIOMode.FILE_STREAMING
                ? SerialDefaults.FILE_STREAMING_QUIET_PERIOD_MS
                : SerialDefaults.QUIET_PERIOD_MS;
        
        StringBuilder response = new StringBuilder();
        long start = System.currentTimeMillis();
        long lastDataTime = start;

        while (System.currentTimeMillis() - start < readTimeoutMs) {
            boolean readSomething = false;

            while (in.available() > 0) {
                int value = in.read();
                if (value >= 0) {
                    response.append((char) value);
                    readSomething = true;
                    lastDataTime = System.currentTimeMillis();
                }
            }

            if (readSomething) {
                Thread.sleep(activitySleepMs);
            } else {
                if (response.length() > 0
                        && hasCommandCompletionSignal(response.toString())
                        && System.currentTimeMillis() - lastDataTime > quietPeriodMs) {
                    break;
                }
                Thread.sleep(idleSleepMs);
            }
        }

        String cleaned = response.toString().trim();

        if (cleaned.isEmpty()) {
            throw new TimeoutException(
                    OperationMessages.noResponseWithinTimeout(readTimeoutMs));
        }

        return cleaned;
    }

    private boolean hasCommandCompletionSignal(String response) {
        if (response == null || response.isBlank()) {
            return false;
        }

        String normalizedResponse = response.toLowerCase(java.util.Locale.ROOT);

        return normalizedResponse.contains("ok")
                || normalizedResponse.contains("error:")
                || normalizedResponse.contains("failed")
                || normalizedResponse.contains("halted")
                || normalizedResponse.contains("kill() called");
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
