package spaghettichef.serial;

import java.io.InputStream;
import java.io.OutputStream;

public interface SerialPortAdapter {
    int ONE_STOP_BIT = 1;
    int NO_PARITY = 0;
    int TIMEOUT_NONBLOCKING = 0;
    int EIGHT_DATA_BITS = 8; 

    void setBaudRate(int baudRate);

    void setNumDataBits(int numDataBits);

    void setNumStopBits(int numStopBits);

    void setParity(int parity);

    void setComPortTimeouts(int mode, int readTimeout, int writeTimeout);

    boolean openPort();

    boolean closePort();

    boolean isOpen();

    InputStream getInputStream();

    OutputStream getOutputStream();

    String getSystemPortName();
}