package spaghettichef.camera;

import java.util.List;

public record CameraCalculationRunComparison(
        CameraCalculationRunSummary left,
        CameraCalculationRunSummary right,
        int comparedFrameCount,
        int suspectedMismatchCount,
        double averageAbsoluteConfidenceDifference,
        List<CameraCalculationComparisonFrame> frames
) {
}
