package spaghettichef.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import spaghettichef.camera.CameraCalculationEngineSettingsService;
import spaghettichef.config.RuntimeDefaults;

import static org.junit.jupiter.api.Assertions.*;

class CameraCalculationEngineSettingsStoreTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearDatabaseProperty() {
        System.clearProperty("spaghettichef.databaseFile");
    }

    @Test
    void initializeCreatesBuiltInEngineSettings() {
        useDatabase("camera-engine-settings-defaults.db");

        List<CameraCalculationEngineSettings> settings = new CameraCalculationEngineSettingsStore().findAll();

        assertEquals(2, settings.size());
        CameraCalculationEngineSettings javaSettings = settings.get(0);
        CameraCalculationEngineSettings rustSettings = settings.get(1);
        assertEquals("JAVA_BASIC_DELTA", javaSettings.engineName());
        assertEquals("JAVA_BASIC_DELTA", javaSettings.adapterType());
        assertEquals("Java basic delta", javaSettings.engineLabel());
        assertTrue(javaSettings.enabled());
        assertEquals(RuntimeDefaults.DEFAULT_CAMERA_CALCULATION_METHOD_NAME, javaSettings.defaultMethodName());
        assertEquals(RuntimeDefaults.DEFAULT_CAMERA_CALCULATION_CONFIDENCE_THRESHOLD,
                javaSettings.defaultConfidenceThreshold());
        assertNull(javaSettings.executablePath());

        assertEquals("RUST_IMG_ANALYZER", rustSettings.engineName());
        assertEquals("EXTERNAL_CLI", rustSettings.adapterType());
        assertEquals("Rust img-analyzer", rustSettings.engineLabel());
        assertTrue(rustSettings.enabled());
        assertEquals(RuntimeDefaults.DEFAULT_CAMERA_RUST_CLI_METHOD, rustSettings.defaultCliMethod());
        assertNull(rustSettings.executablePath());
    }

    @Test
    void savePersistsAdminModifiedSettings() {
        useDatabase("camera-engine-settings-save.db");
        CameraCalculationEngineSettingsStore store = new CameraCalculationEngineSettingsStore();
        CameraCalculationEngineSettings rustSettings = store.findByEngineName("RUST_IMG_ANALYZER").orElseThrow();

        CameraCalculationEngineSettings saved = store.save(new CameraCalculationEngineSettings(
                rustSettings.engineName(),
                rustSettings.adapterType(),
                "Rust tuned",
                false,
                "spaghetti-tuned",
                0.70,
                "{\"profile\":\"tuned\"}",
                "delta-tuned",
                "/opt/spaghetti/img-analyzer",
                12_345,
                5,
                rustSettings.createdAt(),
                Instant.now()));

        CameraCalculationEngineSettings loaded = store.findByEngineName("RUST_IMG_ANALYZER").orElseThrow();
        assertEquals(saved.engineName(), loaded.engineName());
        assertEquals("Rust tuned", loaded.engineLabel());
        assertFalse(loaded.enabled());
        assertEquals("spaghetti-tuned", loaded.defaultMethodName());
        assertEquals(0.70, loaded.defaultConfidenceThreshold());
        assertEquals("{\"profile\":\"tuned\"}", loaded.defaultParameterJson());
        assertEquals("delta-tuned", loaded.defaultCliMethod());
        assertEquals("/opt/spaghetti/img-analyzer", loaded.executablePath());
        assertEquals(12_345, loaded.timeoutMs());
        assertEquals(5, loaded.sortOrder());
    }

    @Test
    void initializerDoesNotOverwriteAdminModifiedSettings() {
        useDatabase("camera-engine-settings-preserve.db");
        CameraCalculationEngineSettingsStore store = new CameraCalculationEngineSettingsStore();
        CameraCalculationEngineSettings javaSettings = store.findByEngineName("JAVA_BASIC_DELTA").orElseThrow();
        store.save(new CameraCalculationEngineSettings(
                javaSettings.engineName(),
                javaSettings.adapterType(),
                "Java custom label",
                true,
                javaSettings.defaultMethodName(),
                0.42,
                javaSettings.defaultParameterJson(),
                javaSettings.defaultCliMethod(),
                javaSettings.executablePath(),
                javaSettings.timeoutMs(),
                javaSettings.sortOrder(),
                javaSettings.createdAt(),
                Instant.now()));

        new DatabaseInitializer().initialize();

        CameraCalculationEngineSettings loaded = store.findByEngineName("JAVA_BASIC_DELTA").orElseThrow();
        assertEquals("Java custom label", loaded.engineLabel());
        assertEquals(0.42, loaded.defaultConfidenceThreshold());
    }

    @Test
    void serviceCachesSettingsUntilRefreshOrSave() {
        useDatabase("camera-engine-settings-cache.db");
        CameraCalculationEngineSettingsStore store = new CameraCalculationEngineSettingsStore();
        CameraCalculationEngineSettingsService service = new CameraCalculationEngineSettingsService(store);

        assertEquals("Java basic delta", service.findByEngineName("JAVA_BASIC_DELTA").orElseThrow().engineLabel());
        CameraCalculationEngineSettings javaSettings = store.findByEngineName("JAVA_BASIC_DELTA").orElseThrow();
        store.save(new CameraCalculationEngineSettings(
                javaSettings.engineName(),
                javaSettings.adapterType(),
                "Java saved behind cache",
                javaSettings.enabled(),
                javaSettings.defaultMethodName(),
                javaSettings.defaultConfidenceThreshold(),
                javaSettings.defaultParameterJson(),
                javaSettings.defaultCliMethod(),
                javaSettings.executablePath(),
                javaSettings.timeoutMs(),
                javaSettings.sortOrder(),
                javaSettings.createdAt(),
                Instant.now()));

        assertEquals("Java basic delta", service.findByEngineName("JAVA_BASIC_DELTA").orElseThrow().engineLabel());
        service.refresh();
        assertEquals("Java saved behind cache", service.findByEngineName("JAVA_BASIC_DELTA").orElseThrow().engineLabel());
    }

    private void useDatabase(String filename) {
        System.setProperty("spaghettichef.databaseFile", tempDir.resolve(filename).toString());
        new DatabaseInitializer().initialize();
    }
}
