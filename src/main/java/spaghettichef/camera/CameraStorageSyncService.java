package spaghettichef.camera;

import spaghettichef.persistence.CameraDeltaFrame;
import spaghettichef.persistence.CameraDeltaFrameStore;
import spaghettichef.persistence.CameraDeltaSet;
import spaghettichef.persistence.CameraDeltaSetStore;
import spaghettichef.persistence.CameraJob;
import spaghettichef.persistence.CameraJobState;
import spaghettichef.persistence.CameraJobStore;
import spaghettichef.persistence.CameraSettings;
import spaghettichef.persistence.CameraSettingsStore;
import spaghettichef.persistence.CameraSnapshotEntry;
import spaghettichef.persistence.CameraSnapshotEntryStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CameraStorageSyncService {
    public static final String CONFIRMATION = "SYNC_CAMERA_DATASET";
    private static final Pattern SNAPSHOT_FILE_PATTERN = Pattern.compile("([0-9]+)_snapshot\\.jpg");
    private static final Pattern DELTA_FILE_PATTERN = Pattern.compile("([0-9]+)_([0-9]+)_delta\\.jpg");

    private final CameraSettingsStore cameraSettingsStore;
    private final CameraJobStore cameraJobStore;
    private final CameraSnapshotEntryStore snapshotEntryStore;
    private final CameraDeltaSetStore deltaSetStore;
    private final CameraDeltaFrameStore deltaFrameStore;
    private final Clock clock;

    public CameraStorageSyncService(
            CameraSettingsStore cameraSettingsStore,
            CameraJobStore cameraJobStore,
            CameraSnapshotEntryStore snapshotEntryStore,
            CameraDeltaSetStore deltaSetStore,
            CameraDeltaFrameStore deltaFrameStore,
            Clock clock) {
        this.cameraSettingsStore = cameraSettingsStore;
        this.cameraJobStore = cameraJobStore;
        this.snapshotEntryStore = snapshotEntryStore;
        this.deltaSetStore = deltaSetStore;
        this.deltaFrameStore = deltaFrameStore;
        this.clock = clock;
    }

    public CameraStorageSyncReport sync(String printerId, CameraStorageSyncRequest request) {
        String normalizedPrinterId = requireText(printerId, "printerId");
        if (!request.dryRun() && !CONFIRMATION.equals(request.requiredConfirmation())) {
            throw new IllegalArgumentException("requiredConfirmation must be " + CONFIRMATION);
        }

        CameraSettings settings = cameraSettingsStore.findByPrinterId(normalizedPrinterId)
                .orElseThrow(() -> new IllegalArgumentException("camera settings not found for printer: " + normalizedPrinterId));
        Path storageRoot = cameraStorageRoot(settings);
        Counts counts = new Counts();
        List<String> warnings = new ArrayList<>();

        Set<String> cameraJobKeys = discoverCameraJobKeys(storageRoot, request);
        for (String cameraJobKey : cameraJobKeys) {
            counts.scannedJobs++;
            syncCameraJob(normalizedPrinterId, settings, storageRoot, cameraJobKey, request, counts, warnings);
        }

        return counts.report(normalizedPrinterId, storageRoot.toString(), request.dryRun(), warnings);
    }

    private void syncCameraJob(
            String printerId,
            CameraSettings settings,
            Path storageRoot,
            String cameraJobKey,
            CameraStorageSyncRequest request,
            Counts counts,
            List<String> warnings) {
        Path snapshotsRoot = storageRoot.resolve("snapshots").resolve(cameraJobKey).normalize();
        Path deltasRoot = storageRoot.resolve("deltas").resolve(cameraJobKey).normalize();
        Optional<CameraJob> existingJob = findStorageCameraJob(printerId, cameraJobKey);
        if (existingJob.isEmpty() && !request.createMissingCameraJobs()) {
            warnings.add("camera job is missing for storage job " + cameraJobKey);
            return;
        }
        if (existingJob.isEmpty() && request.dryRun()) {
            previewMissingJob(snapshotsRoot, deltasRoot, request, counts);
            return;
        }

        CameraJob cameraJob = existingJob.orElseGet(() -> createCameraJob(
                printerId,
                settings,
                cameraJobKey,
                snapshotsRoot,
                request,
                counts));
        if (cameraJob == null) {
            return;
        }

        Map<String, CameraSnapshotEntry> snapshotsBySourceId = new HashMap<>();
        if (request.syncSnapshots()) {
            syncSnapshots(printerId, storageRoot, snapshotsRoot, cameraJob, request, counts, warnings, snapshotsBySourceId);
        } else {
            loadExistingSnapshotsBySourceId(printerId, cameraJob.requireId(), snapshotsBySourceId);
        }

        if (request.syncDeltas()) {
            syncDeltas(printerId, storageRoot, deltasRoot, cameraJob, snapshotsBySourceId, request, counts, warnings);
        }
    }

    private void previewMissingJob(Path snapshotsRoot, Path deltasRoot, CameraStorageSyncRequest request, Counts counts) {
        counts.createdCameraJobs++;
        if (request.syncSnapshots()) {
            int snapshotCount = listFilesIfDirectory(snapshotsRoot, "_snapshot.jpg").size();
            counts.scannedSnapshotFiles += snapshotCount;
            counts.createdSnapshotRows += snapshotCount;
        }
        if (request.syncDeltas()) {
            for (Path deltaSetDirectory : listDirectoriesIfPresent(deltasRoot)) {
                counts.scannedDeltaSetFolders++;
                if (request.createMissingDeltaSets()) {
                    counts.createdDeltaSets++;
                }
                int deltaCount = listFilesIfDirectory(deltaSetDirectory, "_delta.jpg").size();
                counts.scannedDeltaFiles += deltaCount;
                counts.createdDeltaFrameRows += deltaCount;
            }
        }
    }

    private CameraJob createCameraJob(
            String printerId,
            CameraSettings settings,
            String cameraJobKey,
            Path snapshotsRoot,
            CameraStorageSyncRequest request,
            Counts counts) {
        if (!request.createMissingCameraJobs()) {
            return null;
        }
        counts.createdCameraJobs++;
        if (request.dryRun()) {
            return null;
        }

        Instant now = Instant.now(clock);
        return cameraJobStore.save(new CameraJob(
                null,
                printerId,
                null,
                null,
                CameraJobState.COMPLETED,
                now,
                now,
                settings.captureIntervalSeconds(),
                settings.retentionSnapshotCount(),
                "camera-storage-sync",
                storageCameraJobDescription(cameraJobKey),
                snapshotsRoot.toString(),
                "Synchronized camera storage job " + cameraJobKey,
                now,
                now));
    }

    private void syncSnapshots(
            String printerId,
            Path storageRoot,
            Path snapshotsRoot,
            CameraJob cameraJob,
            CameraStorageSyncRequest request,
            Counts counts,
            List<String> warnings,
            Map<String, CameraSnapshotEntry> snapshotsBySourceId) {
        List<Path> files = listFilesIfDirectory(snapshotsRoot, "_snapshot.jpg");
        Set<String> seenPaths = new HashSet<>();
        Instant now = Instant.now(clock);
        int index = 0;
        for (Path file : files) {
            index++;
            counts.scannedSnapshotFiles++;
            String fileName = file.getFileName().toString();
            Matcher matcher = SNAPSHOT_FILE_PATTERN.matcher(fileName);
            if (!matcher.matches()) {
                warnings.add("ignored snapshot file with invalid name: " + file);
                continue;
            }

            String snapshotPath = file.toAbsolutePath().normalize().toString();
            seenPaths.add(snapshotPath);
            Optional<CameraSnapshotEntry> existing = snapshotEntryStore.findBySnapshotPath(
                    printerId,
                    cameraJob.requireId(),
                    snapshotPath);
            if (existing.isPresent()) {
                CameraSnapshotEntry entry = existing.get();
                if (entry.fileDeleted() && request.reactivateDeletedSnapshotRows()) {
                    counts.reactivatedSnapshotRows++;
                    if (!request.dryRun()) {
                        entry = snapshotEntryStore.reactivateFile(entry.id());
                    }
                }
                snapshotsBySourceId.put(matcher.group(1), entry);
                continue;
            }

            counts.createdSnapshotRows++;
            if (!request.dryRun()) {
                CameraSnapshotEntry saved = snapshotEntryStore.save(CameraSnapshotEntry.captured(
                        printerId,
                        cameraJob.requireId(),
                        null,
                        snapshotPath,
                        "image/jpeg",
                        size(file, warnings),
                        now.plusSeconds(index),
                        now,
                        "camera-storage-sync",
                        "Synchronized camera storage snapshot"));
                snapshotsBySourceId.put(matcher.group(1), saved);
            }
        }

        if (request.deleteRowsForMissingFiles()) {
            for (CameraSnapshotEntry entry : snapshotEntryStore.findByPrinterIdAndJobId(printerId, Long.toString(cameraJob.requireId()))) {
                if (!seenPaths.contains(entry.snapshotPath()) && isUnder(entry.snapshotPath(), storageRoot)) {
                    counts.deletedSnapshotRows++;
                    if (!request.dryRun()) {
                        snapshotEntryStore.deleteById(entry.id());
                    }
                }
            }
        }
    }

    private void syncDeltas(
            String printerId,
            Path storageRoot,
            Path deltasRoot,
            CameraJob cameraJob,
            Map<String, CameraSnapshotEntry> snapshotsBySourceId,
            CameraStorageSyncRequest request,
            Counts counts,
            List<String> warnings) {
        for (Path deltaSetDirectory : listDirectoriesIfPresent(deltasRoot)) {
            String deltaSetKey = deltaSetDirectory.getFileName().toString();
            counts.scannedDeltaSetFolders++;
            List<Path> deltaFiles = listFilesIfDirectory(deltaSetDirectory, "_delta.jpg");
            CameraDeltaSet deltaSet = findStorageDeltaSet(cameraJob.requireId(), deltaSetKey)
                    .orElseGet(() -> createDeltaSet(
                            printerId,
                            cameraJob,
                            deltaSetKey,
                            snapshotsBySourceId.size(),
                            deltaFiles.size(),
                            request,
                            counts));
            if (deltaSet == null) {
                if (request.dryRun() && request.createMissingDeltaSets()) {
                    counts.scannedDeltaFiles += deltaFiles.size();
                    counts.createdDeltaFrameRows += deltaFiles.size();
                }
                continue;
            }

            Set<String> seenPaths = new HashSet<>();
            for (Path file : deltaFiles) {
                counts.scannedDeltaFiles++;
                String deltaPath = file.toAbsolutePath().normalize().toString();
                seenPaths.add(deltaPath);
                if (deltaFrameStore.findByDeltaPath(deltaSet.requireId(), deltaPath).isPresent()) {
                    continue;
                }

                Matcher matcher = DELTA_FILE_PATTERN.matcher(file.getFileName().toString());
                if (!matcher.matches()) {
                    warnings.add("ignored delta file with invalid name: " + file);
                    continue;
                }
                CameraSnapshotEntry from = snapshotsBySourceId.get(matcher.group(1));
                CameraSnapshotEntry to = snapshotsBySourceId.get(matcher.group(2));
                if (from == null || to == null) {
                    warnings.add("missing snapshot rows for delta file: " + file);
                    continue;
                }

                counts.createdDeltaFrameRows++;
                if (!request.dryRun()) {
                    deltaFrameStore.save(new CameraDeltaFrame(
                            null,
                            deltaSet.requireId(),
                            printerId,
                            cameraJob.requireId(),
                            from.id(),
                            to.id(),
                            from.capturedAt(),
                            to.capturedAt(),
                            deltaPath,
                            0.0,
                            0.0,
                            0.0,
                            Instant.now(clock)));
                }
            }

            if (!request.dryRun()) {
                deltaSetStore.updateCounts(deltaSet.requireId(), snapshotsBySourceId.size(), deltaFiles.size());
                counts.updatedDeltaSets++;
            }

            if (request.deleteRowsForMissingFiles()) {
                for (CameraDeltaFrame frame : deltaFrameStore.findByDeltaSetId(deltaSet.requireId())) {
                    if (!seenPaths.contains(frame.deltaPath()) && isUnder(frame.deltaPath(), storageRoot)) {
                        counts.deletedDeltaFrameRows++;
                        if (!request.dryRun()) {
                            deltaFrameStore.deleteById(frame.requireId());
                        }
                    }
                }
            }
        }
    }

    private CameraDeltaSet createDeltaSet(
            String printerId,
            CameraJob cameraJob,
            String deltaSetKey,
            int sourceSnapshotCount,
            int generatedDeltaCount,
            CameraStorageSyncRequest request,
            Counts counts) {
        if (!request.createMissingDeltaSets()) {
            return null;
        }
        counts.createdDeltaSets++;
        if (request.dryRun()) {
            return null;
        }
        return deltaSetStore.save(new CameraDeltaSet(
                null,
                printerId,
                cameraJob.requireId(),
                "camera-storage-sync",
                1,
                sourceSnapshotCount,
                generatedDeltaCount,
                Instant.now(clock),
                storageDeltaSetDescription(deltaSetKey)));
    }

    private Optional<CameraJob> findStorageCameraJob(String printerId, String cameraJobKey) {
        Optional<CameraJob> byDescription = cameraJobStore.findByPrinterId(printerId).stream()
                .filter(job -> "camera-storage-sync".equals(job.sourceType()) || "dataset".equals(job.sourceType()))
                .filter(job -> job.sourceDescription().filter(storageCameraJobDescription(cameraJobKey)::equals).isPresent()
                        || job.sourceDescription().filter(("dataset camera job " + cameraJobKey)::equals).isPresent())
                .findFirst();
        if (byDescription.isPresent()) {
            return byDescription;
        }

        return parsePositiveLong(cameraJobKey)
                .flatMap(cameraJobStore::findById)
                .filter(job -> printerId.equals(job.printerId()));
    }

    private Optional<CameraDeltaSet> findStorageDeltaSet(long cameraJobId, String deltaSetKey) {
        Optional<CameraDeltaSet> byDescription = deltaSetStore.findByCameraJobId(cameraJobId).stream()
                .filter(deltaSet -> deltaSet.messageOptional().filter(storageDeltaSetDescription(deltaSetKey)::equals).isPresent())
                .findFirst();
        if (byDescription.isPresent()) {
            return byDescription;
        }

        return parsePositiveLong(deltaSetKey)
                .flatMap(deltaSetStore::findById)
                .filter(deltaSet -> deltaSet.cameraJobId() == cameraJobId);
    }

    private void loadExistingSnapshotsBySourceId(
            String printerId,
            long cameraJobId,
            Map<String, CameraSnapshotEntry> snapshotsBySourceId) {
        for (CameraSnapshotEntry entry : snapshotEntryStore.findByPrinterIdAndJobId(printerId, Long.toString(cameraJobId))) {
            Matcher matcher = SNAPSHOT_FILE_PATTERN.matcher(Path.of(entry.snapshotPath()).getFileName().toString());
            if (matcher.matches()) {
                snapshotsBySourceId.put(matcher.group(1), entry);
            }
        }
    }

    private Set<String> discoverCameraJobKeys(Path storageRoot, CameraStorageSyncRequest request) {
        Set<String> keys = new LinkedHashSet<>();
        if (request.syncSnapshots()) {
            for (Path jobDirectory : listDirectoriesIfPresent(storageRoot.resolve("snapshots"))) {
                keys.add(jobDirectory.getFileName().toString());
            }
        }
        if (request.syncDeltas()) {
            for (Path jobDirectory : listDirectoriesIfPresent(storageRoot.resolve("deltas"))) {
                keys.add(jobDirectory.getFileName().toString());
            }
        }
        return keys;
    }

    private static Path cameraStorageRoot(CameraSettings settings) {
        Path configuredRoot = CameraStoragePaths.resolveBaseDirectory(settings.storageDirectory());
        if (Files.isDirectory(configuredRoot.resolve("snapshots"))
                || Files.isDirectory(configuredRoot.resolve("deltas"))) {
            return configuredRoot;
        }
        return CameraStoragePaths.printerDirectory(settings.storageDirectory(), settings.printerId());
    }

    private static List<Path> listDirectoriesIfPresent(Path root) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (var stream = Files.list(root)) {
            return stream.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to list directories: " + root, exception);
        }
    }

    private static List<Path> listFilesIfDirectory(Path root, String suffix) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (var stream = Files.list(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to list files: " + root, exception);
        }
    }

    private static long size(Path file, List<String> warnings) {
        try {
            return Files.size(file);
        } catch (IOException exception) {
            warnings.add("failed to read file size: " + file);
            return 0L;
        }
    }

    private static boolean isUnder(String path, Path root) {
        return Path.of(path).toAbsolutePath().normalize().startsWith(root);
    }

    private static String storageCameraJobDescription(String cameraJobKey) {
        return "runtime camera job " + cameraJobKey;
    }

    private static String storageDeltaSetDescription(String deltaSetKey) {
        return "runtime camera delta set " + deltaSetKey;
    }

    private static Optional<Long> parsePositiveLong(String value) {
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0L ? Optional.of(parsed) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static final class Counts {
        private int scannedJobs;
        private int scannedSnapshotFiles;
        private int scannedDeltaSetFolders;
        private int scannedDeltaFiles;
        private int createdCameraJobs;
        private int createdSnapshotRows;
        private int reactivatedSnapshotRows;
        private int deletedSnapshotRows;
        private int createdDeltaSets;
        private int updatedDeltaSets;
        private int createdDeltaFrameRows;
        private int deletedDeltaFrameRows;

        private CameraStorageSyncReport report(String printerId, String storageRoot, boolean dryRun, List<String> warnings) {
            return new CameraStorageSyncReport(
                    printerId,
                    storageRoot,
                    dryRun,
                    scannedJobs,
                    scannedSnapshotFiles,
                    scannedDeltaSetFolders,
                    scannedDeltaFiles,
                    createdCameraJobs,
                    createdSnapshotRows,
                    reactivatedSnapshotRows,
                    deletedSnapshotRows,
                    createdDeltaSets,
                    updatedDeltaSets,
                    createdDeltaFrameRows,
                    deletedDeltaFrameRows,
                    warnings);
        }
    }
}
