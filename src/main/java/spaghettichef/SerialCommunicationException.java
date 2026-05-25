package spaghettichef;

public final class SerialCommunicationException extends IllegalStateException {

    private final SerialFailureType failureType;

    public SerialCommunicationException(SerialFailureType failureType, String message) {
        super(message);
        this.failureType = failureType == null ? SerialFailureType.UNKNOWN_SERIAL_FAILURE : failureType;
    }

    public SerialCommunicationException(SerialFailureType failureType, String message, Throwable cause) {
        super(message, cause);
        this.failureType = failureType == null ? SerialFailureType.UNKNOWN_SERIAL_FAILURE : failureType;
    }

    public SerialFailureType failureType() {
        return failureType;
    }
}
