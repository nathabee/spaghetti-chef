package spaghettichef.security;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DangerousActionGuard {
    private static final Pattern BOOLEAN_FIELD_PATTERN = Pattern.compile(
            "\"confirmed\"\\s*:\\s*true",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMAND_FIELD_PATTERN = Pattern.compile(
            "\"command\"\\s*:\\s*\"([^\"]*)\"");

    public Optional<DangerousAction> resolve(String method, String path, String requestBody) {
        String normalizedMethod = normalize(method);
        String normalizedPath = path == null ? "" : path;

        if (normalizedPath.matches("/printers/[^/]+/commands$")) {
            return resolveCommandAction(requestBody);
        }
        if (normalizedPath.matches("/printers/[^/]+/sd-card/uploads$") && "POST".equals(normalizedMethod)) {
            return Optional.of(DangerousAction.FILE_UPLOAD_OVERWRITE);
        }
        if (normalizedPath.matches("/printers/[^/]+/sd-card/recovery/close-upload$") && "POST".equals(normalizedMethod)) {
            return Optional.of(DangerousAction.RECOVERY_CLOSE_UPLOAD);
        }
        if (normalizedPath.matches("/printer-sd-files/[^/]+$") && "DELETE".equals(normalizedMethod)) {
            return Optional.of(DangerousAction.SD_DELETE);
        }
        if (normalizedPath.matches("/jobs/[^/]+/start$") && "POST".equals(normalizedMethod)) {
            return Optional.of(DangerousAction.PRINT_START);
        }
        if (normalizedPath.matches("/jobs/[^/]+/cancel$") && "POST".equals(normalizedMethod)) {
            return Optional.of(DangerousAction.PRINT_CANCEL);
        }

        return Optional.empty();
    }

    public void requireConfirmed(DangerousAction action, String requestBody) {
        if (!isConfirmed(requestBody)) {
            throw new ConfirmationRequiredException(action);
        }
    }

    private Optional<DangerousAction> resolveCommandAction(String requestBody) {
        String command = extractCommand(requestBody);
        if (command.isBlank()) {
            return Optional.of(DangerousAction.RAW_COMMAND);
        }
        if (command.startsWith("M104") || command.startsWith("M109")
                || command.startsWith("M140") || command.startsWith("M190")) {
            return Optional.of(DangerousAction.HEATING);
        }
        if (command.startsWith("G28")) {
            return Optional.of(DangerousAction.HOMING);
        }
        if (command.startsWith("G0") || command.startsWith("G1")) {
            return Optional.of(DangerousAction.MOVEMENT);
        }
        if (command.startsWith("M105") || command.startsWith("M114") || command.startsWith("M115")) {
            return Optional.empty();
        }

        return Optional.of(DangerousAction.RAW_COMMAND);
    }

    private boolean isConfirmed(String requestBody) {
        return requestBody != null && BOOLEAN_FIELD_PATTERN.matcher(requestBody).find();
    }

    private String extractCommand(String requestBody) {
        if (requestBody == null) {
            return "";
        }

        Matcher matcher = COMMAND_FIELD_PATTERN.matcher(requestBody);
        if (!matcher.find()) {
            return "";
        }

        return matcher.group(1).trim().toUpperCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
