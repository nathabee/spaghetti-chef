package printerhub.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotFolderCameraDeviceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-05-18T10:15:30Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @Test
    void snapshotFolderCameraIsUnavailableWhenFolderDoesNotExist() {
        Path missingFolder = tempDir.resolve("missing");
        SnapshotFolderCameraDevice device = new SnapshotFolderCameraDevice("p1", missingFolder, FIXED_CLOCK);

        assertFalse(device.isAvailable());
        assertTrue(device.captureFrame().isEmpty());
    }

    @Test
    void snapshotFolderCameraIsAvailableWhenFolderExists() throws Exception {
        Path folder = Files.createDirectory(tempDir.resolve("camera"));
        SnapshotFolderCameraDevice device = new SnapshotFolderCameraDevice("p1", folder, FIXED_CLOCK);

        assertTrue(device.isAvailable());
        assertTrue(device.describe().startsWith("snapshot-folder:"));
    }

    @Test
    void snapshotFolderCameraReturnsEmptyWhenFolderContainsNoImages() throws Exception {
        Path folder = Files.createDirectory(tempDir.resolve("camera"));
        Files.writeString(folder.resolve("notes.txt"), "not an image");

        SnapshotFolderCameraDevice device = new SnapshotFolderCameraDevice("p1", folder, FIXED_CLOCK);

        assertTrue(device.isAvailable());
        assertTrue(device.captureFrame().isEmpty());
    }

    @Test
    void snapshotFolderCameraCapturesLatestJpegImage() throws Exception {
        Path folder = Files.createDirectory(tempDir.resolve("camera"));

        Path older = folder.resolve("older.jpg");
        Path latest = folder.resolve("fresh-camera-frame.jpg");

        writeImage(older, "jpg", 80, 60);
        Thread.sleep(5);
        writeImage(latest, "jpg", 120, 90);

        SnapshotFolderCameraDevice device = new SnapshotFolderCameraDevice("p1", folder, FIXED_CLOCK);

        Optional<CameraFrame> frame = device.captureFrame();

        assertTrue(frame.isPresent());
        assertEquals("p1", frame.get().printerId());
        assertEquals(FIXED_INSTANT, frame.get().capturedAt());
        assertEquals("image/jpeg", frame.get().contentType());
        assertEquals(120, frame.get().width().orElseThrow());
        assertEquals(90, frame.get().height().orElseThrow());
        assertTrue(frame.get().byteCount() > 0);
        assertTrue(frame.get().sourceDescription().orElseThrow().contains("fresh-camera-frame.jpg"));
    }

    @Test
    void snapshotFolderCameraIgnoresPrinterHubGeneratedFrames() throws Exception {
        Path folder = Files.createDirectory(tempDir.resolve("camera"));

        writeImage(folder.resolve("latest.jpg"), "jpg", 120, 90);
        writeImage(folder.resolve("previous.jpg"), "jpg", 120, 90);
        writeImage(folder.resolve("delta.jpg"), "jpg", 120, 90);

        SnapshotFolderCameraDevice device = new SnapshotFolderCameraDevice("p1", folder, FIXED_CLOCK);

        assertTrue(device.captureFrame().isEmpty());
    }

    @Test
    void snapshotFolderCameraCapturesPngImage() throws Exception {
        Path folder = Files.createDirectory(tempDir.resolve("camera"));
        Path image = folder.resolve("snapshot.png");

        writeImage(image, "png", 64, 48);

        SnapshotFolderCameraDevice device = new SnapshotFolderCameraDevice("p1", folder, FIXED_CLOCK);

        CameraFrame frame = device.captureFrame().orElseThrow();

        assertEquals("image/png", frame.contentType());
        assertEquals(64, frame.width().orElseThrow());
        assertEquals(48, frame.height().orElseThrow());
    }

    @Test
    void snapshotFolderCameraIgnoresUnsupportedFiles() throws Exception {
        Path folder = Files.createDirectory(tempDir.resolve("camera"));
        Files.writeString(folder.resolve("snapshot.gif"), "not used");

        SnapshotFolderCameraDevice device = new SnapshotFolderCameraDevice("p1", folder, FIXED_CLOCK);

        assertTrue(device.captureFrame().isEmpty());
    }

    @Test
    void snapshotFolderCameraReturnsNoFrameAfterClose() throws Exception {
        Path folder = Files.createDirectory(tempDir.resolve("camera"));
        writeImage(folder.resolve("snapshot.jpg"), "jpg", 80, 60);

        SnapshotFolderCameraDevice device = new SnapshotFolderCameraDevice("p1", folder, FIXED_CLOCK);

        device.close();

        assertFalse(device.isAvailable());
        assertTrue(device.captureFrame().isEmpty());
    }

    @Test
    void snapshotFolderCameraRejectsBlankPrinterId() {
        assertThrows(IllegalArgumentException.class, () -> new SnapshotFolderCameraDevice(" ", tempDir, FIXED_CLOCK));
    }

    @Test
    void snapshotFolderCameraRejectsNullFolder() {
        assertThrows(NullPointerException.class, () -> new SnapshotFolderCameraDevice("p1", null, FIXED_CLOCK));
    }

    @Test
    void snapshotFolderCameraRejectsNullClock() {
        assertThrows(NullPointerException.class, () -> new SnapshotFolderCameraDevice("p1", tempDir, null));
    }

    private static void writeImage(Path path, String format, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean border = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                image.setRGB(x, y, border ? 0x000000 : 0xFFFFFF);
            }
        }

        boolean written = ImageIO.write(image, format, path.toFile());
        if (!written) {
            throw new IllegalStateException("No image writer available for format: " + format);
        }
    }
}
