package spaghettichef.camera;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import spaghettichef.persistence.CameraCalculationResult;
import spaghettichef.persistence.CameraCalculationResultStore;
import spaghettichef.persistence.CameraCalculationRun;
import spaghettichef.persistence.CameraCalculationRunStore;

public final class CameraCalculationComparisonService {

    private final CameraCalculationRunStore calculationRunStore;
    private final CameraCalculationResultStore calculationResultStore;

    public CameraCalculationComparisonService() {
        this(new CameraCalculationRunStore(), new CameraCalculationResultStore());
    }

    public CameraCalculationComparisonService(
            CameraCalculationRunStore calculationRunStore,
            CameraCalculationResultStore calculationResultStore) {
        this.calculationRunStore = Objects.requireNonNull(calculationRunStore, "calculationRunStore");
        this.calculationResultStore = Objects.requireNonNull(calculationResultStore, "calculationResultStore");
    }

    public CameraCalculationRunComparison compare(long leftRunId, long rightRunId, String printerId) {
        if (leftRunId <= 0L) {
            throw new IllegalArgumentException("leftRunId must be greater than zero");
        }
        if (rightRunId <= 0L) {
            throw new IllegalArgumentException("rightRunId must be greater than zero");
        }
        if (leftRunId == rightRunId) {
            throw new IllegalArgumentException("calculation runs must be different");
        }

        CameraCalculationRun leftRun = calculationRunStore.findById(leftRunId)
                .orElseThrow(() -> new IllegalArgumentException("left calculation run not found: " + leftRunId));
        CameraCalculationRun rightRun = calculationRunStore.findById(rightRunId)
                .orElseThrow(() -> new IllegalArgumentException("right calculation run not found: " + rightRunId));

        String requestedPrinterId = printerId == null ? "" : printerId.trim();
        if (!requestedPrinterId.isBlank()
                && (!leftRun.printerId().equals(requestedPrinterId) || !rightRun.printerId().equals(requestedPrinterId))) {
            throw new IllegalArgumentException("calculation runs do not belong to printer: " + requestedPrinterId);
        }

        if (!leftRun.printerId().equals(rightRun.printerId())
                || leftRun.cameraJobId() != rightRun.cameraJobId()
                || leftRun.deltaSetId() != rightRun.deltaSetId()) {
            throw new IllegalArgumentException("calculation runs must share printer, camera job, and delta set");
        }

        List<CameraCalculationResult> leftResults = calculationResultStore.findByCalculationRunId(leftRunId);
        List<CameraCalculationResult> rightResults = calculationResultStore.findByCalculationRunId(rightRunId);
        Map<Long, CameraCalculationResult> leftByFrame = byDeltaFrameId(leftResults);
        Map<Long, CameraCalculationResult> rightByFrame = byDeltaFrameId(rightResults);

        List<CameraCalculationComparisonFrame> frames = new ArrayList<>();
        int suspectedMismatchCount = 0;
        double confidenceDifferenceSum = 0.0;
        int comparedConfidenceCount = 0;

        for (Long deltaFrameId : mergedFrameIds(leftByFrame, rightByFrame)) {
            CameraCalculationResult left = leftByFrame.get(deltaFrameId);
            CameraCalculationResult right = rightByFrame.get(deltaFrameId);
            Double confidenceDifference = null;
            boolean suspectedMismatch = false;

            if (left != null && right != null) {
                confidenceDifference = Math.abs(left.confidence() - right.confidence());
                confidenceDifferenceSum += confidenceDifference;
                comparedConfidenceCount++;
                suspectedMismatch = left.suspected() != right.suspected();
                if (suspectedMismatch) {
                    suspectedMismatchCount++;
                }
            }

            frames.add(new CameraCalculationComparisonFrame(
                    deltaFrameId,
                    left == null ? null : left.id(),
                    right == null ? null : right.id(),
                    left == null ? null : left.confidence(),
                    right == null ? null : right.confidence(),
                    confidenceDifference,
                    left == null ? null : left.suspected(),
                    right == null ? null : right.suspected(),
                    suspectedMismatch,
                    left == null ? null : left.reasonCodes(),
                    right == null ? null : right.reasonCodes()));
        }

        double averageDifference = comparedConfidenceCount == 0
                ? 0.0
                : confidenceDifferenceSum / comparedConfidenceCount;

        return new CameraCalculationRunComparison(
                summarize(leftRun, leftResults),
                summarize(rightRun, rightResults),
                comparedConfidenceCount,
                suspectedMismatchCount,
                averageDifference,
                List.copyOf(frames));
    }

    private static CameraCalculationRunSummary summarize(
            CameraCalculationRun run,
            List<CameraCalculationResult> results) {
        int suspectedCount = 0;
        double confidenceSum = 0.0;

        for (CameraCalculationResult result : results) {
            if (result.suspected()) {
                suspectedCount++;
            }
            confidenceSum += result.confidence();
        }

        double averageConfidence = results.isEmpty() ? 0.0 : confidenceSum / results.size();
        Double resultsPerSecond = null;
        Double averageMillisecondsPerFrame = null;
        if (run.executionDurationMs() != null && run.executionDurationMs() > 0L && !results.isEmpty()) {
            resultsPerSecond = results.size() / (run.executionDurationMs() / 1000.0);
            averageMillisecondsPerFrame = run.executionDurationMs() / (double) results.size();
        }

        return new CameraCalculationRunSummary(
                run,
                results.size(),
                suspectedCount,
                averageConfidence,
                resultsPerSecond,
                averageMillisecondsPerFrame);
    }

    private static Map<Long, CameraCalculationResult> byDeltaFrameId(List<CameraCalculationResult> results) {
        Map<Long, CameraCalculationResult> byFrame = new LinkedHashMap<>();
        for (CameraCalculationResult result : results) {
            byFrame.put(result.deltaFrameId(), result);
        }
        return byFrame;
    }

    private static List<Long> mergedFrameIds(
            Map<Long, CameraCalculationResult> left,
            Map<Long, CameraCalculationResult> right) {
        List<Long> ids = new ArrayList<>(left.keySet());
        for (Long id : right.keySet()) {
            if (!left.containsKey(id)) {
                ids.add(id);
            }
        }
        return ids;
    }
}
