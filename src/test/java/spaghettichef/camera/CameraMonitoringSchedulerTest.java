package spaghettichef.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import spaghettichef.persistence.CameraEventStore;
import spaghettichef.persistence.CameraSettingsStore;
import spaghettichef.persistence.CameraSnapshotMetadataStore;
import spaghettichef.persistence.DatabaseInitializer;

class CameraMonitoringSchedulerTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-05-18T10:15:30Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @AfterEach
    void clearDatabaseProperty() {
        System.clearProperty("spaghettichef.databaseFile");
    }

    @Test
    void startMonitoringSchedulesPrinter() {
        useDatabase("camera-monitoring-scheduler-start.db");

        FakeScheduledExecutorService executor = new FakeScheduledExecutorService();
        CameraMonitoringScheduler scheduler = new CameraMonitoringScheduler(
                monitoringService(),
                executor);

        scheduler.startMonitoring("printer-1", 1L, 10);

        assertTrue(scheduler.isMonitoring("printer-1"));
        assertEquals(1, scheduler.monitoredPrinterIds().size());
        assertTrue(scheduler.monitoredPrinterIds().contains("printer-1"));
        assertEquals(1, executor.scheduleCount);
        assertEquals(0, executor.lastInitialDelay);
        assertEquals(10, executor.lastDelay);
        assertEquals(TimeUnit.SECONDS, executor.lastTimeUnit);
    }

    @Test
    void startMonitoringTrimsPrinterId() {
        useDatabase("camera-monitoring-scheduler-trim.db");

        FakeScheduledExecutorService executor = new FakeScheduledExecutorService();
        CameraMonitoringScheduler scheduler = new CameraMonitoringScheduler(
                monitoringService(),
                executor);

        scheduler.startMonitoring("  printer-1  ", 1L, 10);

        assertTrue(scheduler.isMonitoring("printer-1"));
        assertTrue(scheduler.monitoredPrinterIds().contains("printer-1"));
    }

    @Test
    void startMonitoringReplacesExistingTaskForPrinter() {
        useDatabase("camera-monitoring-scheduler-replace.db");

        FakeScheduledExecutorService executor = new FakeScheduledExecutorService();
        CameraMonitoringScheduler scheduler = new CameraMonitoringScheduler(
                monitoringService(),
                executor);

        scheduler.startMonitoring("printer-1", 1L, 10);
        FakeScheduledFuture<?> firstFuture = executor.lastFuture;

        scheduler.startMonitoring("printer-1", 2L, 20);

        assertTrue(firstFuture.isCancelled());
        assertTrue(scheduler.isMonitoring("printer-1"));
        assertEquals(1, scheduler.monitoredPrinterIds().size());
        assertEquals(2, executor.scheduleCount);
        assertEquals(20, executor.lastDelay);
    }

    @Test
    void stopMonitoringCancelsPrinterTask() {
        useDatabase("camera-monitoring-scheduler-stop.db");

        FakeScheduledExecutorService executor = new FakeScheduledExecutorService();
        CameraMonitoringScheduler scheduler = new CameraMonitoringScheduler(
                monitoringService(),
                executor);

        scheduler.startMonitoring("printer-1", 1L, 10);
        FakeScheduledFuture<?> future = executor.lastFuture;

        scheduler.stopMonitoring("printer-1");

        assertTrue(future.isCancelled());
        assertFalse(scheduler.isMonitoring("printer-1"));
        assertTrue(scheduler.monitoredPrinterIds().isEmpty());
    }

    @Test
    void stopAllCancelsAllTasks() {
        useDatabase("camera-monitoring-scheduler-stop-all.db");

        FakeScheduledExecutorService executor = new FakeScheduledExecutorService();
        CameraMonitoringScheduler scheduler = new CameraMonitoringScheduler(
                monitoringService(),
                executor);

        scheduler.startMonitoring("printer-1", 1L, 10);
        FakeScheduledFuture<?> firstFuture = executor.lastFuture;

        scheduler.startMonitoring("printer-2", 2L, 10);
        FakeScheduledFuture<?> secondFuture = executor.lastFuture;

        scheduler.stopAll();

        assertTrue(firstFuture.isCancelled());
        assertTrue(secondFuture.isCancelled());
        assertTrue(scheduler.monitoredPrinterIds().isEmpty());
    }

    @Test
    void closeCancelsTasksAndShutsDownExecutor() {
        useDatabase("camera-monitoring-scheduler-close.db");

        FakeScheduledExecutorService executor = new FakeScheduledExecutorService();
        CameraMonitoringScheduler scheduler = new CameraMonitoringScheduler(
                monitoringService(),
                executor);

        scheduler.startMonitoring("printer-1", 1L, 10);
        FakeScheduledFuture<?> future = executor.lastFuture;

        scheduler.close();

        assertTrue(future.isCancelled());
        assertTrue(executor.shutdownNowCalled);
        assertTrue(scheduler.monitoredPrinterIds().isEmpty());
    }

    @Test
    void startMonitoringFailsForBlankPrinterId() {
        useDatabase("camera-monitoring-scheduler-blank.db");

        CameraMonitoringScheduler scheduler = new CameraMonitoringScheduler(
                monitoringService(),
                new FakeScheduledExecutorService());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> scheduler.startMonitoring("   ", 1L, 10));

        assertEquals("printerId must not be blank", exception.getMessage());
    }

    @Test
    void startMonitoringFailsForNonPositiveCameraJobId() {
        useDatabase("camera-monitoring-scheduler-job-id.db");

        CameraMonitoringScheduler scheduler = new CameraMonitoringScheduler(
                monitoringService(),
                new FakeScheduledExecutorService());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> scheduler.startMonitoring("printer-1", 0L, 10));

        assertEquals("cameraJobId must be greater than zero", exception.getMessage());
    }

    @Test
    void startMonitoringFailsForNonPositiveInterval() {
        useDatabase("camera-monitoring-scheduler-interval.db");

        CameraMonitoringScheduler scheduler = new CameraMonitoringScheduler(
                monitoringService(),
                new FakeScheduledExecutorService());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> scheduler.startMonitoring("printer-1", 1L, 0));

        assertEquals("camera monitoring intervalSeconds must be greater than zero", exception.getMessage());
    }

    @Test
    void constructorFailsForNullMonitoringService() {
        assertThrows(
                NullPointerException.class,
                () -> new CameraMonitoringScheduler(null, new FakeScheduledExecutorService()));
    }

    @Test
    void constructorFailsForNullExecutorService() {
        assertThrows(
                NullPointerException.class,
                () -> new CameraMonitoringScheduler(monitoringService(), null));
    }

    private CameraMonitoringService monitoringService() {
        CameraSettingsService settingsService = new CameraSettingsService(
                new CameraSettingsStore(),
                FIXED_CLOCK);

        CameraCaptureService captureService = new CameraCaptureService(
                settingsService,
                new CameraEventStore(),
                new CameraSnapshotMetadataStore(),
                tempDir.resolve("camera-storage"),
                FIXED_CLOCK);

        return new CameraMonitoringService(captureService);
    }

    private void useDatabase(String fileName) {
        Path dbFile = tempDir.resolve(fileName);
        System.setProperty("spaghettichef.databaseFile", dbFile.toString());
        new DatabaseInitializer().initialize();
    }

    private static final class FakeScheduledExecutorService
            extends AbstractExecutorService
            implements ScheduledExecutorService {

        private int scheduleCount;
        private long lastInitialDelay;
        private long lastDelay;
        private TimeUnit lastTimeUnit;
        private FakeScheduledFuture<?> lastFuture;
        private boolean shutdown;
        private boolean shutdownNowCalled;

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(
                Runnable command,
                long initialDelay,
                long delay,
                TimeUnit unit) {
            scheduleCount++;
            lastInitialDelay = initialDelay;
            lastDelay = delay;
            lastTimeUnit = unit;
            lastFuture = new FakeScheduledFuture<>(command);
            return lastFuture;
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            shutdownNowCalled = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            lastFuture = new FakeScheduledFuture<>(command);
            return lastFuture;
        }

        @Override
        public <V> ScheduledFuture<V> schedule(
                java.util.concurrent.Callable<V> callable,
                long delay,
                TimeUnit unit) {
            FakeScheduledFuture<V> future = new FakeScheduledFuture<>(null);
            lastFuture = future;
            return future;
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(
                Runnable command,
                long initialDelay,
                long period,
                TimeUnit unit) {
            lastFuture = new FakeScheduledFuture<>(command);
            return lastFuture;
        }
    }

    private static final class FakeScheduledFuture<V> implements ScheduledFuture<V> {

        private final Runnable command;
        private boolean cancelled;

        private FakeScheduledFuture(Runnable command) {
            this.command = command;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed other) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return cancelled;
        }

        @Override
        public V get() {
            if (command != null) {
                command.run();
            }
            return null;
        }

        @Override
        public V get(long timeout, TimeUnit unit) {
            return get();
        }
    }
}