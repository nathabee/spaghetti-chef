package printerhub.camera;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class FfmpegCameraDevice implements CameraDevice {

    private final String printerId;
    private final String sourceValue;
    private final String ffmpegCommand;
    private final String inputFormat;
    private final String videoSize;
    private final int timeoutMs;
    private final int jpegQuality;
    private final Clock clock;

    public FfmpegCameraDevice(
            String printerId,
            String sourceValue,
            String ffmpegCommand,
            String inputFormat,
            String videoSize,
            int timeoutMs,
            int jpegQuality,
            Clock clock) {
        this.printerId = requireText(printerId, "printerId");
        this.sourceValue = requireText(sourceValue, "sourceValue");
        this.ffmpegCommand = requireText(ffmpegCommand, "ffmpegCommand");
        this.inputFormat = normalize(inputFormat);
        this.videoSize = normalize(videoSize);
        this.timeoutMs = requirePositive(timeoutMs, "timeoutMs");
        this.jpegQuality = requirePositive(jpegQuality, "jpegQuality");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<CameraFrame> captureFrame() {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("printerhub-camera-", ".jpg");
            List<String> command = captureCommand(tempFile);
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();

            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }

            if (process.exitValue() != 0 || !Files.isRegularFile(tempFile) || Files.size(tempFile) == 0) {
                return Optional.empty();
            }

            byte[] bytes = Files.readAllBytes(tempFile);
            return Optional.of(CameraFrame.jpeg(
                    printerId,
                    Instant.now(clock),
                    bytes,
                    null,
                    null,
                    describe()));
        } catch (IOException exception) {
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // Temporary capture cleanup failure does not affect the capture result.
                }
            }
        }
    }

    @Override
    public boolean isAvailable() {
        return !ffmpegCommand.isBlank() && !sourceValue.isBlank();
    }

    @Override
    public String describe() {
        return "ffmpeg-camera:" + sourceValue;
    }

    @Override
    public void close() {
        // Process-based captures are one-shot and hold no long-lived handle.
    }

    List<String> captureCommand(Path outputPath) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegCommand);
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("error");
        command.add("-y");

        if (inputFormat != null) {
            command.add("-f");
            command.add(inputFormat);
        }

        if (videoSize != null) {
            command.add("-video_size");
            command.add(videoSize);
        }

        command.add("-i");
        command.add(sourceValue);
        command.add("-frames:v");
        command.add("1");
        command.add("-q:v");
        command.add(Integer.toString(jpegQuality));
        command.add(outputPath.toString());
        return command;
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

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
