package spaghettichef.runtime;

import spaghettichef.OperationMessages;
import spaghettichef.PrinterPort;
import spaghettichef.SerialConnection;
import spaghettichef.config.SerialDefaults;
import spaghettichef.serial.SimulatedPrinterPort;

import java.util.Locale;

public final class PrinterRuntimeNodeFactory {

    private PrinterRuntimeNodeFactory() {
    }

    public static PrinterRuntimeNode create(
            String id,
            String displayName,
            String portName,
            String mode,
            boolean enabled
    ) {
        validateRequired("id", id);
        validateRequired("displayName", displayName);
        validateRequired("portName", portName);
        validateRequired("mode", mode);

        PrinterPort printerPort = createPort(portName, mode);

        return new PrinterRuntimeNode(
                id.trim(),
                displayName.trim(),
                portName.trim(),
                mode.trim(),
                printerPort,
                enabled
        );
    }

    private static PrinterPort createPort(String portName, String mode) {
        String normalizedPortName = portName.trim();
        String normalizedMode = mode.trim().toLowerCase(Locale.ROOT);

        if ("real".equals(normalizedMode)) {
            return new SerialConnection(normalizedPortName, SerialDefaults.DEFAULT_BAUD_RATE);
        }

        if ("sim".equals(normalizedMode)
                || "simulated".equals(normalizedMode)
                || "sim-disconnected".equals(normalizedMode)
                || "sim-timeout".equals(normalizedMode)
                || "sim-error".equals(normalizedMode)) {
            return new SimulatedPrinterPort(normalizedPortName, normalizedMode);
        }

        throw new IllegalArgumentException(OperationMessages.INVALID_PRINTER_MODE);
    }

    private static void validateRequired(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank(fieldName));
        }
    }
}