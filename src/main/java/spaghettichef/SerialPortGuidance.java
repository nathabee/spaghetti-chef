package spaghettichef;

import java.util.Locale;

public final class SerialPortGuidance {
    private static final String STABLE_LINUX_SERIAL_PREFIX = "/dev/serial/by-id/";
    private static final String UNSTABLE_LINUX_USB_PREFIX = "/dev/ttyUSB";
    private static final String UNSTABLE_LINUX_ACM_PREFIX = "/dev/ttyACM";

    private SerialPortGuidance() {
    }

    public static boolean stable(String mode, String portName) {
        if (!realMode(mode)) {
            return true;
        }

        String normalizedPortName = normalize(portName);
        return normalizedPortName.startsWith(STABLE_LINUX_SERIAL_PREFIX)
                || normalizedPortName.toUpperCase(Locale.ROOT).matches("COM\\d+");
    }

    public static String warning(String mode, String portName) {
        if (!realMode(mode)) {
            return null;
        }

        String normalizedPortName = normalize(portName);
        if (normalizedPortName.isBlank()) {
            return "Configured serial path is blank.";
        }

        if (normalizedPortName.startsWith(UNSTABLE_LINUX_USB_PREFIX)
                || normalizedPortName.startsWith(UNSTABLE_LINUX_ACM_PREFIX)) {
            return "This Linux USB serial path can change after reconnect or reboot. Prefer /dev/serial/by-id/... when available.";
        }

        if (normalizedPortName.startsWith("/dev/serial/by-id/")) {
            return null;
        }

        if (normalizedPortName.startsWith("/dev/")) {
            return "This device path is accepted, but /dev/serial/by-id/... is easier to identify and debug when available.";
        }

        return null;
    }

    public static String kind(String mode, String portName) {
        if (!realMode(mode)) {
            return "SIMULATED";
        }

        String normalizedPortName = normalize(portName);
        if (normalizedPortName.startsWith(STABLE_LINUX_SERIAL_PREFIX)) {
            return "STABLE_LINUX_BY_ID";
        }
        if (normalizedPortName.startsWith(UNSTABLE_LINUX_USB_PREFIX)
                || normalizedPortName.startsWith(UNSTABLE_LINUX_ACM_PREFIX)) {
            return "UNSTABLE_LINUX_USB";
        }
        if (normalizedPortName.toUpperCase(Locale.ROOT).matches("COM\\d+")) {
            return "WINDOWS_COM";
        }
        if (normalizedPortName.startsWith("/dev/")) {
            return "LINUX_DEVICE_PATH";
        }
        return "CUSTOM";
    }

    private static boolean realMode(String mode) {
        return "real".equalsIgnoreCase(normalize(mode));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
