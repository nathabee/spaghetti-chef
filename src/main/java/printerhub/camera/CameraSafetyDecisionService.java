package printerhub.camera;

import printerhub.OperationMessages;
import printerhub.job.AutonomousPrintControlService;
import printerhub.job.PrintJob;
import printerhub.job.PrintJobService;
import printerhub.persistence.CameraAnalysisSample;
import printerhub.persistence.CameraAnalysisSampleStore;
import printerhub.persistence.CameraEventStore;
import printerhub.persistence.CameraSettings;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class CameraSafetyDecisionService {

    private final CameraSettingsService settingsService;
    private final CameraAnalysisSampleStore sampleStore;
    private final CameraEventStore cameraEventStore;
    private final PrintJobService printJobService;
    private final AutonomousPrintControlService autonomousPrintControlService;

    public CameraSafetyDecisionService(
            CameraSettingsService settingsService,
            CameraAnalysisSampleStore sampleStore,
            CameraEventStore cameraEventStore,
            PrintJobService printJobService,
            AutonomousPrintControlService autonomousPrintControlService) {
        this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
        this.sampleStore = Objects.requireNonNull(sampleStore, "sampleStore");
        this.cameraEventStore = Objects.requireNonNull(cameraEventStore, "cameraEventStore");
        this.printJobService = Objects.requireNonNull(printJobService, "printJobService");
        this.autonomousPrintControlService = Objects.requireNonNull(
                autonomousPrintControlService,
                "autonomousPrintControlService");
    }

    public void evaluate(CameraAnalysisSample sample) {
        Objects.requireNonNull(sample, "sample");

        if (sample.suspected()) {
            cameraEventStore.record(
                    sample.printerId(),
                    OperationMessages.EVENT_SPAGHETTI_SUSPECTED,
                    suspectedMessage(sample),
                    sample.confidence());
        }

        CameraSettings settings = settingsService.load(sample.printerId());
        if (!settings.safetyEnabled()) {
            return;
        }

        if (!hasRequiredConfirmations(sample, settings)) {
            return;
        }

        cameraEventStore.record(
                sample.printerId(),
                OperationMessages.EVENT_SPAGHETTI_CONFIRMED,
                confirmedMessage(sample, settings),
                sample.confidence());

        if (!settings.pauseOnConfirmedSpaghetti()) {
            cameraEventStore.record(
                    sample.printerId(),
                    OperationMessages.EVENT_CAMERA_SAFETY_ACTION_SKIPPED,
                    "Spaghetti confirmed but automatic pause is disabled",
                    sample.confidence());
            return;
        }

        Optional<PrintJob> activeJob = printJobService.findActivePrintFileJobByPrinterId(sample.printerId());
        if (activeJob.isEmpty()) {
            cameraEventStore.record(
                    sample.printerId(),
                    OperationMessages.EVENT_CAMERA_SAFETY_ACTION_SKIPPED,
                    "Spaghetti confirmed but no running SD print job was found",
                    sample.confidence());
            return;
        }

        pauseActivePrintJob(sample, activeJob.get());
    }

    private boolean hasRequiredConfirmations(CameraAnalysisSample sample, CameraSettings settings) {
        List<CameraAnalysisSample> samples = sampleStore.findBySession(sample.printerId(), sample.sessionId());
        int confirmations = 0;

        for (int index = samples.size() - 1; index >= 0; index--) {
            CameraAnalysisSample candidate = samples.get(index);
            if (candidate.suspected() && candidate.confidence() >= settings.confidenceThreshold()) {
                confirmations++;
                if (confirmations >= settings.confirmationsRequired()) {
                    return true;
                }
                continue;
            }
            break;
        }

        return false;
    }

    private void pauseActivePrintJob(CameraAnalysisSample sample, PrintJob job) {
        printJobService.recordJobAuditEvent(
                job.id(),
                OperationMessages.EVENT_SPAGHETTI_CONFIRMED,
                confirmedMessage(sample, null));

        AutonomousPrintControlService.ControlResult result = autonomousPrintControlService.pause(job.id());

        if (result.success()) {
            cameraEventStore.record(
                    sample.printerId(),
                    OperationMessages.EVENT_CAMERA_SAFETY_ACTION_SUCCEEDED,
                    "Paused SD print job after confirmed spaghetti detection: " + job.id(),
                    sample.confidence());
            printJobService.recordJobAuditEvent(
                    job.id(),
                    OperationMessages.EVENT_CAMERA_SAFETY_ACTION_SUCCEEDED,
                    "Paused SD print job after confirmed spaghetti detection");
            return;
        }

        cameraEventStore.record(
                sample.printerId(),
                OperationMessages.EVENT_CAMERA_SAFETY_ACTION_FAILED,
                "Failed to pause SD print job after confirmed spaghetti detection: "
                        + OperationMessages.safeDetail(result.failureDetail(), "unknown failure"),
                sample.confidence());
        printJobService.recordJobAuditEvent(
                job.id(),
                OperationMessages.EVENT_CAMERA_SAFETY_ACTION_FAILED,
                "Failed to pause SD print job after confirmed spaghetti detection: "
                        + OperationMessages.safeDetail(result.failureDetail(), "unknown failure"));
    }

    private static String suspectedMessage(CameraAnalysisSample sample) {
        return OperationMessages.POSSIBLE_SPAGHETTI_FAILURE_DETECTED
                + ": confidence=" + formatRatio(sample.confidence())
                + ", deltaScore=" + formatRatio(sample.deltaScore())
                + ", reasons=" + sample.reasonCodes().orElse("none");
    }

    private static String confirmedMessage(CameraAnalysisSample sample, CameraSettings settings) {
        String thresholdText = settings == null
                ? ""
                : ", threshold=" + formatRatio(settings.confidenceThreshold())
                        + ", confirmationsRequired=" + settings.confirmationsRequired();

        return OperationMessages.SPAGHETTI_FAILURE_CONFIRMED
                + ": confidence=" + formatRatio(sample.confidence())
                + ", deltaScore=" + formatRatio(sample.deltaScore())
                + thresholdText
                + ", reasons=" + sample.reasonCodes().orElse("none");
    }

    private static String formatRatio(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }
}
