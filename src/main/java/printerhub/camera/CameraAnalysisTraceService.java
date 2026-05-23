package printerhub.camera;

import printerhub.persistence.CameraCalculationResult;
import printerhub.persistence.CameraCalculationResultStore;
import printerhub.persistence.CameraCalculationRun;
import printerhub.persistence.CameraCalculationRunStore;
import printerhub.persistence.CameraDeltaFrame;
import printerhub.persistence.CameraDeltaFrameStore;
import printerhub.persistence.CameraSnapshotEntry;
import printerhub.persistence.CameraSnapshotEntryStore;

import java.util.ArrayList;
import java.util.List;

public final class CameraAnalysisTraceService {

    private final CameraCalculationRunStore calculationRunStore;
    private final CameraCalculationResultStore calculationResultStore;
    private final CameraDeltaFrameStore deltaFrameStore;
    private final CameraSnapshotEntryStore snapshotEntryStore;

    public CameraAnalysisTraceService(
            CameraCalculationRunStore calculationRunStore,
            CameraCalculationResultStore calculationResultStore,
            CameraDeltaFrameStore deltaFrameStore,
            CameraSnapshotEntryStore snapshotEntryStore) {
        if (calculationRunStore == null) {
            throw new IllegalArgumentException("calculationRunStore must not be null");
        }
        if (calculationResultStore == null) {
            throw new IllegalArgumentException("calculationResultStore must not be null");
        }
        if (deltaFrameStore == null) {
            throw new IllegalArgumentException("deltaFrameStore must not be null");
        }
        if (snapshotEntryStore == null) {
            throw new IllegalArgumentException("snapshotEntryStore must not be null");
        }

        this.calculationRunStore = calculationRunStore;
        this.calculationResultStore = calculationResultStore;
        this.deltaFrameStore = deltaFrameStore;
        this.snapshotEntryStore = snapshotEntryStore;
    }

    public List<CameraAnalysisTraceRow> traceForCalculationRun(long calculationRunId, String printerId) {
        CameraCalculationRun run = calculationRunStore.findById(calculationRunId)
                .orElseThrow(() -> new IllegalArgumentException("camera calculation run not found: " + calculationRunId));
        String requestedPrinterId = normalizePrinterId(printerId);
        if (requestedPrinterId != null && !requestedPrinterId.equals(run.printerId())) {
            throw new IllegalArgumentException("camera calculation run does not belong to printer: " + requestedPrinterId);
        }

        List<CameraAnalysisTraceRow> rows = new ArrayList<>();
        for (CameraCalculationResult result : calculationResultStore.findByCalculationRunId(calculationRunId)) {
            CameraDeltaFrame frame = deltaFrameStore.findById(result.deltaFrameId())
                    .orElseThrow(() -> new IllegalStateException(
                            "camera delta frame not found for calculation result: " + result.requireId()));
            CameraSnapshotEntry fromSnapshot = snapshotEntryStore.findById(frame.fromSnapshotId())
                    .orElseThrow(() -> new IllegalStateException(
                            "source snapshot not found for delta frame: " + frame.requireId()));
            CameraSnapshotEntry toSnapshot = snapshotEntryStore.findById(frame.toSnapshotId())
                    .orElseThrow(() -> new IllegalStateException(
                            "target snapshot not found for delta frame: " + frame.requireId()));

            rows.add(new CameraAnalysisTraceRow(
                    frame.cameraJobId(),
                    frame.deltaSetId(),
                    frame.requireId(),
                    run.requireId(),
                    result.requireId(),
                    fromSnapshot.snapshotPath(),
                    toSnapshot.snapshotPath(),
                    frame.deltaPath(),
                    result.confidence(),
                    result.suspected(),
                    result.reasonCodes(),
                    result.message(),
                    result.createdAt()));
        }

        return rows;
    }

    private static String normalizePrinterId(String printerId) {
        return printerId == null || printerId.isBlank() ? null : printerId.trim();
    }
}
