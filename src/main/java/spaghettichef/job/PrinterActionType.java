package spaghettichef.job;

import spaghettichef.OperationMessages;

public enum PrinterActionType {
    READ_TEMPERATURE,
    READ_POSITION,
    READ_FIRMWARE_INFO,
    HOME_AXES,
    SET_NOZZLE_TEMPERATURE,
    SET_BED_TEMPERATURE,
    SET_FAN_SPEED,
    TURN_FAN_OFF;

    public static PrinterActionType fromJobType(JobType jobType) {
        if (jobType == null) {
            throw new IllegalArgumentException(OperationMessages.JOB_TYPE_MUST_NOT_BE_NULL);
        }

        return switch (jobType) {
            case READ_TEMPERATURE -> READ_TEMPERATURE;
            case READ_POSITION -> READ_POSITION;
            case READ_FIRMWARE_INFO -> READ_FIRMWARE_INFO;
            case HOME_AXES -> HOME_AXES;
            case SET_NOZZLE_TEMPERATURE -> SET_NOZZLE_TEMPERATURE;
            case SET_BED_TEMPERATURE -> SET_BED_TEMPERATURE;
            case SET_FAN_SPEED -> SET_FAN_SPEED;
            case TURN_FAN_OFF -> TURN_FAN_OFF;
            case PRINT_FILE -> throw new IllegalArgumentException(
                    OperationMessages.invalidEnumField("type", jobType.name()));
        };
    }
}
