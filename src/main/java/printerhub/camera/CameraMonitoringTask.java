package printerhub.camera;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class CameraMonitoringTask implements Runnable {

    private final String printerId;
    private final CameraMonitoringService monitoringService;
    private final Clock clock;
    private final AtomicReference<Instant> lastRunAt = new AtomicReference<>();
    private final AtomicReference<CameraCaptureResult> lastResult = new AtomicReference<>();
    private final AtomicReference<String> lastFailure = new AtomicReference<>();

    public CameraMonitoringTask(String printerId, CameraMonitoringService monitoringService) {
        this(printerId, monitoringService, Clock.systemUTC());
    }

    public CameraMonitoringTask(String printerId, CameraMonitoringService monitoringService, Clock clock) {
        this.printerId = requirePrinterId(printerId);
        this.monitoringService = Objects.requireNonNull(monitoringService, "monitoringService");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void run() {
        lastRunAt.set(Instant.now(clock));

        try {
            CameraCaptureResult result = monitoringService.capture(printerId);
            lastResult.set(result);
            lastFailure.set(null);
        } catch (RuntimeException exception) {
            lastFailure.set(exception.getMessage());
        }
    }

    public String printerId() {
        return printerId;
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
}