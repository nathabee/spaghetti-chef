package spaghettichef.camera;

public record CameraCalculationComparisonFrame(
        long deltaFrameId,
        Long leftResultId,
        Long rightResultId,
        Double leftConfidence,
        Double rightConfidence,
        Double confidenceDifference,
        Boolean leftSuspected,
        Boolean rightSuspected,
        boolean suspectedMismatch,
        String leftReasonCodes,
        String rightReasonCodes
) {
}
