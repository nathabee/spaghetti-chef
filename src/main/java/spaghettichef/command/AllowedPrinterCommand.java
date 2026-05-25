package spaghettichef.command;

import spaghettichef.OperationMessages;

import java.util.Locale;

public enum AllowedPrinterCommand {
    M105(false),
    M114(false),
    M115(false),
    G28(false),
    M104(true),
    M140(true),
    M106(false),
    M107(false);

    private final boolean requiresTargetTemperature;

    AllowedPrinterCommand(boolean requiresTargetTemperature) {
        this.requiresTargetTemperature = requiresTargetTemperature;
    }

    public static AllowedPrinterCommand parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.COMMAND_MUST_NOT_BE_BLANK);
        }

        try {
            return AllowedPrinterCommand.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    OperationMessages.invalidPrinterCommand(value),
                    exception
            );
        }
    }

    public String buildWireCommand(Double targetTemperature) {
        if (!requiresTargetTemperature) {
            return name();
        }

        if (targetTemperature == null) {
            throw new IllegalArgumentException(
                    OperationMessages.targetTemperatureRequired(name())
            );
        }

        if (targetTemperature < 0) {
            throw new IllegalArgumentException(
                    OperationMessages.TARGET_TEMPERATURE_MUST_NOT_BE_NEGATIVE
            );
        }

        return name() + " S" + formatTemperature(targetTemperature);
    }

    private String formatTemperature(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }

        return String.valueOf(value);
    }
}