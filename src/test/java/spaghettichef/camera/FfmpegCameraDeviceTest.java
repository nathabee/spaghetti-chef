package spaghettichef.camera;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FfmpegCameraDeviceTest {

    @Test
    void captureCommandIncludesConfiguredLinuxDeviceOptions() {
        FfmpegCameraDevice device = new FfmpegCameraDevice(
                "printer-1",
                "/dev/video0",
                "ffmpeg",
                "v4l2",
                "1280x720",
                5000,
                4,
                fixedClock());

        List<String> command = device.captureCommand(Path.of("/tmp/frame.jpg"));

        assertEquals(List.of(
                "ffmpeg",
                "-hide_banner",
                "-loglevel",
                "error",
                "-y",
                "-f",
                "v4l2",
                "-video_size",
                "1280x720",
                "-i",
                "/dev/video0",
                "-frames:v",
                "1",
                "-q:v",
                "4",
                "/tmp/frame.jpg"), command);
    }

    @Test
    void captureCommandSupportsWindowsDshowCameraName() {
        FfmpegCameraDevice device = new FfmpegCameraDevice(
                "printer-1",
                "video=Integrated Camera",
                "ffmpeg",
                "dshow",
                "640x480",
                5000,
                3,
                fixedClock());

        List<String> command = device.captureCommand(Path.of("C:/Temp/frame.jpg"));

        assertTrue(command.contains("dshow"));
        assertTrue(command.contains("video=Integrated Camera"));
    }

    @Test
    void describeIdentifiesFfmpegSource() {
        FfmpegCameraDevice device = new FfmpegCameraDevice(
                "printer-1",
                "/dev/video0",
                "ffmpeg",
                "",
                "",
                5000,
                3,
                fixedClock());

        assertEquals("ffmpeg-camera:/dev/video0", device.describe());
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-05-19T08:00:00Z"), ZoneOffset.UTC);
    }
}
