package spaghettichef.camera;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import spaghettichef.persistence.CameraCalculationResultStore;
import spaghettichef.persistence.CameraCalculationRun;
import spaghettichef.persistence.CameraCalculationRunStore;
import spaghettichef.persistence.CameraDeltaFrame;
import spaghettichef.persistence.CameraDeltaFrameStore;
import spaghettichef.persistence.CameraDeltaSet;
import spaghettichef.persistence.CameraDeltaSetStore;
import spaghettichef.persistence.CameraSettings;

public final class CameraDeltaSetDeletionService {

    private static final Set<String> PREVIEW_FILENAMES = Set.of("latest.jpg", "previous.jpg", "delta.jpg");

    private final CameraSettingsService settingsService;
    private final CameraDeltaSetStore deltaSetStore;
    private final CameraDeltaFrameStore deltaFrameStore;
    private final CameraCalculationRunStore calculationRunStore;
    private final CameraCalculationResultStore calculationResultStore;

    public CameraDeltaSetDeletionService(CameraSettingsService settingsService) {
        this(
                settingsService,
                new CameraDeltaSetStore(),
                new CameraDeltaFrameStore(),
                new CameraCalculationRunStore(),
                new CameraCalculationResultStore());
    }

    public CameraDeltaSetDeletionService(
            CameraSettingsService settingsService,
            CameraDeltaSetStore deltaSetStore,
            CameraDeltaFrameStore deltaFrameStore,
            CameraCalculationRunStore calculationRunStore,
            CameraCalculationResultStore calculationResultStore) {
        this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
        this.deltaSetStore = Objects.requireNonNull(deltaSetStore, "deltaSetStore");
        this.deltaFrameStore = Objects.requireNonNull(deltaFrameStore, "deltaFrameStore");
        this.calculationRunStore = Objects.requireNonNull(calculationRunStore, "calculationRunStore");
        this.calculationResultStore = Objects.requireNonNull(calculationResultStore, "calculationResultStore");
    }

    public CameraDeltaSetDeletionReport delete(String printerId, long deltaSetId, CameraDeltaSetDeletionRequest request) {
        String normalizedPrinterId = requirePrinterId(printerId);
        if (deltaSetId <= 0L) {
            throw new IllegalArgumentException("deltaSetId must be greater than zero");
        }
        CameraDeltaSetDeletionRequest effectiveRequest = request == null
                ? CameraDeltaSetDeletionRequest.safeDefault(CameraDeltaSetDeletionRequest.CONFIRMATION)
                : request;
        effectiveRequest.requireConfirmed();

        CameraDeltaSet deltaSet = deltaSetStore.findById(deltaSetId)
                .orElseThrow(() -> new IllegalArgumentException("camera delta set not found: " + deltaSetId));
        if (!normalizedPrinterId.equals(deltaSet.printerId())) {
            throw new IllegalArgumentException("camera delta set does not belong to printer: " + normalizedPrinterId);
        }

        CameraSettings settings = settingsService.load(normalizedPrinterId);
        Path expectedDeltaDirectory = CameraStoragePaths.deltasDirectory(
                settings.storageDirectory(),
                normalizedPrinterId,
                deltaSet.cameraJobId(),
                deltaSet.requireId())
                .toAbsolutePath()
                .normalize();

        List<String> failedFiles = new ArrayList<>();
        DeleteFilesResult deltaFiles = new DeleteFilesResult(0, 0L);
        int deletedDeltaRows = 0;
        int deletedDeltaSetRows = 0;
        int deletedCalculationRunRows = 0;
        int deletedCalculationResultRows = 0;

        if (effectiveRequest.deleteDeltaFiles()) {
            deltaFiles = deleteDeltaFiles(
                    deltaFrameStore.findByDeltaSetId(deltaSet.requireId()),
                    expectedDeltaDirectory,
                    failedFiles);
        }

        if (effectiveRequest.deleteCalculationRuns()) {
            for (CameraCalculationRun run : calculationRunStore.findByDeltaSetId(deltaSet.requireId())) {
                deletedCalculationResultRows += calculationResultStore.deleteByCalculationRunId(run.requireId());
            }
            deletedCalculationRunRows = calculationRunStore.deleteByDeltaSetId(deltaSet.requireId());
        }

        if (effectiveRequest.deleteDeltaRows()) {
            deletedDeltaRows = deltaFrameStore.deleteByDeltaSetId(deltaSet.requireId());
            deletedDeltaSetRows = deltaSetStore.deleteById(deltaSet.requireId());
        }

        String message = failedFiles.isEmpty()
                ? "camera_delta_set_deleted"
                : "camera_delta_set_deleted_with_file_errors";

        return new CameraDeltaSetDeletionReport(
                normalizedPrinterId,
                deltaSet.cameraJobId(),
                deltaSet.requireId(),
                deltaFiles.deletedFiles(),
                deltaFiles.deletedBytes(),
                deletedDeltaRows,
                deletedDeltaSetRows,
                deletedCalculationRunRows,
                deletedCalculationResultRows,
                failedFiles,
                message);
    }

    private static DeleteFilesResult deleteDeltaFiles(
            List<CameraDeltaFrame> frames,
            Path expectedDeltaDirectory,
            List<String> failedFiles) {
        int deletedFiles = 0;
        long deletedBytes = 0L;

        for (CameraDeltaFrame frame : frames) {
            FileDeleteResult result = deleteFile(frame.deltaPath(), expectedDeltaDirectory, failedFiles);
            deletedFiles += result.deletedFiles();
            deletedBytes += result.deletedBytes();
        }

        return new DeleteFilesResult(deletedFiles, deletedBytes);
    }

    private static FileDeleteResult deleteFile(
            String storedPath,
            Path expectedDeltaDirectory,
            List<String> failedFiles) {
        Path path = Path.of(storedPath).toAbsolutePath().normalize();
        Path fileName = path.getFileName();
        if (fileName == null
                || PREVIEW_FILENAMES.contains(fileName.toString().toLowerCase(Locale.ROOT))
                || !path.startsWith(expectedDeltaDirectory)) {
            failedFiles.add(storedPath);
            return new FileDeleteResult(0, 0L);
        }

        try {
            long size = Files.isRegularFile(path) ? Files.size(path) : 0L;
            if (Files.deleteIfExists(path)) {
                return new FileDeleteResult(1, size);
            }
            return new FileDeleteResult(0, 0L);
        } catch (IOException exception) {
            failedFiles.add(storedPath);
            return new FileDeleteResult(0, 0L);
        }
    }

    private static String requirePrinterId(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException("printerId must not be blank");
        }
        return printerId.trim();
    }

    private record DeleteFilesResult(int deletedFiles, long deletedBytes) {
    }

    private record FileDeleteResult(int deletedFiles, long deletedBytes) {
    }
}
