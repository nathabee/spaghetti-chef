package spaghettichef.camera.analysis;

public final class ExternalCliAnalyzerException extends RuntimeException {

    private final ExternalCliAnalyzerExitCode exitCode;
    private final String stdout;
    private final String stderr;

    public ExternalCliAnalyzerException(
            String message,
            ExternalCliAnalyzerExitCode exitCode,
            String stdout,
            String stderr) {
        super(message);
        this.exitCode = exitCode == null ? ExternalCliAnalyzerExitCode.UNKNOWN : exitCode;
        this.stdout = stdout == null ? "" : stdout;
        this.stderr = stderr == null ? "" : stderr;
    }

    public ExternalCliAnalyzerException(String message, Throwable cause) {
        super(message, cause);
        this.exitCode = ExternalCliAnalyzerExitCode.UNKNOWN;
        this.stdout = "";
        this.stderr = "";
    }

    public ExternalCliAnalyzerExitCode exitCode() {
        return exitCode;
    }

    public String stdout() {
        return stdout;
    }

    public String stderr() {
        return stderr;
    }
}
