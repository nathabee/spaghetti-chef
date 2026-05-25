package spaghettichef.job;

public final class PrinterActionExecutionResult {

    private final boolean success;
    private final String wireCommand;
    private final String response;
    private final String outcome;
    private final JobFailureReason failureReason;
    private final String failureDetail;

    public PrinterActionExecutionResult(
            boolean success,
            String wireCommand,
            String response,
            String outcome,
            JobFailureReason failureReason,
            String failureDetail
    ) {
        this.success = success;
        this.wireCommand = wireCommand;
        this.response = response;
        this.outcome = outcome;
        this.failureReason = failureReason;
        this.failureDetail = failureDetail;
    }

    public static PrinterActionExecutionResult success(
            String wireCommand,
            String response
    ) {
        return new PrinterActionExecutionResult(
                true,
                wireCommand,
                response,
                "SUCCESS",
                null,
                null
        );
    }

    public static PrinterActionExecutionResult failure(
            String wireCommand,
            String response,
            JobFailureReason failureReason,
            String failureDetail
    ) {
        return new PrinterActionExecutionResult(
                false,
                wireCommand,
                response,
                failureReason == null ? "FAILED" : failureReason.name(),
                failureReason,
                failureDetail
        );
    }

    public boolean success() {
        return success;
    }

    public String wireCommand() {
        return wireCommand;
    }

    public String response() {
        return response;
    }

    public String outcome() {
        return outcome;
    }

    public JobFailureReason failureReason() {
        return failureReason;
    }

    public String failureDetail() {
        return failureDetail;
    }
}