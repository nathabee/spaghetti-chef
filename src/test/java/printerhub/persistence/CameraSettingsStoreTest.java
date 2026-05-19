package printerhub.persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import printerhub.camera.CameraSourceType;

class CameraSettingsStoreTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearDatabaseProperty() {
        System.clearProperty("printerhub.databaseFile");
    }

    @Test
    void saveStoresCameraSettings() throws Exception {
        useDatabase("camera-settings-save.db");

        CameraSettingsStore store = new CameraSettingsStore();
        CameraSettings settings = new CameraSettings(
                "printer-1",
                true,
                CameraSourceType.SNAPSHOT_FOLDER,
                "/tmp/camera/p1",
                12,
                30,
                false,
                false,
                false,
                0.85,
                3,
                "ffmpeg",
                "v4l2",
                "1280x720",
                6000,
                4,
                Instant.parse("2026-05-18T10:00:00Z"));

        CameraSettings saved = store.save(settings);

        assertSame(settings, saved);
        assertEquals(1, countRows());

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        """
                                SELECT
                                    printer_id,
                                    enabled,
                                    source_type,
                                    source_value,
                                    capture_interval_seconds,
                                    retention_snapshot_count,
                                    analysis_enabled,
                                    safety_enabled,
                                    pause_on_confirmed_spaghetti,
                                    confidence_threshold,
                                    confirmations_required,
                                    ffmpeg_command,
                                    ffmpeg_input_format,
                                    ffmpeg_video_size,
                                    ffmpeg_timeout_ms,
                                    ffmpeg_jpeg_quality,
                                    updated_at
                                FROM camera_settings
                                WHERE printer_id = ?
                                """)) {
            statement.setString(1, "printer-1");

            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals("printer-1", resultSet.getString("printer_id"));
                assertEquals(1, resultSet.getInt("enabled"));
                assertEquals("snapshot-folder", resultSet.getString("source_type"));
                assertEquals("/tmp/camera/p1", resultSet.getString("source_value"));
                assertEquals(12, resultSet.getInt("capture_interval_seconds"));
                assertEquals(30, resultSet.getInt("retention_snapshot_count"));
                assertEquals(0, resultSet.getInt("analysis_enabled"));
                assertEquals(0, resultSet.getInt("safety_enabled"));
                assertEquals(0, resultSet.getInt("pause_on_confirmed_spaghetti"));
                assertEquals(0.85, resultSet.getDouble("confidence_threshold"));
                assertEquals(3, resultSet.getInt("confirmations_required"));
                assertEquals("ffmpeg", resultSet.getString("ffmpeg_command"));
                assertEquals("v4l2", resultSet.getString("ffmpeg_input_format"));
                assertEquals("1280x720", resultSet.getString("ffmpeg_video_size"));
                assertEquals(6000, resultSet.getInt("ffmpeg_timeout_ms"));
                assertEquals(4, resultSet.getInt("ffmpeg_jpeg_quality"));
                assertEquals("2026-05-18T10:00:00Z", resultSet.getString("updated_at"));
            }
        }
    }

    @Test
    void saveUpdatesExistingCameraSettings() {
        useDatabase("camera-settings-update.db");

        CameraSettingsStore store = new CameraSettingsStore();

        store.save(CameraSettings.disabled(
                "printer-1",
                Instant.parse("2026-05-18T10:00:00Z")));

        store.save(new CameraSettings(
                "printer-1",
                true,
                CameraSourceType.SIMULATED,
                "default",
                15,
                40,
                true,
                true,
                true,
                0.9,
                4,
                "ffmpeg-custom",
                "dshow",
                "640x360",
                7000,
                2,
                Instant.parse("2026-05-18T10:05:00Z")));

        CameraSettings loaded = store.findByPrinterId("printer-1").orElseThrow();

        assertTrue(loaded.enabled());
        assertEquals(CameraSourceType.SIMULATED, loaded.sourceType());
        assertEquals("default", loaded.sourceValue().orElseThrow());
        assertEquals(15, loaded.captureIntervalSeconds());
        assertEquals(40, loaded.retentionSnapshotCount());
        assertTrue(loaded.analysisEnabled());
        assertTrue(loaded.safetyEnabled());
        assertTrue(loaded.pauseOnConfirmedSpaghetti());
        assertEquals(0.9, loaded.confidenceThreshold());
        assertEquals(4, loaded.confirmationsRequired());
        assertEquals("ffmpeg-custom", loaded.ffmpegCommand());
        assertEquals("dshow", loaded.ffmpegInputFormat().orElseThrow());
        assertEquals("640x360", loaded.ffmpegVideoSize().orElseThrow());
        assertEquals(7000, loaded.ffmpegTimeoutMs());
        assertEquals(2, loaded.ffmpegJpegQuality());
        assertEquals(Instant.parse("2026-05-18T10:05:00Z"), loaded.updatedAt());
    }

    @Test
    void findByPrinterIdReturnsEmptyWhenMissing() {
        useDatabase("camera-settings-missing.db");

        CameraSettingsStore store = new CameraSettingsStore();

        Optional<CameraSettings> settings = store.findByPrinterId("printer-1");

        assertTrue(settings.isEmpty());
    }

    @Test
    void loadOrDefaultReturnsDisabledSettingsWhenMissing() {
        useDatabase("camera-settings-default.db");

        CameraSettingsStore store = new CameraSettingsStore();

        CameraSettings settings = store.loadOrDefault("printer-1");

        assertEquals("printer-1", settings.printerId());
        assertFalse(settings.enabled());
        assertEquals(CameraSourceType.DISABLED, settings.sourceType());
    }

    @Test
    void findByPrinterIdTrimsPrinterId() {
        useDatabase("camera-settings-trim.db");

        CameraSettingsStore store = new CameraSettingsStore();
        store.save(CameraSettings.simulated(
                "printer-1",
                Instant.parse("2026-05-18T10:00:00Z")));

        Optional<CameraSettings> loaded = store.findByPrinterId("  printer-1  ");

        assertTrue(loaded.isPresent());
    }

    @Test
    void saveFailsForNullSettings() {
        CameraSettingsStore store = new CameraSettingsStore();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> store.save(null));

        assertEquals("camera settings must not be null", exception.getMessage());
    }

    @Test
    void findByPrinterIdFailsForBlankPrinterId() {
        CameraSettingsStore store = new CameraSettingsStore();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> store.findByPrinterId("   "));

        assertEquals("printerId must not be blank", exception.getMessage());
    }

    @Test
    void saveWrapsDatabaseFailure() {
        System.setProperty("printerhub.databaseFile", tempDir.resolve("not-a-db-dir").toString());
        assertDoesNotThrow(() -> java.nio.file.Files.createDirectories(tempDir.resolve("not-a-db-dir")));

        CameraSettingsStore store = new CameraSettingsStore();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> store.save(CameraSettings.disabled(
                        "printer-1",
                        Instant.parse("2026-05-18T10:00:00Z"))));

        assertEquals("Failed to save camera settings", exception.getMessage());
    }

    private void useDatabase(String fileName) {
        Path dbFile = tempDir.resolve(fileName);
        System.setProperty("printerhub.databaseFile", dbFile.toString());
        new DatabaseInitializer().initialize();
    }

    private int countRows() throws Exception {
        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM camera_settings");
                ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}
