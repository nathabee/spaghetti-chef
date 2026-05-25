package spaghettichef.job;

import spaghettichef.OperationMessages;

public final class PrinterActionRequest {

    private final PrinterActionType actionType;
    private final Double targetTemperature;
    private final Integer fanSpeed;

    public PrinterActionRequest(
            PrinterActionType actionType,
            Double targetTemperature,
            Integer fanSpeed
    ) {
        if (actionType == null) {
            throw new IllegalArgumentException(OperationMessages.ACTION_TYPE_MUST_NOT_BE_NULL);
        }

        this.actionType = actionType;
        this.targetTemperature = targetTemperature;
        this.fanSpeed = fanSpeed;
    }

    public static PrinterActionRequest fromJob(PrintJob job) {
        if (job == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("job"));
        }

        return new PrinterActionRequest(
                PrinterActionType.fromJobType(job.type()),
                job.targetTemperature(),
                job.fanSpeed()
        );
    }

    public PrinterActionType actionType() {
        return actionType;
    }

    public Double targetTemperature() {
        return targetTemperature;
    }

    public Integer fanSpeed() {
        return fanSpeed;
    }
}