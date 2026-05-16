package printerhub.monitoring;

import printerhub.command.SdCardUploadService;
import printerhub.job.PrintJob;
import printerhub.runtime.PrinterRuntimeNode;

import java.time.Instant;
import java.util.List;

public record GlobalMonitoringSnapshot(
        Instant generatedAt,
        Summary summary,
        List<PrinterRuntime> printers,
        List<PrintJob> activeJobs,
        List<SdCardUploadService.UploadProgress> activeUploads) {

    public record Summary(
            int totalPrinters,
            int enabledPrinters,
            int disabledPrinters,
            int busyPrinters,
            int errorPrinters,
            int activeJobs,
            int activeUploads) {
    }

    public record PrinterRuntime(
            PrinterRuntimeNode printer,
            String state,
            boolean busy,
            String activeJobId,
            String errorMessage,
            Instant updatedAt) {
    }
}
