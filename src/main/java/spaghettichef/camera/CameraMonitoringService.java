package spaghettichef.camera;

import java.util.Objects;

public final class CameraMonitoringService {

    private final CameraCaptureService captureService;

    public CameraMonitoringService(CameraCaptureService captureService) {
        this.captureService = Objects.requireNonNull(captureService, "captureService");
    }

    public CameraCaptureResult captureForCameraJob(String printerId, long cameraJobId) {
        return captureService.captureForCameraJob(
                requirePrinterId(printerId),
                requireCameraJobId(cameraJobId));
    }

    public CameraStatus status(String printerId) {
        return captureService.status(requirePrinterId(printerId));
    }

    public CameraMonitoringTask createTask(String printerId, long cameraJobId) {
        return new CameraMonitoringTask(
                requirePrinterId(printerId),
                requireCameraJobId(cameraJobId),
                this);
    }

    private static String requirePrinterId(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException("printerId must not be blank");
        }

        return printerId.trim();
    }

    private static long requireCameraJobId(long cameraJobId) {
        if (cameraJobId <= 0L) {
            throw new IllegalArgumentException("cameraJobId must be greater than zero");
        }

        return cameraJobId;
    }
}