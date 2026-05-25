package spaghettichef.camera;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import spaghettichef.persistence.CameraSettings;
import spaghettichef.persistence.CameraSettingsStore;

public final class CameraSettingsService {

    private final CameraSettingsStore settingsStore;
    private final Clock clock;

    public CameraSettingsService(CameraSettingsStore settingsStore) {
        this(settingsStore, Clock.systemUTC());
    }

    public CameraSettingsService(CameraSettingsStore settingsStore, Clock clock) {
        this.settingsStore = Objects.requireNonNull(settingsStore, "settingsStore");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CameraSettings load(String printerId) {
        return settingsStore.loadOrDefault(printerId);
    }

    public CameraSettings save(CameraSettings settings) {
        return settingsStore.save(Objects.requireNonNull(settings, "settings"));
    }

    public CameraSettings disable(String printerId) {
        CameraSettings settings = CameraSettings.disabled(
                requirePrinterId(printerId),
                Instant.now(clock));

        return settingsStore.save(settings);
    }

    public CameraSettings enableSimulated(String printerId) {
        CameraSettings settings = CameraSettings.simulated(
                requirePrinterId(printerId),
                Instant.now(clock));

        return settingsStore.save(settings);
    }

    public CameraSettings enableSnapshotFolder(String printerId, Path folder) {
        Objects.requireNonNull(folder, "folder");

        CameraSettings settings = new CameraSettings(
                requirePrinterId(printerId),
                true,
                CameraSourceType.SNAPSHOT_FOLDER,
                folder.toString(),
                CameraSettings.DEFAULT_CAPTURE_INTERVAL_SECONDS,
                CameraSettings.DEFAULT_RETENTION_SNAPSHOT_COUNT,
                false,
                false,
                false,
                CameraSettings.DEFAULT_CONFIDENCE_THRESHOLD,
                CameraSettings.DEFAULT_CONFIRMATIONS_REQUIRED,
                Instant.now(clock));

        return settingsStore.save(settings);
    }

    private static String requirePrinterId(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException("printerId must not be blank");
        }

        return printerId.trim();
    }
}
