package printerhub.camera;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import printerhub.persistence.CameraSnapshotEntry;
import printerhub.persistence.CameraSnapshotEntryStore;
import printerhub.persistence.CameraSnapshotJobSummary;
import printerhub.persistence.CameraSettings;

public final class CameraSnapshotManagementService {

    private final CameraSettingsService settingsService;
    private final CameraSnapshotEntryStore snapshotEntryStore;

    public CameraSnapshotManagementService(
            CameraSettingsService settingsService,
            CameraSnapshotEntryStore snapshotEntryStore) {
        this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
        this.snapshotEntryStore = Objects.requireNonNull(snapshotEntryStore, "snapshotEntryStore");
    }

    public List<CameraSnapshotJobSummary> listJobs() {
        return snapshotEntryStore.findJobSummaries();
    }

    public List<CameraSnapshotJobSummary> listJobs(String printerId) {
        return snapshotEntryStore.findJobSummariesByPrinterId(printerId);
    }

    public List<CameraSnapshotEntry> entriesForJob(String jobId) {
        return snapshotEntryStore.findByJobId(jobId);
    }

    public Optional<CameraSnapshotEntry> entryById(long entryId) {
        return snapshotEntryStore.findById(entryId);
    }

    public List<CameraSnapshotEntry> entriesForJob(String printerId, String jobId) {
        return snapshotEntryStore.findByPrinterIdAndJobId(printerId, jobId);
    }

    public CameraSnapshotDeletionReport deleteJobSnapshot(String jobId) {
        List<CameraSnapshotEntry> entries = snapshotEntryStore.findByJobId(jobId);
        List<String> failedFiles = new ArrayList<>();
        int deletedFiles = 0;
        long deletedBytes = 0L;

        for (CameraSnapshotEntry entry : entries) {
            Path snapshotPath = Path.of(entry.snapshotPath()).normalize();

            if (!isInsidePrinterCameraDirectory(entry.printerId(), snapshotPath)) {
                failedFiles.add(entry.snapshotPath());
                continue;
            }

            try {
                long size = Files.isRegularFile(snapshotPath) ? Files.size(snapshotPath) : 0L;
                if (Files.deleteIfExists(snapshotPath)) {
                    deletedFiles++;
                    deletedBytes += size;
                }
            } catch (IOException exception) {
                failedFiles.add(entry.snapshotPath());
            }
        }

        int deletedRows = snapshotEntryStore.deleteByJobId(jobId);
        String message = failedFiles.isEmpty()
                ? "camera_snapshot_job_deleted"
                : "camera_snapshot_job_deleted_with_file_errors";

        return new CameraSnapshotDeletionReport(
                jobId,
                deletedFiles,
                deletedBytes,
                deletedRows,
                List.copyOf(failedFiles),
                message);
    }

    public CameraSnapshotDeletionReport deleteJobSnapshot(String printerId, String jobId) {
        List<CameraSnapshotEntry> entries = snapshotEntryStore.findByPrinterIdAndJobId(printerId, jobId);
        List<String> failedFiles = new ArrayList<>();
        int deletedFiles = 0;
        long deletedBytes = 0L;

        for (CameraSnapshotEntry entry : entries) {
            Path snapshotPath = Path.of(entry.snapshotPath()).normalize();

            if (!isInsidePrinterCameraDirectory(entry.printerId(), snapshotPath)) {
                failedFiles.add(entry.snapshotPath());
                continue;
            }

            try {
                long size = Files.isRegularFile(snapshotPath) ? Files.size(snapshotPath) : 0L;
                if (Files.deleteIfExists(snapshotPath)) {
                    deletedFiles++;
                    deletedBytes += size;
                }
            } catch (IOException exception) {
                failedFiles.add(entry.snapshotPath());
            }
        }

        int deletedRows = snapshotEntryStore.deleteByPrinterIdAndJobId(printerId, jobId);
        String message = failedFiles.isEmpty()
                ? "camera_snapshot_job_deleted"
                : "camera_snapshot_job_deleted_with_file_errors";

        return new CameraSnapshotDeletionReport(
                jobId,
                deletedFiles,
                deletedBytes,
                deletedRows,
                List.copyOf(failedFiles),
                message);
    }


    private boolean isInsidePrinterCameraDirectory(String printerId, Path candidatePath) {
        CameraSettings settings = settingsService.load(printerId);
        Path printerDirectory = CameraStoragePaths
                .resolveBaseDirectory(settings.storageDirectory())
                .resolve(safePathSegment(printerId))
                .normalize();

        return candidatePath.startsWith(printerDirectory);
    }

    private static String safePathSegment(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        return value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
