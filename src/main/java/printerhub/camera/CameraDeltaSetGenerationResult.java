package printerhub.camera;

import printerhub.persistence.CameraDeltaSet;

public record CameraDeltaSetGenerationResult(
        CameraDeltaSet deltaSet,
        int sourceSnapshotCount,
        int generatedDeltaCount,
        int skippedIntermediateSnapshotCount
) {
}
