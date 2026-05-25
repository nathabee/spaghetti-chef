package spaghettichef.monitoring;

import spaghettichef.OperationMessages;
import spaghettichef.PrinterSnapshot;
import spaghettichef.PrinterState;
import spaghettichef.config.PrinterProtocolDefaults;
import spaghettichef.config.RuntimeDefaults;
import spaghettichef.persistence.MonitoringRules;
import spaghettichef.persistence.PrinterEventStore;
import spaghettichef.persistence.PrinterSnapshotStore;
import spaghettichef.runtime.PrinterRegistry;
import spaghettichef.runtime.PrinterRuntimeNode;
import spaghettichef.runtime.PrinterRuntimeStateCache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PrinterMonitoringScheduler {

    private final PrinterRegistry printerRegistry;
    private final PrinterRuntimeStateCache stateCache;
    private final PrinterEventStore eventStore;
    private final Clock clock;
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    private volatile MonitoringRules monitoringRules;
    private volatile PrinterSnapshotStore snapshotStore;
    private volatile MonitoringEventPolicy eventPolicy;
    private ScheduledExecutorService executorService;

    public PrinterMonitoringScheduler(
            PrinterRegistry printerRegistry,
            PrinterRuntimeStateCache stateCache) {
        this(
                printerRegistry,
                stateCache,
                new PrinterSnapshotStore(MonitoringRules.defaults()),
                new PrinterEventStore(),
                MonitoringRules.defaults());
    }

    public PrinterMonitoringScheduler(
            PrinterRegistry printerRegistry,
            PrinterRuntimeStateCache stateCache,
            PrinterSnapshotStore snapshotStore,
            PrinterEventStore eventStore,
            MonitoringRules monitoringRules) {
        if (printerRegistry == null) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_REGISTRY_MUST_NOT_BE_NULL);
        }
        if (stateCache == null) {
            throw new IllegalArgumentException(OperationMessages.STATE_CACHE_MUST_NOT_BE_NULL);
        }
        if (snapshotStore == null) {
            throw new IllegalArgumentException(OperationMessages.SNAPSHOT_STORE_MUST_NOT_BE_NULL);
        }
        if (eventStore == null) {
            throw new IllegalArgumentException(OperationMessages.EVENT_STORE_MUST_NOT_BE_NULL);
        }
        if (monitoringRules == null) {
            throw new IllegalArgumentException(OperationMessages.MONITORING_RULES_MUST_NOT_BE_NULL);
        }

        this.printerRegistry = printerRegistry;
        this.stateCache = stateCache;
        this.snapshotStore = snapshotStore;
        this.eventStore = eventStore;
        this.monitoringRules = monitoringRules;
        this.clock = Clock.systemUTC();
        this.eventPolicy = new MonitoringEventPolicy(
                clock,
                Duration.ofSeconds(monitoringRules.eventDeduplicationWindowSeconds()));
    }

    public synchronized void updateMonitoringRules(MonitoringRules monitoringRules) {
        if (monitoringRules == null) {
            throw new IllegalArgumentException(OperationMessages.MONITORING_RULES_MUST_NOT_BE_NULL);
        }

        this.monitoringRules = monitoringRules;
        this.snapshotStore = new PrinterSnapshotStore(monitoringRules);
        this.eventPolicy = new MonitoringEventPolicy(
                clock,
                Duration.ofSeconds(monitoringRules.eventDeduplicationWindowSeconds()));

        if (executorService == null || executorService.isShutdown()) {
            return;
        }

        for (PrinterRuntimeNode node : printerRegistry.all()) {
            if (node.enabled()) {
                startMonitoring(node);
            } else {
                stopMonitoring(node.id());
                initializeDisabledPrinter(node);
            }
        }
    }

    public synchronized void start() {
        if (executorService != null && !executorService.isShutdown()) {
            return;
        }

        shuttingDown.set(false);

        Collection<PrinterRuntimeNode> nodes = printerRegistry.all();

        int poolSize = Math.max(
                RuntimeDefaults.MIN_MONITORING_EXECUTOR_POOL_SIZE,
                nodes.size() + RuntimeDefaults.MONITORING_EXECUTOR_EXTRA_THREADS);
        executorService = Executors.newScheduledThreadPool(poolSize);

        for (PrinterRuntimeNode node : nodes) {
            if (node.enabled()) {
                startMonitoring(node);
            } else {
                initializeDisabledPrinter(node);
            }
        }
    }

    public synchronized void startMonitoring(PrinterRuntimeNode node) {
        if (node == null) {
            throw new IllegalArgumentException(OperationMessages.NODE_MUST_NOT_BE_NULL);
        }

        ensureExecutorStarted();

        stopMonitoring(node.id());

        if (!node.enabled()) {
            initializeDisabledPrinter(node);
            return;
        }

        stateCache.initializePrinter(node.id());

        ScheduledFuture<?> future = executorService.scheduleWithFixedDelay(
                new PrinterMonitoringTask(
                        node,
                        stateCache,
                        snapshotStore,
                        eventStore,
                        clock,
                        PrinterProtocolDefaults.DEFAULT_STATUS_COMMAND,
                        shuttingDown::get,
                        eventPolicy,
                        monitoringRules),
                0,
                monitoringRules.pollIntervalSeconds(),
                TimeUnit.SECONDS);

        scheduledTasks.put(node.id(), future);
    }

    public synchronized void stopMonitoring(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            return;
        }

        ScheduledFuture<?> future = scheduledTasks.remove(printerId);

        if (future != null) {
            future.cancel(true);
        }

        eventPolicy.clearPrinter(printerId);
    }

    public synchronized void restartMonitoring(PrinterRuntimeNode node) {
        if (node == null) {
            throw new IllegalArgumentException(OperationMessages.NODE_MUST_NOT_BE_NULL);
        }

        stopMonitoring(node.id());
        startMonitoring(node);
    }

    public synchronized void stop() {
        shuttingDown.set(true);

        for (ScheduledFuture<?> future : scheduledTasks.values()) {
            future.cancel(true);
        }

        scheduledTasks.clear();

        if (executorService == null) {
            return;
        }

        ScheduledExecutorService executorToStop = executorService;
        executorService = null;

        executorToStop.shutdownNow();

        try {
            if (!executorToStop.awaitTermination(2, TimeUnit.SECONDS)) {
                System.err.println(OperationMessages.apiOperationFailed(
                        "Monitoring scheduler did not terminate cleanly within timeout."));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.err.println(OperationMessages.apiOperationFailed(
                    OperationMessages.safeDetail(
                            exception.getMessage(),
                            OperationMessages.UNKNOWN_API_ERROR)));
        }
    }

    private void initializeDisabledPrinter(PrinterRuntimeNode node) {
        stateCache.update(
                node.id(),
                PrinterSnapshot.error(
                        PrinterState.DISCONNECTED,
                        null,
                        null,
                        null,
                        OperationMessages.PRINTER_NODE_DISABLED,
                        Instant.now(clock)));
        eventPolicy.clearPrinter(node.id());
    }

    private void ensureExecutorStarted() {
        if (executorService == null || executorService.isShutdown()) {
            shuttingDown.set(false);
            executorService = Executors.newScheduledThreadPool(
                    RuntimeDefaults.DEFAULT_MONITORING_EXECUTOR_POOL_SIZE);
        }
    }
}