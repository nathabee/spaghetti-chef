package spaghettichef.camera.analysis;

public final class RustCliAnalyzerException extends RuntimeException {

    private final RustCliAnalyzerExitCode exitCode;
    private final String stdout;
    private final String stderr;

    public RustCliAnalyzerException(
            String message,
            RustCliAnalyzerExitCode exitCode,
            String stdout,
            String stderr) {
        super(message);
        this.exitCode = exitCode == null ? RustCliAnalyzerExitCode.UNKNOWN : exitCode;
        this.stdout = stdout == null ? "" : stdout;
        this.stderr = stderr == null ? "" : stderr;
    }

    public RustCliAnalyzerException(String message, Throwable cause) {
        super(message, cause);
        this.exitCode = RustCliAnalyzerExitCode.UNKNOWN;
        this.stdout = "";
        this.stderr = "";
    }

    public RustCliAnalyzerExitCode exitCode() {
        return exitCode;
    }

    public String stdout() {
        return stdout;
    }

    public String stderr() {
        return stderr;
    }
}
