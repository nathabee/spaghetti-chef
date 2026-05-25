package printerhub.camera;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class CameraMonitoringScheduler implements AutoCloseable {

    private final CameraMonitoringService monitoringService;
    private final ScheduledExecutorService executorService;
    private final ConcurrentMap<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public CameraMonitoringScheduler(
            CameraMonitoringService monitoringService,
            ScheduledExecutorService executorService) {
        this.monitoringService = Objects.requireNonNull(monitoringService, "monitoringService");
        this.executorService = Objects.requireNonNull(executorService, "executorService");
    }

    public void startMonitoring(String printerId, long cameraJobId, long intervalSeconds) {
        String normalizedPrinterId = requirePrinterId(printerId);
        long safeCameraJobId = requireCameraJobId(cameraJobId);
        long safeIntervalSeconds = requirePositiveInterval(intervalSeconds);

        stopMonitoring(normalizedPrinterId);

        CameraMonitoringTask task = monitoringService.createTask(normalizedPrinterId, safeCameraJobId);

        ScheduledFuture<?> future = executorService.scheduleWithFixedDelay(
                task,
                0,
                safeIntervalSeconds,
                TimeUnit.SECONDS);

        scheduledTasks.put(normalizedPrinterId, future);
    }

    public void stopMonitoring(String printerId) {
        String normalizedPrinterId = requirePrinterId(printerId);

        ScheduledFuture<?> future = scheduledTasks.remove(normalizedPrinterId);
        if (future != null) {
            future.cancel(true);
        }
    }

    public void stopAll() {
        for (String printerId : new LinkedHashSet<>(scheduledTasks.keySet())) {
            stopMonitoring(printerId);
        }
    }

    public boolean isMonitoring(String printerId) {
        String normalizedPrinterId = requirePrinterId(printerId);
        ScheduledFuture<?> future = scheduledTasks.get(normalizedPrinterId);

        return future != null && !future.isCancelled() && !future.isDone();
    }

    public Set<String> monitoredPrinterIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(scheduledTasks.keySet()));
    }

    @Override
    public void close() {
        stopAll();
        executorService.shutdownNow();
    }

    private static long requirePositiveInterval(long intervalSeconds) {
        if (intervalSeconds <= 0) {
            throw new IllegalArgumentException("camera monitoring intervalSeconds must be greater than zero");
        }

        return intervalSeconds;
    }

    private static long requireCameraJobId(long cameraJobId) {
        if (cameraJobId <= 0L) {
            throw new IllegalArgumentException("cameraJobId must be greater than zero");
        }

        return cameraJobId;
    }

    private static String requirePrinterId(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException("printerId must not be blank");
        }

        return printerId.trim();
    }
}