package printerhub.camera;

import java.nio.file.Path;
import java.util.Optional;

public interface FrameAnalyzer {

    FrameAnalysisResult analyze(
            String printerId,
            Path previousFramePath,
            Path latestFramePath,
            Optional<Path> deltaOutputPath);
}