package spaghettichef.job;

import spaghettichef.OperationMessages;

public final class PrinterResponseClassifier {

    public ResponseClassification classifyResponse(
            String wireCommand,
            String response
    ) {
        String normalizedCommand = safeCommand(wireCommand);
        String normalizedResponse = normalize(response);
        String lower = normalizedResponse.toLowerCase();

        if (normalizedResponse.isBlank()) {
            return ResponseClassification.failure(
                    JobFailureReason.NO_RESPONSE,
                    "No response for command '" + normalizedCommand + "'.",
                    response
            );
        }

        if (lower.contains("timeout")) {
            return ResponseClassification.failure(
                    JobFailureReason.TIMEOUT,
                    "Timeout while executing command '" + normalizedCommand + "': " + normalizedResponse,
                    response
            );
        }

        if (containsPrinterReportedFailure(lower)) {
            return ResponseClassification.failure(
                    JobFailureReason.PRINTER_REPORTED_FAILURE,
                    "Printer reported failure for command '" + normalizedCommand + "': " + normalizedResponse,
                    response
            );
        }

        if (lower.contains("busy") && !lower.contains("ok")) {
            return ResponseClassification.failure(
                    JobFailureReason.PRINTER_BUSY,
                    "Printer still processing command '" + normalizedCommand + "': " + normalizedResponse,
                    response
            );
        }

        if (lower.contains("ok")
                || lower.contains("firmware")
                || lower.contains("x:")
                || lower.contains("y:")
                || lower.contains("z:")
                || lower.contains("t:")) {
            return ResponseClassification.success(response);
        }

        return ResponseClassification.success(response);
    }

    public ResponseClassification classifyException(
            String wireCommand,
            Exception exception
    ) {
        String normalizedCommand = safeCommand(wireCommand);
        String message = OperationMessages.safeDetail(
                exception == null ? null : exception.getMessage(),
                JobFailureReason.UNKNOWN.name()
        );
        String lower = message.toLowerCase();

        if (lower.contains("timeout")) {
            return ResponseClassification.failure(
                    JobFailureReason.TIMEOUT,
                    "Timeout while executing command '" + normalizedCommand + "': " + message,
                    null
            );
        }

        if (lower.contains("no response")) {
            return ResponseClassification.failure(
                    JobFailureReason.NO_RESPONSE,
                    "No response for command '" + normalizedCommand + "': " + message,
                    null
            );
        }

        if (lower.contains("disconnected")
                || lower.contains("not connected")
                || lower.contains("not open")
                || lower.contains("failed to open serial port")) {
            return ResponseClassification.failure(
                    JobFailureReason.PRINTER_DISCONNECTED,
                    "Printer communication failed for command '" + normalizedCommand + "': " + message,
                    null
            );
        }

        if (lower.contains("busy")) {
            return ResponseClassification.failure(
                    JobFailureReason.PRINTER_BUSY,
                    "Printer busy while executing command '" + normalizedCommand + "': " + message,
                    null
            );
        }

        if (lower.contains("parameter")) {
            return ResponseClassification.failure(
                    JobFailureReason.INVALID_PARAMETER,
                    "Invalid parameter while executing command '" + normalizedCommand + "': " + message,
                    null
            );
        }

        return ResponseClassification.failure(
                JobFailureReason.COMMUNICATION_FAILURE,
                "Communication failure while executing command '" + normalizedCommand + "': " + message,
                null
        );
    }

    private boolean containsPrinterReportedFailure(String lowerResponse) {
        return lowerResponse.contains("error:")
                || lowerResponse.contains("failed")
                || lowerResponse.contains("halted")
                || lowerResponse.contains("kill() called")
                || lowerResponse.contains("homing failed");
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.trim();
    }

    private String safeCommand(String wireCommand) {
        if (wireCommand == null || wireCommand.isBlank()) {
            return "unknown";
        }

        return wireCommand.trim();
    }

    public record ResponseClassification(
            boolean success,
            JobFailureReason failureReason,
            String detail,
            String response
    ) {
        public static ResponseClassification success(String response) {
            return new ResponseClassification(true, null, null, response);
        }

        public static ResponseClassification failure(
                JobFailureReason failureReason,
                String detail,
                String response
        ) {
            return new ResponseClassification(false, failureReason, detail, response);
        }
    }
}
