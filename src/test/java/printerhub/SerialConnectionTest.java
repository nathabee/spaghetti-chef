package printerhub;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import printerhub.config.SerialDefaults;
import printerhub.persistence.DatabaseInitializer;
import printerhub.persistence.SerialTransferSettings;
import printerhub.persistence.SerialTransferSettingsStore;
import printerhub.serial.SerialPortAdapter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SerialConnectionTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearDatabaseProperty() {
        System.clearProperty("printerhub.databaseFile");
    }

    @Test
    void constructorFailsForBlankPortName() {
        IllegalArgumentException ex1 = assertThrows(
                IllegalArgumentException.class,
                () -> new SerialConnection(null, 115200, new FakeSerialPortAdapter()));
        assertEquals("portName must not be blank", ex1.getMessage());

        IllegalArgumentException ex2 = assertThrows(
                IllegalArgumentException.class,
                () -> new SerialConnection("", 115200, new FakeSerialPortAdapter()));
        assertEquals("portName must not be blank", ex2.getMessage());

        IllegalArgumentException ex3 = assertThrows(
                IllegalArgumentException.class,
                () -> new SerialConnection("   ", 115200, new FakeSerialPortAdapter()));
        assertEquals("portName must not be blank", ex3.getMessage());
    }

    @Test
    void constructorFailsForNullPortAdapter() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SerialConnection("COM1", 115200, null));

        assertEquals("portAdapter must not be null", exception.getMessage());
    }

    @Test
    void connectConfiguresPortAndInitializesStreams() {
        FakeSerialPortAdapter adapter = new FakeSerialPortAdapter();
        adapter.inputStream = new ByteArrayInputStream(new byte[0]);
        adapter.outputStream = new ByteArrayOutputStream();

        SerialConnection connection = new SerialConnection("COM1", 250000, adapter);

        connection.connect();

        assertEquals("COM1", connection.portName());
        assertTrue(connection.isConnected());
        assertEquals(250000, adapter.baudRate);
        assertEquals(SerialPortAdapter.EIGHT_DATA_BITS, adapter.numDataBits);
        assertEquals(SerialPortAdapter.ONE_STOP_BIT, adapter.numStopBits);
        assertEquals(SerialPortAdapter.NO_PARITY, adapter.parity);
        assertEquals(SerialPortAdapter.TIMEOUT_NONBLOCKING, adapter.timeoutMode);
        assertEquals(0, adapter.readTimeout);
        assertEquals(0, adapter.writeTimeout);
        assertEquals(1, adapter.openPortCalls);
    }

    @Test
    void connectIsIgnoredWhenAlreadyConnected() {
        FakeSerialPortAdapter adapter = new FakeSerialPortAdapter();
        adapter.inputStream = new ByteArrayInputStream(new byte[0]);
        adapter.outputStream = new ByteArrayOutputStream();

        SerialConnection connection = new SerialConnection("COM1", 115200, adapter);

        connection.connect();
        connection.connect();

        assertEquals(1, adapter.openPortCalls);
    }

    @Test
    void connectFailsWhenOpenPortReturnsFalse() {
        FakeSerialPortAdapter adapter = new FakeSerialPortAdapter();
        adapter.openPortResult = false;

        SerialConnection connection = new SerialConnection("COM1", 115200, adapter);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                connection::connect);

        assertEquals(
                "Failed to open serial port 'COM1'. Possible causes: device path is wrong, permission is missing, or the port is already in use.",
                exception.getMessage());
    }

    @Test
    void connectFailsWhenStreamInitializationFails() {
        FakeSerialPortAdapter adapter = new FakeSerialPortAdapter();
        adapter.throwOnGetInputStream = true;

        SerialConnection connection = new SerialConnection("COM1", 115200, adapter);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                connection::connect);

        assertEquals("Failed to initialize serial streams for port: COM1", exception.getMessage());
        assertTrue(adapter.closePortCalls >= 1);
    }

    @Test
    void sendCommandFailsWhenNotConnected() {
        SerialConnection connection = new SerialConnection("COM1", 115200, new FakeSerialPortAdapter());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> connection.sendCommand("M105"));

        assertEquals("Serial port is not open. COM1", exception.getMessage());
    }

    @Test
    void sendCommandFailsForBlankCommand() {
        FakeSerialPortAdapter adapter = new FakeSerialPortAdapter();
        adapter.inputStream = new ByteArrayInputStream(new byte[0]);
        adapter.outputStream = new ByteArrayOutputStream();

        SerialConnection connection = new SerialConnection("COM1", 115200, adapter);
        connection.connect();

        IllegalArgumentException ex1 = assertThrows(
                IllegalArgumentException.class,
                () -> connection.sendCommand(null));
        assertEquals("command must not be blank", ex1.getMessage());

        IllegalArgumentException ex2 = assertThrows(
                IllegalArgumentException.class,
                () -> connection.sendCommand(""));
        assertEquals("command must not be blank", ex2.getMessage());

        IllegalArgumentException ex3 = assertThrows(
                IllegalArgumentException.class,
                () -> connection.sendCommand("   "));
        assertEquals("command must not be blank", ex3.getMessage());
    }

    @Test
    void sendCommandWritesCommandAndReturnsTrimmedResponse() {
        FakeSerialPortAdapter adapter = new FakeSerialPortAdapter();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        adapter.inputStream = new ControlledInputStream("ok T:21.80 /0.00 B:21.52 /0.00\n");
        adapter.outputStream = out;

        SerialConnection connection = new SerialConnection("COM1", 115200, adapter);
        connection.connect();

        String response = connection.sendCommand("  M105  ");

        assertEquals("ok T:21.80 /0.00 B:21.52 /0.00", response);
        assertEquals(
                "M105" + SerialDefaults.DEFAULT_COMMAND_TERMINATOR,
                out.toString());
    }

    @Test
    void sendCommandConvertsIOExceptionDuringWrite() {
        FakeSerialPortAdapter adapter = new FakeSerialPortAdapter();
        adapter.inputStream = new ByteArrayInputStream(new byte[0]);
        adapter.outputStream = new FailingOutputStream();

        SerialConnection connection = new SerialConnection("COM1", 115200, adapter);
        connection.connect();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> connection.sendCommand("M105"));

        assertEquals("Failed to send command 'M105' to COM1", exception.getMessage());
    }

    @Test
    void sendCommandConvertsTimeoutToIllegalStateException() {
        FakeSerialPortAdapter adapter = new FakeSerialPortAdapter();
        adapter.inputStream = new ControlledInputStream("");
        adapter.outputStream = new ByteArrayOutputStream();

        SerialConnection connection = new SerialConnection("COM1", 115200, adapter);
        connection.connect();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> connection.sendCommand("M105"));

        assertEquals("No response for command 'M105' on COM1", exception.getMessage());
    }

    @Test
    void homeAxesUsesLongRunningCommandTimeout() {
        assertEquals(
                SerialDefaults.LONG_RUNNING_COMMAND_READ_TIMEOUT_MS,
                SerialConnection.readTimeoutMsForCommand("G28"));
        assertEquals(
                SerialDefaults.LONG_RUNNING_COMMAND_READ_TIMEOUT_MS,
                SerialConnection.readTimeoutMsForCommand("g28 x y"));
    }

    @Test
    void regularCommandsUseDefaultReadTimeout() {
        assertEquals(
                SerialDefaults.READ_TIMEOUT_MS,
                SerialConnection.readTimeoutMsForCommand("M105"));
    }

    @Test
    void fileStreamingReadsUsePersistedTransferTimeout() {
        System.setProperty("printerhub.databaseFile", tempDir.resolve("serial-transfer-settings.db").toString());
        new DatabaseInitializer().initialize();
        new SerialTransferSettingsStore().save(new SerialTransferSettings(
                5, // sdUploadBatchSize
                1, // sdUploadMinBatchSize
                1, // sdUploadBatchUpgradeStep
                1, // sdUploadBatchDowngradeStep
                200, // sdUploadStableLinesForUpgrade
                50, // sdUploadResendWindowLines
                1, // sdUploadResendThresholdForDowngrade
                3, // sdUploadRecoveryThresholdForMinBatch
                2, // sdUploadRecoveryWindowMultiplier
                100, // sdUploadMaxErrors
                10, // sdUploadMaxConsecutiveIdenticalResends
                5, // sdUploadMinPerformancePercent
                3, // sdUploadMaxRetriesPerLine
                1, // fileStreamingReadTimeoutMs
                0, // fileStreamingQuietPeriodMs
                0, // fileStreamingReadActivitySleepMs
                0, // fileStreamingReadIdleSleepMs
                15 // fileStreamingRecoveryReplayDelayMs
        ));

        FakeSerialPortAdapter adapter = new FakeSerialPortAdapter();
        adapter.inputStream = new ControlledInputStream("");
        adapter.outputStream = new ByteArrayOutputStream();
        SerialConnection connection = new SerialConnection("COM1", 115200, adapter);
        connection.connect();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> connection.readRawResponse(SerialIOMode.FILE_STREAMING));

        assertNotNull(exception.getCause());
        assertEquals("No response received from printer within 1 ms.", exception.getCause().getMessage());
    }

    @Test
    void sendCommandConvertsReadIOException() {
        FakeSerialPortAdapter adapter = new FakeSerialPortAdapter();
        adapter.inputStream = new FailingInputStream();
        adapter.outputStream = new ByteArrayOutputStream();

        SerialConnection connection = new SerialConnection("COM1", 115200, adapter);
        connection.connect();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> connection.sendCommand("M105"));

        assertEquals("Failed to send command 'M105' to COM1", exception.getMessage());
        assertNotNull(exception.getCause());
        assertInstanceOf(IOException.class, exception.getCause());
    }

    @Test
    void disconnectClosesStreamsAndPort() {
        FakeSerialPortAdapter adapter = new FakeSerialPortAdapter();
        TrackableInputStream in = new TrackableInputStream("ok\n");
        TrackableOutputStream out = new TrackableOutputStream();

        adapter.inputStream = in;
        adapter.outputStream = out;

        SerialConnection connection = new SerialConnection("COM1", 115200, adapter);
        connection.connect();

        connection.disconnect();

        assertFalse(connection.isConnected());
        assertTrue(in.closed.get());
        assertTrue(out.closed.get());
        assertEquals(1, adapter.closePortCalls);
    }

    @Test
    void disconnectToleratesCloseFailures() {
        FakeSerialPortAdapter adapter = new FakeSerialPortAdapter();
        adapter.inputStream = new CloseFailingInputStream();
        adapter.outputStream = new CloseFailingOutputStream();

        SerialConnection connection = new SerialConnection("COM1", 115200, adapter);
        connection.connect();

        assertDoesNotThrow(connection::disconnect);
    }

    @Test
    void disconnectToleratesPortCloseFailure() {
        FakeSerialPortAdapter adapter = new FakeSerialPortAdapter();
        adapter.inputStream = new ByteArrayInputStream(new byte[0]);
        adapter.outputStream = new ByteArrayOutputStream();
        adapter.throwOnClosePort = true;

        SerialConnection connection = new SerialConnection("COM1", 115200, adapter);
        connection.connect();

        assertDoesNotThrow(connection::disconnect);
    }

    @Test
    void isConnectedReturnsFalseBeforeConnectAndAfterDisconnect() {
        FakeSerialPortAdapter adapter = new FakeSerialPortAdapter();
        adapter.inputStream = new ByteArrayInputStream(new byte[0]);
        adapter.outputStream = new ByteArrayOutputStream();

        SerialConnection connection = new SerialConnection("COM1", 115200, adapter);

        assertFalse(connection.isConnected());

        connection.connect();
        assertTrue(connection.isConnected());

        connection.disconnect();
        assertFalse(connection.isConnected());
    }

    private static final class FakeSerialPortAdapter implements SerialPortAdapter {
        private int baudRate;
        private int numDataBits;
        private int numStopBits;
        private int parity;
        private int timeoutMode;
        private int readTimeout;
        private int writeTimeout;

        private int openPortCalls;
        private int closePortCalls;

        private boolean open;
        private boolean openPortResult = true;
        private boolean throwOnGetInputStream;
        private boolean throwOnClosePort;

        private InputStream inputStream;
        private OutputStream outputStream;

        @Override
        public void setBaudRate(int baudRate) {
            this.baudRate = baudRate;
        }

        @Override
        public void setNumDataBits(int numDataBits) {
            this.numDataBits = numDataBits;
        }

        @Override
        public void setNumStopBits(int numStopBits) {
            this.numStopBits = numStopBits;
        }

        @Override
        public void setParity(int parity) {
            this.parity = parity;
        }

        @Override
        public void setComPortTimeouts(int mode, int readTimeout, int writeTimeout) {
            this.timeoutMode = mode;
            this.readTimeout = readTimeout;
            this.writeTimeout = writeTimeout;
        }

        @Override
        public boolean openPort() {
            openPortCalls++;
            open = openPortResult;
            return openPortResult;
        }

        @Override
        public boolean closePort() {
            closePortCalls++;
            if (throwOnClosePort) {
                throw new IllegalStateException("close failed");
            }
            open = false;
            return true;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public InputStream getInputStream() {
            if (throwOnGetInputStream) {
                throw new IllegalStateException("input stream failed");
            }
            return inputStream;
        }

        @Override
        public OutputStream getOutputStream() {
            return outputStream;
        }

        @Override
        public String getSystemPortName() {
            return "COM1";
        }
    }

    private static final class ControlledInputStream extends InputStream {
        private final byte[] data;
        private int index;

        private ControlledInputStream(String text) {
            this.data = text.getBytes();
        }

        @Override
        public int available() {
            return Math.max(0, data.length - index);
        }

        @Override
        public int read() {
            if (index >= data.length) {
                return -1;
            }
            return data[index++];
        }
    }

    private static final class FailingInputStream extends InputStream {
        @Override
        public int available() throws IOException {
            throw new java.io.InterruptedIOException("interrupted");
        }

        @Override
        public int read() {
            return -1;
        }
    }

    private static final class FailingOutputStream extends OutputStream {
        @Override
        public void write(int b) throws IOException {
            throw new IOException("write failed");
        }
    }

    private static final class TrackableInputStream extends InputStream {
        private final byte[] data;
        private int index;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private TrackableInputStream(String text) {
            this.data = text.getBytes();
        }

        @Override
        public int available() {
            return Math.max(0, data.length - index);
        }

        @Override
        public int read() {
            if (index >= data.length) {
                return -1;
            }
            return data[index++];
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }

    private static final class TrackableOutputStream extends ByteArrayOutputStream {
        private final AtomicBoolean closed = new AtomicBoolean(false);

        @Override
        public void close() throws IOException {
            super.close();
            closed.set(true);
        }
    }

    private static final class CloseFailingInputStream extends InputStream {
        @Override
        public int read() {
            return -1;
        }

        @Override
        public void close() throws IOException {
            throw new IOException("close failed");
        }
    }

    private static final class CloseFailingOutputStream extends OutputStream {
        @Override
        public void write(int b) {
            // nothing
        }

        @Override
        public void close() throws IOException {
            throw new IOException("close failed");
        }
    }
}
