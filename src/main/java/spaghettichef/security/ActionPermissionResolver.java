package spaghettichef.security;

import java.util.Locale;
import java.util.Optional;

public final class ActionPermissionResolver {
    public Optional<Permission> resolve(String method, String path, String requestBody) {
        String normalizedMethod = normalize(method);
        String normalizedPath = path == null ? "" : path;

        if ("GET".equals(normalizedMethod)) {
            return resolveReadPermission(normalizedPath);
        }

        if (normalizedPath.startsWith("/admin/camera")) {
            return Optional.of(Permission.CAMERA_DATA_MANAGE);
        }
        if ("/printers".equals(normalizedPath)) {
            return Optional.of(Permission.PRINTER_CONFIGURE);
        }
        if (normalizedPath.matches("/printers/[^/]+$")) {
            return Optional.of(Permission.PRINTER_CONFIGURE);
        }
        if (normalizedPath.matches("/printers/[^/]+/(enable|disable)$")) {
            return Optional.of(Permission.PRINTER_CONFIGURE);
        }
        if (normalizedPath.matches("/printers/[^/]+/commands$")) {
            return Optional.of(commandPermission(requestBody));
        }
        if (normalizedPath.matches("/printers/[^/]+/sd-card/files$")) {
            return Optional.of(Permission.SD_REFRESH);
        }
        if (normalizedPath.matches("/printers/[^/]+/sd-card/uploads$")) {
            return Optional.of(Permission.SD_UPLOAD);
        }
        if (normalizedPath.matches("/printers/[^/]+/sd-card/recovery/close-upload$")) {
            return Optional.of(Permission.SD_RECOVERY_CLOSE_UPLOAD);
        }
        if ("/printer-sd-files".equals(normalizedPath)) {
            return Optional.of(Permission.SD_UPLOAD);
        }
        if (normalizedPath.matches("/printer-sd-files/[^/]+/(enable|disable)$")) {
            return Optional.of(Permission.SD_UPLOAD);
        }
        if (normalizedPath.matches("/printer-sd-files/[^/]+$") && "DELETE".equals(normalizedMethod)) {
            return Optional.of(Permission.SD_DELETE);
        }
        if ("/print-files".equals(normalizedPath) || "/print-files/uploads".equals(normalizedPath)) {
            return Optional.of(Permission.MANAGE_PRINT_FILES);
        }
        if ("/jobs".equals(normalizedPath)) {
            return Optional.of(Permission.JOB_CREATE);
        }
        if (normalizedPath.matches("/jobs/[^/]+$") && "DELETE".equals(normalizedMethod)) {
            return Optional.of(Permission.JOB_DELETE);
        }
        if (normalizedPath.matches("/jobs/[^/]+/start$")) {
            return Optional.of(Permission.JOB_START);
        }
        if (normalizedPath.matches("/jobs/[^/]+/pause$")) {
            return Optional.of(Permission.JOB_PAUSE);
        }
        if (normalizedPath.matches("/jobs/[^/]+/resume$")) {
            return Optional.of(Permission.JOB_RESUME);
        }
        if (normalizedPath.matches("/jobs/[^/]+/cancel$")) {
            return Optional.of(Permission.JOB_CANCEL);
        }
        if (normalizedPath.matches("/jobs/[^/]+/restart$")) {
            return Optional.of(Permission.JOB_RESTART);
        }
        if ("/settings/monitoring".equals(normalizedPath)) {
            return Optional.of(Permission.MONITORING_CONFIGURE);
        }
        if ("/settings/serial-transfer".equals(normalizedPath)
                || "/settings/print-files".equals(normalizedPath)) {
            return Optional.of(Permission.SETTINGS_UPDATE);
        }
        if ("/settings/security".equals(normalizedPath) || "/security/roles".equals(normalizedPath)) {
            return Optional.of(Permission.SECURITY_MANAGE);
        }

        return Optional.empty();
    }

    private Optional<Permission> resolveReadPermission(String path) {
        if (path.startsWith("/dashboard") || "/health".equals(path)) {
            return Optional.empty();
        }
        if (path.startsWith("/printers")) {
            return Optional.of(Permission.PRINTER_VIEW);
        }
        if (path.startsWith("/jobs")) {
            return Optional.of(Permission.JOB_VIEW);
        }
        if (path.startsWith("/monitoring")) {
            return Optional.of(Permission.MONITORING_VIEW);
        }
        if (path.startsWith("/operator-audit")) {
            return Optional.of(Permission.SECURITY_VIEW);
        }
        if (path.startsWith("/printer-sd-files") || path.contains("/sd-card")) {
            return Optional.of(Permission.SD_VIEW);
        }
        if (path.startsWith("/print-files")) {
            return Optional.of(Permission.MANAGE_PRINT_FILES);
        }
        if (path.startsWith("/settings")) {
            return Optional.of(Permission.SETTINGS_VIEW);
        }
        if (path.startsWith("/security")) {
            return Optional.of(Permission.SECURITY_VIEW);
        }
        if (path.startsWith("/admin/camera")) {
            return Optional.of(Permission.CAMERA_DATA_MANAGE);
        }

        return Optional.empty();
    }

    private Permission commandPermission(String requestBody) {
        String command = extractCommand(requestBody);
        if (command.isBlank()) {
            return Permission.COMMAND_RAW;
        }
        if (command.startsWith("M105") || command.startsWith("M114") || command.startsWith("M115")) {
            return Permission.COMMAND_READ;
        }
        if (command.startsWith("M104")
                || command.startsWith("M140")
                || command.startsWith("M106")
                || command.startsWith("M107")
                || command.startsWith("G28")) {
            return Permission.COMMAND_SAFE_CONTROL;
        }

        return Permission.COMMAND_RAW;
    }

    private String extractCommand(String requestBody) {
        if (requestBody == null) {
            return "";
        }

        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"command\"\\s*:\\s*\"([^\"]*)\"")
                .matcher(requestBody);
        if (!matcher.find()) {
            return "";
        }

        return matcher.group(1).trim().toUpperCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
