package printerhub;

import java.time.Instant;

public final class PrinterHubLog {

    private PrinterHubLog() {
    }

    public static void info(String message) {
        System.out.println(format("INFO", message));
    }

    public static void warn(String message) {
        System.err.println(format("WARN", message));
    }

    public static void error(String message) {
        System.err.println(format("ERROR", message));
    }

    private static String format(String level, String message) {
        return Instant.now() + " [PrinterHub] " + level + " " + safeMessage(message);
    }

    private static String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "No log message provided";
        }
        String trimmed = message.trim();
        if (trimmed.startsWith("[PrinterHub]")) {
            return trimmed.substring("[PrinterHub]".length()).trim();
        }
        return trimmed;
    }
}
