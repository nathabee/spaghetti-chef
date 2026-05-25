package spaghettichef.runtime;

import spaghettichef.OperationMessages;
import spaghettichef.api.RemoteApiServer;
import spaghettichef.monitoring.PrinterMonitoringScheduler;
import spaghettichef.persistence.DatabaseInitializer;
import spaghettichef.persistence.MonitoringRules;
import spaghettichef.persistence.MonitoringRulesStore;
import spaghettichef.persistence.PrinterConfigurationStore;
import spaghettichef.persistence.SerialTransferSettings;
import spaghettichef.persistence.SerialTransferSettingsStore;

public final class SpaghettiChefRuntime implements AutoCloseable {

    private final DatabaseInitializer databaseInitializer;
    private final PrinterRegistry printerRegistry;
    private final PrinterRuntimeStateCache stateCache;
    private final PrinterMonitoringScheduler monitoringScheduler;
    private final RemoteApiServer apiServer;
    private final PrinterConfigurationStore printerConfigurationStore;
    private final MonitoringRulesStore monitoringRulesStore;
    private final SerialTransferSettingsStore serialTransferSettingsStore;

    public SpaghettiChefRuntime(
            DatabaseInitializer databaseInitializer,
            PrinterConfigurationStore printerConfigurationStore,
            MonitoringRulesStore monitoringRulesStore,
            SerialTransferSettingsStore serialTransferSettingsStore,
            PrinterRegistry printerRegistry,
            PrinterRuntimeStateCache stateCache,
            PrinterMonitoringScheduler monitoringScheduler,
            RemoteApiServer apiServer
    ) {
        if (databaseInitializer == null) {
            throw new IllegalArgumentException(OperationMessages.DATABASE_INITIALIZER_MUST_NOT_BE_NULL);
        }
        if (printerConfigurationStore == null) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_CONFIGURATION_STORE_MUST_NOT_BE_NULL);
        }
        if (monitoringRulesStore == null) {
            throw new IllegalArgumentException(OperationMessages.MONITORING_RULES_STORE_MUST_NOT_BE_NULL);
        }
        if (serialTransferSettingsStore == null) {
            throw new IllegalArgumentException(OperationMessages.SERIAL_TRANSFER_SETTINGS_STORE_MUST_NOT_BE_NULL);
        }
        if (printerRegistry == null) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_REGISTRY_MUST_NOT_BE_NULL);
        }
        if (stateCache == null) {
            throw new IllegalArgumentException(OperationMessages.STATE_CACHE_MUST_NOT_BE_NULL);
        }
        if (monitoringScheduler == null) {
            throw new IllegalArgumentException(OperationMessages.MONITORING_SCHEDULER_MUST_NOT_BE_NULL);
        }
        if (apiServer == null) {
            throw new IllegalArgumentException(OperationMessages.API_SERVER_MUST_NOT_BE_NULL);
        }

        this.databaseInitializer = databaseInitializer;
        this.printerConfigurationStore = printerConfigurationStore;
        this.monitoringRulesStore = monitoringRulesStore;
        this.serialTransferSettingsStore = serialTransferSettingsStore;
        this.printerRegistry = printerRegistry;
        this.stateCache = stateCache;
        this.monitoringScheduler = monitoringScheduler;
        this.apiServer = apiServer;
    }

    public void start() {
        databaseInitializer.initialize();

        MonitoringRules monitoringRules = monitoringRulesStore.load();
        monitoringRulesStore.save(monitoringRules);
        monitoringScheduler.updateMonitoringRules(monitoringRules);

        SerialTransferSettings serialTransferSettings = serialTransferSettingsStore.load();
        serialTransferSettingsStore.save(serialTransferSettings);

        loadConfiguredPrinters();
        printerRegistry.initialize();
        monitoringScheduler.start();
        apiServer.start();
    }

    private void loadConfiguredPrinters() {
        for (PrinterRuntimeNode node : printerConfigurationStore.findAll()) {
            printerRegistry.register(node);
        }
    }

    @Override
    public void close() {
        apiServer.stop();
        monitoringScheduler.stop();
        printerRegistry.close();
    }

    public PrinterRegistry printerRegistry() {
        return printerRegistry;
    }

    public PrinterRuntimeStateCache stateCache() {
        return stateCache;
    }
}