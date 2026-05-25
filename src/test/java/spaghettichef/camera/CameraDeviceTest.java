package spaghettichef.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class CameraDeviceTest {

    @Test
    void noopCameraIsUnavailableAndReturnsNoFrame() {
        NoopCameraDevice device = new NoopCameraDevice();

        assertFalse(device.isAvailable());
        assertEquals("noop-camera", device.describe());
        assertEquals(Optional.empty(), device.captureFrame());
    }

    @Test
    void noopCameraNormalizesBlankDescription() {
        NoopCameraDevice device = new NoopCameraDevice("   ");

        assertEquals("noop-camera", device.describe());
    }

    @Test
    void noopCameraUsesCustomDescription() {
        NoopCameraDevice device = new NoopCameraDevice("camera-disabled-for-test");

        assertEquals("camera-disabled-for-test", device.describe());
    }

    @Test
    void closingNoopCameraIsSafe() {
        NoopCameraDevice device = new NoopCameraDevice();

        device.close();
        device.close();

        assertFalse(device.isAvailable());
        assertEquals(Optional.empty(), device.captureFrame());
    }
}