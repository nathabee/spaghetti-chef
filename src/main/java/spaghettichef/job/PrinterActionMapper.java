package spaghettichef.job;

import spaghettichef.OperationMessages;
import spaghettichef.config.PrinterProtocolDefaults;

public final class PrinterActionMapper {

    public String toWireCommand(PrinterActionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("request"));
        }

        return switch (request.actionType()) {
            case READ_TEMPERATURE -> PrinterProtocolDefaults.COMMAND_READ_TEMPERATURE;
            case READ_POSITION -> PrinterProtocolDefaults.COMMAND_READ_POSITION;
            case READ_FIRMWARE_INFO -> PrinterProtocolDefaults.COMMAND_READ_FIRMWARE_INFO;
            case HOME_AXES -> PrinterProtocolDefaults.COMMAND_HOME_AXES;
            case SET_NOZZLE_TEMPERATURE ->
                    PrinterProtocolDefaults.COMMAND_SET_NOZZLE_TEMPERATURE + " S"
                            + formatRequiredTemperature(request.targetTemperature());
            case SET_BED_TEMPERATURE ->
                    PrinterProtocolDefaults.COMMAND_SET_BED_TEMPERATURE + " S"
                            + formatRequiredTemperature(request.targetTemperature());
            case SET_FAN_SPEED ->
                    PrinterProtocolDefaults.COMMAND_SET_FAN_SPEED + " S"
                            + formatRequiredFanSpeed(request.fanSpeed());
            case TURN_FAN_OFF -> PrinterProtocolDefaults.COMMAND_TURN_FAN_OFF;
        };
    }

    private String formatRequiredTemperature(Double targetTemperature) {
        if (targetTemperature == null) {
            throw new IllegalArgumentException(OperationMessages.TARGET_TEMPERATURE_MUST_NOT_BE_NEGATIVE);
        }
        if (targetTemperature < 0) {
            throw new IllegalArgumentException(OperationMessages.TARGET_TEMPERATURE_MUST_NOT_BE_NEGATIVE);
        }

        if (targetTemperature == Math.rint(targetTemperature)) {
            return String.valueOf(targetTemperature.longValue());
        }

        return String.valueOf(targetTemperature);
    }

    private String formatRequiredFanSpeed(Integer fanSpeed) {
        if (fanSpeed == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("fanSpeed"));
        }
        if (fanSpeed < 0) {
            throw new IllegalArgumentException(OperationMessages.invalidEnumField("fanSpeed", String.valueOf(fanSpeed)));
        }

        return String.valueOf(fanSpeed);
    }
}