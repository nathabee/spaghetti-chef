package printerhub.camera;

import java.util.Objects;
import java.util.Optional;

public final class CameraCaptureResult {

    private final boolean success;
    private final CameraFrame frame;
    private final String message;

    private CameraCaptureResult(boolean success, CameraFrame frame, String message) {
        this.success = success;
        this.frame = frame;
        this.message = normalizeNullableText(message);
    }

    public static CameraCaptureResult captured(CameraFrame frame) {
        return new CameraCaptureResult(true, Objects.requireNonNull(frame, "frame"), "camera frame captured");
    }

    public static CameraCaptureResult skipped(String message) {
        return new CameraCaptureResult(false, null, requireText(message, "message"));
    }

    public static CameraCaptureResult failed(String message) {
        return new CameraCaptureResult(false, null, requireText(message, "message"));
    }

    public boolean success() {
        return success;
    }

    public Optional<CameraFrame> frame() {
        return Optional.ofNullable(frame);
    }

    public Optional<String> message() {
        return Optional.ofNullable(message);
    }

    public boolean hasFrame() {
        return frame != null;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}