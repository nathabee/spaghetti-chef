package spaghettichef.monitoring;

import spaghettichef.command.SdCardUploadService;
import spaghettichef.job.PrintJob;
import spaghettichef.runtime.PrinterRuntimeNode;
import spaghettichef.SerialFailureType;

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
            SerialFailureType serialFailureType,
            Instant updatedAt) {
    }
}
