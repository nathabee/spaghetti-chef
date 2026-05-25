package spaghettichef.camera;

import java.util.Optional;

public final class NoopCameraDevice implements CameraDevice {

    private final String description;

    public NoopCameraDevice() {
        this("noop-camera");
    }

    public NoopCameraDevice(String description) {
        this.description = normalizeDescription(description);
    }

    @Override
    public Optional<CameraFrame> captureFrame() {
        return Optional.empty();
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String describe() {
        return description;
    }

    @Override
    public void close() {
        // Nothing to close.
    }

    private static String normalizeDescription(String value) {
        if (value == null || value.isBlank()) {
            return "noop-camera";
        }
        return value.trim();
    }
}