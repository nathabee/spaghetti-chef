package spaghettichef.job;

import spaghettichef.OperationMessages;
import spaghettichef.runtime.PrinterRuntimeNode;

public final class PrinterActionGuard {

    private static final int MAX_FAN_SPEED = 255;

    public GuardDecision validateForExecution(
            PrintJob job,
            PrinterRuntimeNode node) {
        if (job == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("job"));
        }
        if (node == null) {
            throw new IllegalArgumentException(OperationMessages.NODE_MUST_NOT_BE_NULL);
        }

        if (!node.enabled()) {
            return GuardDecision.allowReject(
                    JobFailureReason.PRINTER_DISABLED,
                    OperationMessages.PRINTER_NODE_DISABLED);
        }

        if (node.executionInProgress()) {
            return GuardDecision.allowReject(
                    JobFailureReason.PRINTER_BUSY,
                    OperationMessages.PRINTER_BUSY);
        }

        if (job.printerId() == null || job.printerId().isBlank()) {
            return GuardDecision.allowReject(
                    JobFailureReason.PRECONDITION_FAILED,
                    OperationMessages.PRINTER_ID_MUST_NOT_BE_BLANK);
        }

        if (!job.printerId().equals(node.id())) {
            return GuardDecision.allowReject(
                    JobFailureReason.PRECONDITION_FAILED,
                    "Job printerId does not match runtime printer node.");
        }

        return switch (job.type()) {
            case READ_TEMPERATURE,
                    READ_POSITION,
                    READ_FIRMWARE_INFO,
                    HOME_AXES,
                    PRINT_FILE,
                    TURN_FAN_OFF ->
                GuardDecision.allow();

            case SET_NOZZLE_TEMPERATURE,
                    SET_BED_TEMPERATURE ->
                validateTargetTemperature(job.targetTemperature());

            case SET_FAN_SPEED -> validateFanSpeed(job.fanSpeed());
        };
    }

    private GuardDecision validateTargetTemperature(Double targetTemperature) {
        if (targetTemperature == null) {
            return GuardDecision.allowReject(
                    JobFailureReason.INVALID_PARAMETER,
                    "targetTemperature is required for this job type.");
        }

        if (targetTemperature < 0) {
            return GuardDecision.allowReject(
                    JobFailureReason.INVALID_PARAMETER,
                    OperationMessages.TARGET_TEMPERATURE_MUST_NOT_BE_NEGATIVE);
        }

        return GuardDecision.allow();
    }

    private GuardDecision validateFanSpeed(Integer fanSpeed) {
        if (fanSpeed == null) {
            return GuardDecision.allowReject(
                    JobFailureReason.INVALID_PARAMETER,
                    "fanSpeed is required for this job type.");
        }

        if (fanSpeed < 0 || fanSpeed > MAX_FAN_SPEED) {
            return GuardDecision.allowReject(
                    JobFailureReason.INVALID_PARAMETER,
                    "fanSpeed must be between 0 and 255.");
        }

        return GuardDecision.allow();
    }

    public GuardDecision validateForSdUpload(PrinterRuntimeNode node) {
        if (node == null) {
            throw new IllegalArgumentException(OperationMessages.NODE_MUST_NOT_BE_NULL);
        }

        if (!node.enabled()) {
            return GuardDecision.allowReject(
                    JobFailureReason.PRINTER_DISABLED,
                    OperationMessages.PRINTER_NODE_DISABLED);
        }

        if (node.executionInProgress()) {
            return GuardDecision.allowReject(
                    JobFailureReason.PRINTER_BUSY,
                    OperationMessages.PRINTER_BUSY);
        }

        return GuardDecision.allow();
    }

    public record GuardDecision(
            boolean allowed,
            JobFailureReason failureReason,
            String detail) {
        public static GuardDecision allow() {
            return new GuardDecision(true, null, null);
        }

        public static GuardDecision allowReject(
                JobFailureReason failureReason,
                String detail) {
            return new GuardDecision(false, failureReason, detail);
        }
    }
}
