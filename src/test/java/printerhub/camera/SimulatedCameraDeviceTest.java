package printerhub.camera;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class SimulatedCameraDeviceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-05-18T10:15:30Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @Test
    void simulatedCameraIsAvailableBeforeClose() {
        SimulatedCameraDevice device = new SimulatedCameraDevice("p1", FIXED_CLOCK);

        assertTrue(device.isAvailable());
        assertEquals("simulated-camera:p1", device.describe());
    }

    @Test
    void simulatedCameraCapturesJpegFrame() {
        SimulatedCameraDevice device = new SimulatedCameraDevice("p1", FIXED_CLOCK, 320, 240);

        Optional<CameraFrame> frame = device.captureFrame();

        assertTrue(frame.isPresent());
        assertEquals("p1", frame.get().printerId());
        assertEquals(FIXED_INSTANT, frame.get().capturedAt());
        assertEquals("image/jpeg", frame.get().contentType());
        assertTrue(frame.get().byteCount() > 0);
        assertEquals(320, frame.get().width().orElseThrow());
        assertEquals(240, frame.get().height().orElseThrow());
        assertEquals("simulated-camera:p1", frame.get().sourceDescription().orElseThrow());
    }

    @Test
    void simulatedCameraReturnsDefensiveByteCopy() {
        SimulatedCameraDevice device = new SimulatedCameraDevice("p1", FIXED_CLOCK);

        CameraFrame frame = device.captureFrame().orElseThrow();

        byte[] firstCopy = frame.bytes();
        byte[] secondCopy = frame.bytes();

        assertArrayEquals(firstCopy, secondCopy);

        firstCopy[0] = (byte) (firstCopy[0] + 1);

        assertFalse(firstCopy[0] == frame.bytes()[0]);
    }

    @Test
    void simulatedCameraReturnsNoFrameAfterClose() {
        SimulatedCameraDevice device = new SimulatedCameraDevice("p1", FIXED_CLOCK);

        device.close();

        assertFalse(device.isAvailable());
        assertTrue(device.captureFrame().isEmpty());
    }

    @Test
    void simulatedCameraRejectsBlankPrinterId() {
        assertThrows(IllegalArgumentException.class, () -> new SimulatedCameraDevice(" ", FIXED_CLOCK));
    }

    @Test
    void simulatedCameraRejectsInvalidWidth() {
        assertThrows(IllegalArgumentException.class, () -> new SimulatedCameraDevice("p1", FIXED_CLOCK, 0, 240));
    }

    @Test
    void simulatedCameraRejectsInvalidHeight() {
        assertThrows(IllegalArgumentException.class, () -> new SimulatedCameraDevice("p1", FIXED_CLOCK, 320, 0));
    }

    @Test
    void simulatedCameraRejectsNullClock() {
        assertThrows(NullPointerException.class, () -> new SimulatedCameraDevice("p1", null, 320, 240));
    }
}