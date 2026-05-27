package spaghettichef.camera;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import spaghettichef.persistence.CameraSnapshotEntry;
import spaghettichef.persistence.CameraSnapshotEntryStore;

public final class CameraSnapshotPurgeService {

    private static final Set<String> PREVIEW_FILENAMES = Set.of("latest.jpg", "previous.jpg", "delta.jpg");

    private final CameraSnapshotEntryStore snapshotEntryStore;
    private final Clock clock;

    public CameraSnapshotPurgeService(CameraSnapshotEntryStore snapshotEntryStore) {
        this(snapshotEntryStore, Clock.systemUTC());
    }

    public CameraSnapshotPurgeService(CameraSnapshotEntryStore snapshotEntryStore, Clock clock) {
        if (snapshotEntryStore == null) {
            throw new IllegalArgumentException("snapshotEntryStore must not be null");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.snapshotEntryStore = snapshotEntryStore;
        this.clock = clock;
    }

    public CameraSnapshotPurgeReport purge(
            String printerId,
            long cameraJobId,
            int retentionSnapshotCount,
            int purgeRetentionFrequency,
            String deletionReason) {
        String normalizedPrinterId = requirePrinterId(printerId);
        if (cameraJobId <= 0L) {
            throw new IllegalArgumentException("cameraJobId must be greater than zero");
        }
        if (retentionSnapshotCount < 0) {
            throw new IllegalArgumentException("retentionSnapshotCount must not be negative");
        }
        if (purgeRetentionFrequency <= 0) {
            throw new IllegalArgumentException("purgeRetentionFrequency must be greater than zero");
        }

        List<CameraSnapshotEntry> entries = snapshotEntryStore.findByPrinterIdAndJobId(
                normalizedPrinterId,
                Long.toString(cameraJobId));
        Set<Integer> retainedIndexes = retainedIndexes(
                entries.size(),
                retentionSnapshotCount,
                purgeRetentionFrequency);
        Instant deletedAt = Instant.now(clock);
        String normalizedReason = normalizeReason(deletionReason);
        List<Long> deletedSnapshotIds = new ArrayList<>();
        List<Long> failedSnapshotIds = new ArrayList<>();
        int alreadyDeletedCount = 0;

        for (int index = 0; index < entries.size(); index++) {
            if (retainedIndexes.contains(index)) {
                continue;
            }

            CameraSnapshotEntry entry = entries.get(index);
            if (entry.fileDeleted()) {
                alreadyDeletedCount++;
                continue;
            }

            try {
                deleteSnapshotFile(entry);
                snapshotEntryStore.markFileDeleted(entry.id(), deletedAt, normalizedReason);
                deletedSnapshotIds.add(entry.id());
            } catch (RuntimeException | IOException exception) {
                failedSnapshotIds.add(entry.id());
            }
        }

        int purgeCandidateCount = entries.size() - retainedIndexes.size();
        int deletedCount = deletedSnapshotIds.size();
        int failedCount = failedSnapshotIds.size();
        String message = "Purged " + deletedCount + " snapshot file"
                + (deletedCount == 1 ? "" : "s")
                + " for camera job " + cameraJobId + ".";

        return new CameraSnapshotPurgeReport(
                normalizedPrinterId,
                cameraJobId,
                entries.size(),
                retainedIndexes.size(),
                purgeCandidateCount,
                deletedCount,
                alreadyDeletedCount,
                failedCount,
                retentionSnapshotCount,
                purgeRetentionFrequency,
                deletedSnapshotIds,
                failedSnapshotIds,
                message);
    }

    private static Set<Integer> retainedIndexes(
            int totalSnapshotCount,
            int retentionSnapshotCount,
            int purgeRetentionFrequency) {
        Set<Integer> retained = new HashSet<>();
        int latestWindowStart = Math.max(0, totalSnapshotCount - retentionSnapshotCount);

        for (int index = 0; index < totalSnapshotCount; index++) {
            if (index >= latestWindowStart || index % purgeRetentionFrequency == 0) {
                retained.add(index);
            }
        }

        return retained;
    }

    private static void deleteSnapshotFile(CameraSnapshotEntry entry) throws IOException {
        Path snapshotPath = Path.of(entry.snapshotPath()).toAbsolutePath().normalize();
        Path fileName = snapshotPath.getFileName();
        if (fileName == null || PREVIEW_FILENAMES.contains(fileName.toString().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("Refusing to purge preview file: " + entry.snapshotPath());
        }

        Files.deleteIfExists(snapshotPath);
    }

    private static String normalizeReason(String deletionReason) {
        return deletionReason == null || deletionReason.isBlank()
                ? "manual snapshot purge"
                : deletionReason.trim();
    }

    private static String requirePrinterId(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException("printerId must not be blank");
        }
        return printerId.trim();
    }
}
