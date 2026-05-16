package printerhub.monitoring;

import printerhub.OperationMessages;
import printerhub.PrinterSnapshot;
import printerhub.PrinterState;
import printerhub.command.SdCardUploadService;
import printerhub.job.JobState;
import printerhub.job.PrintJob;
import printerhub.job.PrintJobService;
import printerhub.runtime.PrinterRegistry;
import printerhub.runtime.PrinterRuntimeNode;
import printerhub.runtime.PrinterRuntimeStateCache;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class GlobalMonitoringService {

    private static final int RECENT_JOB_LIMIT = 20;
    private static final Set<JobState> RELEVANT_JOB_STATES = Set.of(
            JobState.QUEUED,
            JobState.RUNNING,
            JobState.PAUSED,
            JobState.COMPLETED,
            JobState.FAILED,
            JobState.CANCELLED);

    private final PrinterRegistry printerRegistry;
    private final PrinterRuntimeStateCache stateCache;
    private final PrintJobService printJobService;
    private final SdCardUploadService sdCardUploadService;
    private final Clock clock;

    public GlobalMonitoringService(
            PrinterRegistry printerRegistry,
            PrinterRuntimeStateCache stateCache,
            PrintJobService printJobService,
            SdCardUploadService sdCardUploadService) {
        this(
                printerRegistry,
                stateCache,
                printJobService,
                sdCardUploadService,
                Clock.systemUTC());
    }

    GlobalMonitoringService(
            PrinterRegistry printerRegistry,
            PrinterRuntimeStateCache stateCache,
            PrintJobService printJobService,
            SdCardUploadService sdCardUploadService,
            Clock clock) {
        if (printerRegistry == null) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_REGISTRY_MUST_NOT_BE_NULL);
        }
        if (stateCache == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("stateCache"));
        }
        if (printJobService == null) {
            throw new IllegalArgumentException(OperationMessages.PRINT_JOB_SERVICE_MUST_NOT_BE_NULL);
        }
        if (sdCardUploadService == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("sdCardUploadService"));
        }
        if (clock == null) {
            throw new IllegalArgumentException(OperationMessages.CLOCK_MUST_NOT_BE_NULL);
        }

        this.printerRegistry = printerRegistry;
        this.stateCache = stateCache;
        this.printJobService = printJobService;
        this.sdCardUploadService = sdCardUploadService;
        this.clock = clock;
    }

    public GlobalMonitoringSnapshot snapshot() {
        List<PrinterRuntimeNode> printerNodes = printerRegistry.all().stream()
                .sorted(Comparator.comparing(PrinterRuntimeNode::id))
                .toList();

        List<GlobalMonitoringSnapshot.PrinterRuntime> printers = new ArrayList<>();
        List<SdCardUploadService.UploadProgress> activeUploads = new ArrayList<>();

        int enabledPrinters = 0;
        int busyPrinters = 0;
        int errorPrinters = 0;

        for (PrinterRuntimeNode node : printerNodes) {
            PrinterSnapshot snapshot = stateCache.findByPrinterId(node.id()).orElse(null);
            boolean busy = node.executionInProgress();
            String state = snapshot == null ? "UNKNOWN" : snapshot.state().name();

            if (node.enabled()) {
                enabledPrinters++;
            }
            if (busy) {
                busyPrinters++;
            }
            if (snapshot != null
                    && (snapshot.state() == PrinterState.ERROR || snapshot.state() == PrinterState.DISCONNECTED)) {
                errorPrinters++;
            }

            printers.add(new GlobalMonitoringSnapshot.PrinterRuntime(
                    node,
                    state,
                    busy,
                    node.activeJobId(),
                    snapshot == null ? null : snapshot.errorMessage(),
                    snapshot == null ? null : snapshot.updatedAt()));

            sdCardUploadService.uploadProgress(node.id())
                    .filter(progress -> progress.active() || !"idle".equalsIgnoreCase(progress.state()))
                    .ifPresent(activeUploads::add);
        }

        List<PrintJob> activeJobs = printJobService.findRecent(RECENT_JOB_LIMIT).stream()
                .filter(job -> RELEVANT_JOB_STATES.contains(job.state()))
                .sorted(Comparator.comparing(PrintJob::updatedAt).reversed())
                .toList();

        GlobalMonitoringSnapshot.Summary summary = new GlobalMonitoringSnapshot.Summary(
                printerNodes.size(),
                enabledPrinters,
                printerNodes.size() - enabledPrinters,
                busyPrinters,
                errorPrinters,
                (int) activeJobs.stream().filter(GlobalMonitoringService::isActiveJob).count(),
                (int) activeUploads.stream().filter(SdCardUploadService.UploadProgress::active).count());

        return new GlobalMonitoringSnapshot(
                Instant.now(clock),
                summary,
                List.copyOf(printers),
                activeJobs,
                List.copyOf(activeUploads));
    }

    private static boolean isActiveJob(PrintJob job) {
        return job.state() == JobState.QUEUED
                || job.state() == JobState.RUNNING
                || job.state() == JobState.PAUSED;
    }
}
