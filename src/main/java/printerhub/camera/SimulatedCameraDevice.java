package printerhub.camera;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import javax.imageio.ImageIO;

public final class SimulatedCameraDevice implements CameraDevice {

    private static final int DEFAULT_WIDTH = 320;
    private static final int DEFAULT_HEIGHT = 240;

    private final String printerId;
    private final Clock clock;
    private final int width;
    private final int height;
    private boolean closed;

    public SimulatedCameraDevice(String printerId) {
        this(printerId, Clock.systemUTC(), DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public SimulatedCameraDevice(String printerId, Clock clock) {
        this(printerId, clock, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public SimulatedCameraDevice(String printerId, Clock clock, int width, int height) {
        this.printerId = requireText(printerId, "printerId");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.width = requirePositive(width, "width");
        this.height = requirePositive(height, "height");
    }

    @Override
    public Optional<CameraFrame> captureFrame() {
        if (closed) {
            return Optional.empty();
        }

        Instant capturedAt = Instant.now(clock);
        byte[] jpegBytes = renderJpeg(capturedAt);

        return Optional.of(CameraFrame.jpeg(
                printerId,
                capturedAt,
                jpegBytes,
                width,
                height,
                describe()));
    }

    @Override
    public boolean isAvailable() {
        return !closed;
    }

    @Override
    public String describe() {
        return "simulated-camera:" + printerId;
    }

    @Override
    public void close() {
        closed = true;
    }

    private byte[] renderJpeg(Instant capturedAt) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int timeSeed = Math.abs(capturedAt.toString().hashCode());
        int printerSeed = Math.abs(printerId.hashCode());

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int base = 220 + ((x + y + timeSeed) % 28);
                int red = clamp(base);
                int green = clamp(base - 6);
                int blue = clamp(base - 12);

                if (isFrameBorder(x, y) || isBuildPlateLine(x, y) || isNozzleMarker(x, y, printerSeed)) {
                    red = 45;
                    green = 45;
                    blue = 45;
                }

                if (isDiagonalFilament(x, y)) {
                    red = 110;
                    green = 110;
                    blue = 110;
                }

                image.setRGB(x, y, (red << 16) | (green << 8) | blue);
            }
        }

        return writeJpeg(image);
    }

    private boolean isFrameBorder(int x, int y) {
        int left = Math.max(8, width / 16);
        int right = width - left;
        int top = Math.max(8, height / 5);
        int bottom = height - Math.max(8, height / 8);

        return (x >= left && x <= right && (Math.abs(y - top) <= 1 || Math.abs(y - bottom) <= 1))
                || (y >= top && y <= bottom && (Math.abs(x - left) <= 1 || Math.abs(x - right) <= 1));
    }

    private boolean isBuildPlateLine(int x, int y) {
        int plateY = height - Math.max(12, height / 6);
        return y >= plateY && y <= plateY + 2 && x > width / 8 && x < width - width / 8;
    }

    private boolean isDiagonalFilament(int x, int y) {
        int expectedY = height - 35 - ((x * Math.max(1, height - 90)) / Math.max(1, width));
        int mirroredY = 55 + ((x * Math.max(1, height - 90)) / Math.max(1, width));
        return Math.abs(y - expectedY) <= 1 || Math.abs(y - mirroredY) <= 1;
    }

    private boolean isNozzleMarker(int x, int y, int seed) {
        int centerX = width / 2 + (seed % Math.max(1, width / 8)) - width / 16;
        int centerY = Math.max(18, height / 4);
        return Math.abs(x - centerX) + Math.abs(y - centerY) <= Math.max(4, width / 48);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static byte[] writeJpeg(BufferedImage image) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            boolean written = ImageIO.write(image, "jpg", output);
            if (!written) {
                throw new IllegalStateException("No JPEG writer available");
            }
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to render simulated camera frame", exception);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static int requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero");
        }
        return value;
    }
}
