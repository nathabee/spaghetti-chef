package printerhub.camera;

import java.util.Objects;

public final class CameraMonitoringService {

    private final CameraCaptureService captureService;

    public CameraMonitoringService(CameraCaptureService captureService) {
        this.captureService = Objects.requireNonNull(captureService, "captureService");
    }

    public CameraCaptureResult capture(String printerId) {
        return captureService.capture(requirePrinterId(printerId));
    }

    public CameraStatus status(String printerId) {
        return captureService.status(requirePrinterId(printerId));
    }

    public CameraMonitoringTask createTask(String printerId) {
        return new CameraMonitoringTask(requirePrinterId(printerId), this);
    }

    private static String requirePrinterId(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException("printerId must not be blank");
        }

        return printerId.trim();
    }
}