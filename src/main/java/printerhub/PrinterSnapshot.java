package printerhub;

import java.time.Instant;

public final class PrinterSnapshot {

    private final PrinterState state;
    private final Double hotendTemperature;
    private final Double bedTemperature;
    private final String lastResponse;
    private final String errorMessage;
    private final SerialFailureType serialFailureType;
    private final Instant updatedAt;

    private PrinterSnapshot(
            PrinterState state,
            Double hotendTemperature,
            Double bedTemperature,
            String lastResponse,
            String errorMessage,
            SerialFailureType serialFailureType,
            Instant updatedAt
    ) {
        if (updatedAt == null) {
            throw new IllegalArgumentException(OperationMessages.UPDATED_AT_MUST_NOT_BE_NULL);
        }

        this.state = state == null ? PrinterState.UNKNOWN : state;
        this.hotendTemperature = hotendTemperature;
        this.bedTemperature = bedTemperature;
        this.lastResponse = lastResponse;
        this.errorMessage = errorMessage;
        this.serialFailureType = serialFailureType;
        this.updatedAt = updatedAt;
    }

    public static PrinterSnapshot disconnected(Instant updatedAt) {
        return new PrinterSnapshot(
                PrinterState.DISCONNECTED,
                null,
                null,
                null,
                null,
                null,
                updatedAt
        );
    }

    public static PrinterSnapshot connecting(
            Double previousHotendTemperature,
            Double previousBedTemperature,
            String previousResponse,
            Instant updatedAt
    ) {
        return new PrinterSnapshot(
                PrinterState.CONNECTING,
                previousHotendTemperature,
                previousBedTemperature,
                previousResponse,
                null,
                null,
                updatedAt
        );
    }

    public static PrinterSnapshot fromResponse(
            PrinterState state,
            Double hotendTemperature,
            Double bedTemperature,
            String lastResponse,
            Instant updatedAt
    ) {
        PrinterState resolvedState = state == null ? PrinterState.UNKNOWN : state;

        return new PrinterSnapshot(
                resolvedState,
                hotendTemperature,
                bedTemperature,
                lastResponse,
                null,
                null,
                updatedAt
        );
    }

    public static PrinterSnapshot error(
            PrinterState state,
            Double previousHotendTemperature,
            Double previousBedTemperature,
            String lastResponse,
            String errorMessage,
            Instant updatedAt
    ) {
        return error(
                state,
                previousHotendTemperature,
                previousBedTemperature,
                lastResponse,
                errorMessage,
                null,
                updatedAt
        );
    }

    public static PrinterSnapshot error(
            PrinterState state,
            Double previousHotendTemperature,
            Double previousBedTemperature,
            String lastResponse,
            String errorMessage,
            SerialFailureType serialFailureType,
            Instant updatedAt
    ) {
        PrinterState resolvedState = state == null
                ? PrinterState.ERROR
                : state;

        return new PrinterSnapshot(
                resolvedState,
                previousHotendTemperature,
                previousBedTemperature,
                lastResponse,
                errorMessage,
                serialFailureType,
                updatedAt
        );
    }

    public PrinterState state() {
        return state;
    }

    public Double hotendTemperature() {
        return hotendTemperature;
    }

    public Double bedTemperature() {
        return bedTemperature;
    }

    public String lastResponse() {
        return lastResponse;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public SerialFailureType serialFailureType() {
        return serialFailureType;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "PrinterSnapshot{"
                + "state=" + state
                + ", hotendTemperature=" + hotendTemperature
                + ", bedTemperature=" + bedTemperature
                + ", lastResponse='" + lastResponse + '\''
                + ", errorMessage='" + errorMessage + '\''
                + ", serialFailureType=" + serialFailureType
                + ", updatedAt=" + updatedAt
                + '}';
    }
}
