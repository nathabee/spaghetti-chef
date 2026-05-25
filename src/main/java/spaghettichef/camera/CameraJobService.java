package spaghettichef.camera;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import spaghettichef.SpaghettiChefLog;
import spaghettichef.job.PrintJob;
import spaghettichef.persistence.CameraJob;
import spaghettichef.persistence.CameraJobStore;
import spaghettichef.persistence.CameraSettings;
import spaghettichef.persistence.PrintJobStore;

public final class CameraJobService {

    private final CameraJobStore cameraJobStore;
    private final PrintJobStore printJobStore;
    private final Clock clock;

    public CameraJobService() {
        this(new CameraJobStore(), new PrintJobStore(), Clock.systemUTC());
    }

    public CameraJobService(
            CameraJobStore cameraJobStore,
            PrintJobStore printJobStore,
            Clock clock) {
        this.cameraJobStore = Objects.requireNonNull(cameraJobStore, "cameraJobStore");
        this.printJobStore = Objects.requireNonNull(printJobStore, "printJobStore");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CameraJob getOrCreateActive(CameraSettings settings) {
        Objects.requireNonNull(settings, "settings");

        Optional<CameraJob> active = cameraJobStore.findActiveByPrinterId(settings.printerId());

        if (active.isPresent()) {
            return active.get();
        }

        Instant now = clock.instant();
        Optional<String> linkedPrintJobId = activePrintJobId(settings.printerId());

        CameraJob created = cameraJobStore.save(CameraJob.running(
                settings.printerId(),
                linkedPrintJobId.orElse(null),
                null,
                now,
                settings.captureIntervalSeconds(),
                settings.retentionSnapshotCount(),
                settings.sourceType().wireValue(),
                settings.sourceValue().orElse(null),
                pendingSnapshotDirectory(settings.printerId()),
                "Camera job created from camera capture"));

        Path snapshotDirectory = CameraStoragePaths
                .resolveBaseDirectory(settings.storageDirectory())
                .resolve(safePathSegment(settings.printerId()))
                .resolve("snapshots")
                .resolve(Long.toString(created.requireId()));

        return cameraJobStore.updateSnapshotDirectory(
                created.requireId(),
                snapshotDirectory.toString(),
                now);
    }

    public Optional<CameraJob> findActive(String printerId) {
        return cameraJobStore.findActiveByPrinterId(requireText(printerId, "printerId"));
    }

    public CameraJob start(CameraSettings settings) {
        Objects.requireNonNull(settings, "settings");

        Optional<CameraJob> active = cameraJobStore.findActiveByPrinterId(settings.printerId());

        if (active.isPresent()) {
            return active.get();
        }

        Instant now = clock.instant();
        Optional<String> linkedPrintJobId = activePrintJobId(settings.printerId());

        CameraJob created = cameraJobStore.save(CameraJob.running(
                settings.printerId(),
                linkedPrintJobId.orElse(null),
                null,
                now,
                settings.captureIntervalSeconds(),
                settings.retentionSnapshotCount(),
                settings.sourceType().wireValue(),
                settings.sourceValue().orElse(null),
                pendingSnapshotDirectory(settings.printerId()),
                "Camera job started from dashboard"));

        Path snapshotDirectory = CameraStoragePaths
                .resolveBaseDirectory(settings.storageDirectory())
                .resolve(safePathSegment(settings.printerId()))
                .resolve("snapshots")
                .resolve(Long.toString(created.requireId()));

        return cameraJobStore.updateSnapshotDirectory(
                created.requireId(),
                snapshotDirectory.toString(),
                now);
    }

    public Optional<CameraJob> completeActive(String printerId, String message) {
        Optional<CameraJob> active = findActive(printerId);
        if (active.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(cameraJobStore.markStopped(
                active.get().requireId(),
                spaghettichef.persistence.CameraJobState.COMPLETED,
                clock.instant(),
                message));
    }

    public Optional<CameraJob> findById(long cameraJobId) {
    if (cameraJobId <= 0L) {
        throw new IllegalArgumentException("cameraJobId must be greater than zero");
    }

    return cameraJobStore.findById(cameraJobId);
}


    private Optional<String> activePrintJobId(String printerId) {
        try {
            return printJobStore
                    .findActivePrintFileJobByPrinterId(printerId)
                    .map(PrintJob::id);
        } catch (RuntimeException exception) {
            SpaghettiChefLog.error("Camera job could not resolve active print job printerId="
                    + printerId
                    + ": "
                    + safeDetail(exception.getMessage()));
            return Optional.empty();
        }
    }

    private static String pendingSnapshotDirectory(String printerId) {
        return "pending-camera-job-snapshot-directory/" + safePathSegment(printerId);
    }

    private static String safeDetail(String value) {
        if (value == null || value.isBlank()) {
            return "unknown error";
        }

        return value.trim();
    }

    private static String safePathSegment(String value) {
        String normalized = requireText(value, "value").replaceAll("[^A-Za-z0-9._-]", "_");

        if (normalized.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }

        return normalized;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return value.trim();
    }
}
