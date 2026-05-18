package printerhub.camera;

import java.awt.Color;
import java.awt.Graphics2D;
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
        Graphics2D graphics = image.createGraphics();

        try {
            graphics.setColor(new Color(245, 245, 245));
            graphics.fillRect(0, 0, width, height);

            graphics.setColor(new Color(40, 40, 40));
            graphics.drawString("PrinterHub simulated camera", 20, 30);
            graphics.drawString("Printer: " + printerId, 20, 55);
            graphics.drawString("Captured: " + capturedAt, 20, 80);

            graphics.setColor(new Color(80, 80, 80));
            graphics.drawRect(20, 105, width - 40, height - 130);

            graphics.setColor(new Color(120, 120, 120));
            graphics.drawLine(35, height - 45, width - 35, 120);
            graphics.drawLine(35, 120, width - 35, height - 45);

            return writeJpeg(image);
        } finally {
            graphics.dispose();
        }
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