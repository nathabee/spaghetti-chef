package printerhub.camera;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import printerhub.persistence.CameraArchiveEntry;
import printerhub.persistence.CameraArchiveEntryStore;
import printerhub.persistence.CameraArchiveJobSummary;
import printerhub.persistence.CameraSettings;

public final class CameraArchiveManagementService {

    private final CameraSettingsService settingsService;
    private final CameraArchiveEntryStore archiveEntryStore;

    public CameraArchiveManagementService(
            CameraSettingsService settingsService,
            CameraArchiveEntryStore archiveEntryStore) {
        this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
        this.archiveEntryStore = Objects.requireNonNull(archiveEntryStore, "archiveEntryStore");
    }

    public List<CameraArchiveJobSummary> listJobs() {
        return archiveEntryStore.findJobSummaries();
    }

    public List<CameraArchiveJobSummary> listJobs(String printerId) {
        return archiveEntryStore.findJobSummariesByPrinterId(printerId);
    }

    public List<CameraArchiveEntry> entriesForJob(String jobId) {
        return archiveEntryStore.findByJobId(jobId);
    }

    public Optional<CameraArchiveEntry> entryById(long entryId) {
        return archiveEntryStore.findById(entryId);
    }

    public List<CameraArchiveEntry> entriesForJob(String printerId, String jobId) {
        return archiveEntryStore.findByPrinterIdAndJobId(printerId, jobId);
    }

    public CameraArchiveDeletionReport deleteJobArchive(String jobId) {
        List<CameraArchiveEntry> entries = archiveEntryStore.findByJobId(jobId);
        List<String> failedFiles = new ArrayList<>();
        int deletedFiles = 0;
        long deletedBytes = 0L;

        for (CameraArchiveEntry entry : entries) {
            Path archivePath = Path.of(entry.archivePath()).normalize();

            if (!isInsidePrinterCameraDirectory(entry.printerId(), archivePath)) {
                failedFiles.add(entry.archivePath());
                continue;
            }

            try {
                long size = Files.isRegularFile(archivePath) ? Files.size(archivePath) : 0L;
                if (Files.deleteIfExists(archivePath)) {
                    deletedFiles++;
                    deletedBytes += size;
                }
            } catch (IOException exception) {
                failedFiles.add(entry.archivePath());
            }
        }

        int deletedRows = archiveEntryStore.deleteByJobId(jobId);
        String message = failedFiles.isEmpty()
                ? "camera_archive_job_deleted"
                : "camera_archive_job_deleted_with_file_errors";

        return new CameraArchiveDeletionReport(
                jobId,
                deletedFiles,
                deletedBytes,
                deletedRows,
                List.copyOf(failedFiles),
                message);
    }

    public CameraArchiveDeletionReport deleteJobArchive(String printerId, String jobId) {
        List<CameraArchiveEntry> entries = archiveEntryStore.findByPrinterIdAndJobId(printerId, jobId);
        List<String> failedFiles = new ArrayList<>();
        int deletedFiles = 0;
        long deletedBytes = 0L;

        for (CameraArchiveEntry entry : entries) {
            Path archivePath = Path.of(entry.archivePath()).normalize();

            if (!isInsidePrinterCameraDirectory(entry.printerId(), archivePath)) {
                failedFiles.add(entry.archivePath());
                continue;
            }

            try {
                long size = Files.isRegularFile(archivePath) ? Files.size(archivePath) : 0L;
                if (Files.deleteIfExists(archivePath)) {
                    deletedFiles++;
                    deletedBytes += size;
                }
            } catch (IOException exception) {
                failedFiles.add(entry.archivePath());
            }
        }

        int deletedRows = archiveEntryStore.deleteByPrinterIdAndJobId(printerId, jobId);
        String message = failedFiles.isEmpty()
                ? "camera_archive_job_deleted"
                : "camera_archive_job_deleted_with_file_errors";

        return new CameraArchiveDeletionReport(
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
