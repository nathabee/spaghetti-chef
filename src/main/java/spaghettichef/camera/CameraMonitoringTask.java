package spaghettichef.camera;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class CameraMonitoringTask implements Runnable {

    private final String printerId;
    private final long cameraJobId;
    private final CameraMonitoringService monitoringService;
    private final Clock clock;
    private final AtomicReference<Instant> lastRunAt = new AtomicReference<>();
    private final AtomicReference<CameraCaptureResult> lastResult = new AtomicReference<>();
    private final AtomicReference<String> lastFailure = new AtomicReference<>();

    public CameraMonitoringTask(String printerId, long cameraJobId, CameraMonitoringService monitoringService) {
        this(printerId, cameraJobId, monitoringService, Clock.systemUTC());
    }

    public CameraMonitoringTask(
            String printerId,
            long cameraJobId,
            CameraMonitoringService monitoringService,
            Clock clock) {
        this.printerId = requirePrinterId(printerId);
        this.cameraJobId = requireCameraJobId(cameraJobId);
        this.monitoringService = Objects.requireNonNull(monitoringService, "monitoringService");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void run() {
        lastRunAt.set(Instant.now(clock));

        try {
            CameraCaptureResult result = monitoringService.captureForCameraJob(printerId, cameraJobId);
            lastResult.set(result);
            lastFailure.set(null);
        } catch (RuntimeException exception) {
            lastFailure.set(exception.getMessage());
        }
    }

    public String printerId() {
        return printerId;
    }

    public long cameraJobId() {
        return cameraJobId;
    }

    public Optional<Instant> lastRunAt() {
        return Optional.ofNullable(lastRunAt.get());
    }

    public Optional<CameraCaptureResult> lastResult() {
        return Optional.ofNullable(lastResult.get());
    }

    public Optional<String> lastFailure() {
        return Optional.ofNullable(lastFailure.get());
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