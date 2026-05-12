package printerhub;

import printerhub.api.RemoteApiServer;
import printerhub.command.PrinterCommandService;
import printerhub.command.SdCardService;
import printerhub.config.RuntimeDefaults;
import printerhub.job.AsyncPrintJobExecutor;
import printerhub.job.PrintFileService;
import printerhub.job.PrintJobExecutionService;
import printerhub.job.PrintJobService;
import printerhub.job.PrinterSdFileService;
import printerhub.job.PrinterActionGuard;
import printerhub.job.PrinterActionMapper;
import printerhub.monitoring.PrinterMonitoringScheduler;
import printerhub.persistence.DatabaseInitializer;
import printerhub.persistence.MonitoringRulesStore;
import printerhub.persistence.PrintFileSettingsStore;
import printerhub.persistence.PrintFileStore;
import printerhub.persistence.PrintJobStore;
import printerhub.persistence.PrinterConfigurationStore;
import printerhub.persistence.PrinterEventStore;
import printerhub.persistence.PrintJobExecutionStepStore;
import printerhub.persistence.PrinterSdFileStore;
import printerhub.runtime.PrinterHubRuntime;
import printerhub.runtime.PrinterRegistry;
import printerhub.runtime.PrinterRuntimeStateCache;
import printerhub.command.SdCardUploadService;
import java.util.concurrent.CountDownLatch;
import printerhub.persistence.SerialTransferSettingsStore;

public final class Main {

        private Main() {
        }

        public static void main(String[] args) throws InterruptedException {
                try {
                        int apiPort = readIntProperty(
                                        RuntimeDefaults.API_PORT_PROPERTY,
                                        RuntimeDefaults.DEFAULT_API_PORT);

                        PrinterRegistry printerRegistry = new PrinterRegistry();
                        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
                        DatabaseInitializer databaseInitializer = new DatabaseInitializer();
                        PrinterConfigurationStore printerConfigurationStore = new PrinterConfigurationStore();
                        MonitoringRulesStore monitoringRulesStore = new MonitoringRulesStore();
                        SerialTransferSettingsStore serialTransferSettingsStore = new SerialTransferSettingsStore();
                        PrintFileSettingsStore printFileSettingsStore = new PrintFileSettingsStore();
                        PrinterEventStore printerEventStore = new PrinterEventStore();
                        PrintFileStore printFileStore = new PrintFileStore();
                        PrinterSdFileStore printerSdFileStore = new PrinterSdFileStore();
                        PrintJobStore printJobStore = new PrintJobStore();

                        PrinterCommandService printerCommandService = new PrinterCommandService(printerEventStore);
                        SdCardService sdCardService = new SdCardService(printerEventStore);

                        PrinterMonitoringScheduler monitoringScheduler = new PrinterMonitoringScheduler(
                                        printerRegistry,
                                        stateCache);

                        PrintJobService printJobService = new PrintJobService(
                                        printJobStore,
                                        printerEventStore);
                        PrintFileService printFileService = new PrintFileService(
                                        printFileStore,
                                        printFileSettingsStore,
                                        java.time.Clock.systemUTC());
                        PrinterSdFileService printerSdFileService = new PrinterSdFileService(
                                        printerSdFileStore,
                                        printFileStore);

                        PrinterActionGuard printerActionGuard = new PrinterActionGuard();

                        SdCardUploadService sdCardUploadService = new SdCardUploadService(
                                        printerRegistry,
                                        monitoringScheduler,
                                        printerActionGuard,
                                        printFileService,
                                        sdCardService,
                                        printerSdFileService,
                                        printerEventStore,
                                        monitoringRulesStore,
                                        serialTransferSettingsStore);

                        PrintJobExecutionService printJobExecutionService = new PrintJobExecutionService(
                                        printJobService,
                                        printerRegistry,
                                        monitoringScheduler,
                                        printerActionGuard,
                                        new PrinterActionMapper(),
                                        new PrintJobExecutionStepStore());

                        AsyncPrintJobExecutor asyncPrintJobExecutor = new AsyncPrintJobExecutor(
                                        printJobService,
                                        printerRegistry,
                                        printerActionGuard,
                                        printJobExecutionService);

                        RemoteApiServer apiServer = new RemoteApiServer(
                                        apiPort,
                                        printerRegistry,
                                        stateCache,
                                        monitoringScheduler,
                                        printerConfigurationStore,
                                        monitoringRulesStore,
                                        printFileSettingsStore,
                                        serialTransferSettingsStore,
                                        printerEventStore,
                                        printerCommandService,
                                        sdCardService,
                                        sdCardUploadService,
                                        printFileService,
                                        printerSdFileService,
                                        printJobService,
                                        asyncPrintJobExecutor,
                                        new PrintJobExecutionStepStore());

                        PrinterHubRuntime runtime = new PrinterHubRuntime(
                                        databaseInitializer,
                                        printerConfigurationStore,
                                        monitoringRulesStore,
                                        serialTransferSettingsStore,
                                        printerRegistry,
                                        stateCache,
                                        monitoringScheduler,
                                        apiServer);

                        Runtime.getRuntime().addShutdownHook(new Thread(runtime::close));

                        runtime.start();

                        System.out.println(OperationMessages.localRuntimeStarted());
                        System.out.println(OperationMessages.healthEndpoint(apiPort));
                        System.out.println(OperationMessages.printersEndpoint(apiPort));
                        System.out.println(OperationMessages.monitoringSettingsEndpoint(apiPort));

                        new CountDownLatch(1).await();
                } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        System.err.println(OperationMessages.runtimeStartupFailed(
                                        OperationMessages.safeDetail(
                                                        exception.getMessage(),
                                                        OperationMessages.UNKNOWN_STARTUP_ERROR)));
                        throw exception;
                } catch (IllegalArgumentException exception) {
                        System.err.println(OperationMessages.runtimeStartupFailed(
                                        OperationMessages.safeDetail(
                                                        exception.getMessage(),
                                                        OperationMessages.UNKNOWN_STARTUP_ERROR)));
                        System.exit(RuntimeDefaults.ERROR_EXIT_CODE);
                } catch (Exception exception) {
                        System.err.println(OperationMessages.runtimeStartupFailed(
                                        OperationMessages.safeDetail(
                                                        exception.getMessage(),
                                                        OperationMessages.UNKNOWN_STARTUP_ERROR)));
                        exception.printStackTrace(System.err);
                        System.exit(RuntimeDefaults.ERROR_EXIT_CODE);
                }
        }

        private static int readIntProperty(String key, int defaultValue) {
                String value = System.getProperty(key);

                if (value == null || value.isBlank()) {
                        return defaultValue;
                }

                try {
                        return Integer.parseInt(value);
                } catch (NumberFormatException exception) {
                        throw new IllegalArgumentException(
                                        OperationMessages.invalidIntegerSystemProperty(key, value),
                                        exception);
                }
        }
}
