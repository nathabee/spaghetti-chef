package spaghettichef.camera;

import spaghettichef.persistence.CameraDeltaSet;

public record CameraDeltaSetGenerationResult(
        CameraDeltaSet deltaSet,
        int sourceSnapshotCount,
        int generatedDeltaCount,
        int skippedIntermediateSnapshotCount
) {
}
