package printerhub.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import printerhub.persistence.CameraSettings;
import printerhub.persistence.CameraSettingsStore;
import printerhub.persistence.DatabaseInitializer;

class CameraSettingsServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-05-18T10:15:30Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @AfterEach
    void clearDatabaseProperty() {
        System.clearProperty("printerhub.databaseFile");
    }

    @Test
    void loadReturnsDisabledDefaultWhenNoSettingsExist() {
        useDatabase("camera-settings-service-default.db");

        CameraSettingsService service = new CameraSettingsService(new CameraSettingsStore(), FIXED_CLOCK);

        CameraSettings settings = service.load("printer-1");

        assertEquals("printer-1", settings.printerId());
        assertFalse(settings.enabled());
        assertEquals(CameraSourceType.DISABLED, settings.sourceType());
    }

    @Test
    void disableStoresDisabledSettings() {
        useDatabase("camera-settings-service-disable.db");

        CameraSettingsService service = new CameraSettingsService(new CameraSettingsStore(), FIXED_CLOCK);

        CameraSettings settings = service.disable(" printer-1 ");

        assertEquals("printer-1", settings.printerId());
        assertFalse(settings.enabled());
        assertEquals(CameraSourceType.DISABLED, settings.sourceType());
        assertEquals(FIXED_INSTANT, settings.updatedAt());

        CameraSettings loaded = service.load("printer-1");
        assertFalse(loaded.enabled());
        assertEquals(CameraSourceType.DISABLED, loaded.sourceType());
    }

    @Test
    void enableSimulatedStoresSimulatedSettings() {
        useDatabase("camera-settings-service-simulated.db");

        CameraSettingsService service = new CameraSettingsService(new CameraSettingsStore(), FIXED_CLOCK);

        CameraSettings settings = service.enableSimulated("printer-1");

        assertEquals("printer-1", settings.printerId());
        assertTrue(settings.enabled());
        assertEquals(CameraSourceType.SIMULATED, settings.sourceType());
        assertEquals("default", settings.sourceValue().orElseThrow());
        assertEquals(CameraSettings.DEFAULT_CAPTURE_INTERVAL_SECONDS, settings.captureIntervalSeconds());
        assertEquals(CameraSettings.DEFAULT_RETENTION_SNAPSHOT_COUNT, settings.retentionSnapshotCount());
        assertFalse(settings.analysisEnabled());
        assertFalse(settings.safetyEnabled());
        assertFalse(settings.pauseOnConfirmedSpaghetti());
        assertEquals(CameraSettings.DEFAULT_CONFIDENCE_THRESHOLD, settings.confidenceThreshold());
        assertEquals(CameraSettings.DEFAULT_CONFIRMATIONS_REQUIRED, settings.confirmationsRequired());
        assertEquals(FIXED_INSTANT, settings.updatedAt());
    }

    @Test
    void enableSnapshotFolderStoresSnapshotFolderSettings() {
        useDatabase("camera-settings-service-folder.db");

        CameraSettingsService service = new CameraSettingsService(new CameraSettingsStore(), FIXED_CLOCK);
        Path folder = tempDir.resolve("snapshots");

        CameraSettings settings = service.enableSnapshotFolder("printer-1", folder);

        assertEquals("printer-1", settings.printerId());
        assertTrue(settings.enabled());
        assertEquals(CameraSourceType.SNAPSHOT_FOLDER, settings.sourceType());
        assertEquals(folder.toString(), settings.sourceValue().orElseThrow());
        assertEquals(FIXED_INSTANT, settings.updatedAt());
    }

    @Test
    void savePersistsProvidedSettings() {
        useDatabase("camera-settings-service-save.db");

        CameraSettingsService service = new CameraSettingsService(new CameraSettingsStore(), FIXED_CLOCK);
        CameraSettings settings = CameraSettings.simulated("printer-1", FIXED_INSTANT);

        CameraSettings saved = service.save(settings);

        assertEquals(settings, saved);

        CameraSettings loaded = service.load("printer-1");
        assertTrue(loaded.enabled());
        assertEquals(CameraSourceType.SIMULATED, loaded.sourceType());
    }

    @Test
    void disableFailsForBlankPrinterId() {
        CameraSettingsService service = new CameraSettingsService(new CameraSettingsStore(), FIXED_CLOCK);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.disable("   "));

        assertEquals("printerId must not be blank", exception.getMessage());
    }

    @Test
    void enableSimulatedFailsForBlankPrinterId() {
        CameraSettingsService service = new CameraSettingsService(new CameraSettingsStore(), FIXED_CLOCK);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.enableSimulated("   "));

        assertEquals("printerId must not be blank", exception.getMessage());
    }

    @Test
    void enableSnapshotFolderFailsForNullFolder() {
        CameraSettingsService service = new CameraSettingsService(new CameraSettingsStore(), FIXED_CLOCK);

        assertThrows(
                NullPointerException.class,
                () -> service.enableSnapshotFolder("printer-1", null));
    }

    @Test
    void constructorFailsForNullStore() {
        assertThrows(
                NullPointerException.class,
                () -> new CameraSettingsService(null, FIXED_CLOCK));
    }

    @Test
    void constructorFailsForNullClock() {
        assertThrows(
                NullPointerException.class,
                () -> new CameraSettingsService(new CameraSettingsStore(), null));
    }

    private void useDatabase(String fileName) {
        Path dbFile = tempDir.resolve(fileName);
        System.setProperty("printerhub.databaseFile", dbFile.toString());
        new DatabaseInitializer().initialize();
    }
}