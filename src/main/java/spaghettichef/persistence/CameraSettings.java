package spaghettichef.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import spaghettichef.camera.CameraSourceType;
import spaghettichef.config.RuntimeDefaults;

public final class CameraSettings {

    public static final int DEFAULT_CAPTURE_INTERVAL_SECONDS = 10;
    public static final int DEFAULT_RETENTION_SNAPSHOT_COUNT = 20;
    public static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.85;
    public static final int DEFAULT_CONFIRMATIONS_REQUIRED = 3;

    private final String printerId;
    private final boolean enabled;
    private final CameraSourceType sourceType;
    private final String sourceValue;
    private final int captureIntervalSeconds;
    private final int retentionSnapshotCount;
    private final boolean analysisEnabled;
    private final boolean safetyEnabled;
    private final boolean pauseOnConfirmedSpaghetti;
    private final double confidenceThreshold;
    private final int confirmationsRequired;
    private final String ffmpegCommand;
    private final String ffmpegInputFormat;
    private final String ffmpegVideoSize;
    private final int ffmpegTimeoutMs;
    private final int ffmpegJpegQuality;
    private final String storageDirectory;
    private final boolean diagnosticLoggingEnabled;
    private final Instant updatedAt;

    public CameraSettings(
            String printerId,
            boolean enabled,
            CameraSourceType sourceType,
            String sourceValue,
            int captureIntervalSeconds,
            int retentionSnapshotCount,
            boolean analysisEnabled,
            boolean safetyEnabled,
            boolean pauseOnConfirmedSpaghetti,
            double confidenceThreshold,
            int confirmationsRequired,
            Instant updatedAt) {
        this(
                printerId,
                enabled,
                sourceType,
                sourceValue,
                captureIntervalSeconds,
                retentionSnapshotCount,
                analysisEnabled,
                safetyEnabled,
                pauseOnConfirmedSpaghetti,
                confidenceThreshold,
                confirmationsRequired,
                RuntimeDefaults.DEFAULT_CAMERA_FFMPEG_COMMAND,
                RuntimeDefaults.DEFAULT_CAMERA_FFMPEG_INPUT_FORMAT,
                RuntimeDefaults.DEFAULT_CAMERA_FFMPEG_VIDEO_SIZE,
                RuntimeDefaults.DEFAULT_CAMERA_FFMPEG_TIMEOUT_MS,
                RuntimeDefaults.DEFAULT_CAMERA_FFMPEG_JPEG_QUALITY,
                RuntimeDefaults.DEFAULT_CAMERA_STORAGE_DIRECTORY,
                false,
                updatedAt);
    }

    public CameraSettings(
            String printerId,
            boolean enabled,
            CameraSourceType sourceType,
            String sourceValue,
            int captureIntervalSeconds,
            int retentionSnapshotCount,
            boolean analysisEnabled,
            boolean safetyEnabled,
            boolean pauseOnConfirmedSpaghetti,
            double confidenceThreshold,
            int confirmationsRequired,
            String ffmpegCommand,
            String ffmpegInputFormat,
            String ffmpegVideoSize,
            int ffmpegTimeoutMs,
            int ffmpegJpegQuality,
            String storageDirectory,
            Instant updatedAt) {
        this(
                printerId,
                enabled,
                sourceType,
                sourceValue,
                captureIntervalSeconds,
                retentionSnapshotCount,
                analysisEnabled,
                safetyEnabled,
                pauseOnConfirmedSpaghetti,
                confidenceThreshold,
                confirmationsRequired,
                ffmpegCommand,
                ffmpegInputFormat,
                ffmpegVideoSize,
                ffmpegTimeoutMs,
                ffmpegJpegQuality,
                storageDirectory,
                false,
                updatedAt);
    }

    public CameraSettings(
            String printerId,
            boolean enabled,
            CameraSourceType sourceType,
            String sourceValue,
            int captureIntervalSeconds,
            int retentionSnapshotCount,
            boolean analysisEnabled,
            boolean safetyEnabled,
            boolean pauseOnConfirmedSpaghetti,
            double confidenceThreshold,
            int confirmationsRequired,
            String ffmpegCommand,
            String ffmpegInputFormat,
            String ffmpegVideoSize,
            int ffmpegTimeoutMs,
            int ffmpegJpegQuality,
            String storageDirectory,
            boolean diagnosticLoggingEnabled,
            Instant updatedAt) {
        this.printerId = requireText(printerId, "printerId");
        this.enabled = enabled;
        this.sourceType = Objects.requireNonNull(sourceType, "sourceType");
        this.sourceValue = normalizeNullableText(sourceValue);
        this.captureIntervalSeconds = requirePositive(captureIntervalSeconds, "captureIntervalSeconds");
        this.retentionSnapshotCount = requirePositive(retentionSnapshotCount, "retentionSnapshotCount");
        this.analysisEnabled = analysisEnabled;
        this.safetyEnabled = safetyEnabled;
        this.pauseOnConfirmedSpaghetti = pauseOnConfirmedSpaghetti;
        this.confidenceThreshold = requireConfidenceThreshold(confidenceThreshold);
        this.confirmationsRequired = requirePositive(confirmationsRequired, "confirmationsRequired");
        this.ffmpegCommand = requireTextOrDefault(
                ffmpegCommand,
                RuntimeDefaults.DEFAULT_CAMERA_FFMPEG_COMMAND,
                "ffmpegCommand");
        this.ffmpegInputFormat = normalizeNullableText(ffmpegInputFormat);
        this.ffmpegVideoSize = normalizeNullableText(ffmpegVideoSize);
        this.ffmpegTimeoutMs = requirePositive(ffmpegTimeoutMs, "ffmpegTimeoutMs");
        this.ffmpegJpegQuality = requirePositive(ffmpegJpegQuality, "ffmpegJpegQuality");
        this.storageDirectory = requireTextOrDefault(
                storageDirectory,
                RuntimeDefaults.DEFAULT_CAMERA_STORAGE_DIRECTORY,
                "storageDirectory");
        this.diagnosticLoggingEnabled = diagnosticLoggingEnabled;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");

        if (!enabled && sourceType != CameraSourceType.DISABLED) {
            throw new IllegalArgumentException("disabled camera settings must use source type DISABLED");
        }
        if (enabled && sourceType == CameraSourceType.DISABLED) {
            throw new IllegalArgumentException("enabled camera settings must not use source type DISABLED");
        }
    }

    public static CameraSettings disabled(String printerId, Instant updatedAt) {
        return new CameraSettings(
                printerId,
                false,
                CameraSourceType.DISABLED,
                null,
                DEFAULT_CAPTURE_INTERVAL_SECONDS,
                DEFAULT_RETENTION_SNAPSHOT_COUNT,
                false,
                false,
                false,
                DEFAULT_CONFIDENCE_THRESHOLD,
                DEFAULT_CONFIRMATIONS_REQUIRED,
                RuntimeDefaults.DEFAULT_CAMERA_FFMPEG_COMMAND,
                RuntimeDefaults.DEFAULT_CAMERA_FFMPEG_INPUT_FORMAT,
                RuntimeDefaults.DEFAULT_CAMERA_FFMPEG_VIDEO_SIZE,
                RuntimeDefaults.DEFAULT_CAMERA_FFMPEG_TIMEOUT_MS,
                RuntimeDefaults.DEFAULT_CAMERA_FFMPEG_JPEG_QUALITY,
                RuntimeDefaults.DEFAULT_CAMERA_STORAGE_DIRECTORY,
                false,
                updatedAt);
    }

    public static CameraSettings simulated(String printerId, Instant updatedAt) {
        return new CameraSettings(
                printerId,
                true,
                CameraSourceType.SIMULATED,
                "default",
                DEFAULT_CAPTURE_INTERVAL_SECONDS,
                DEFAULT_RETENTION_SNAPSHOT_COUNT,
                false,
                false,
                false,
                DEFAULT_CONFIDENCE_THRESHOLD,
                DEFAULT_CONFIRMATIONS_REQUIRED,
                RuntimeDefaults.DEFAULT_CAMERA_FFMPEG_COMMAND,
                RuntimeDefaults.DEFAULT_CAMERA_FFMPEG_INPUT_FORMAT,
                RuntimeDefaults.DEFAULT_CAMERA_FFMPEG_VIDEO_SIZE,
                RuntimeDefaults.DEFAULT_CAMERA_FFMPEG_TIMEOUT_MS,
                RuntimeDefaults.DEFAULT_CAMERA_FFMPEG_JPEG_QUALITY,
                RuntimeDefaults.DEFAULT_CAMERA_STORAGE_DIRECTORY,
                false,
                updatedAt);
    }

    public String printerId() {
        return printerId;
    }

    public boolean enabled() {
        return enabled;
    }

    public CameraSourceType sourceType() {
        return sourceType;
    }

    public Optional<String> sourceValue() {
        return Optional.ofNullable(sourceValue);
    }

    public int captureIntervalSeconds() {
        return captureIntervalSeconds;
    }

    public int retentionSnapshotCount() {
        return retentionSnapshotCount;
    }

    public boolean analysisEnabled() {
        return analysisEnabled;
    }

    public boolean safetyEnabled() {
        return safetyEnabled;
    }

    public boolean pauseOnConfirmedSpaghetti() {
        return pauseOnConfirmedSpaghetti;
    }

    public double confidenceThreshold() {
        return confidenceThreshold;
    }

    public int confirmationsRequired() {
        return confirmationsRequired;
    }

    public String ffmpegCommand() {
        return ffmpegCommand;
    }

    public Optional<String> ffmpegInputFormat() {
        return Optional.ofNullable(ffmpegInputFormat);
    }

    public Optional<String> ffmpegVideoSize() {
        return Optional.ofNullable(ffmpegVideoSize);
    }

    public int ffmpegTimeoutMs() {
        return ffmpegTimeoutMs;
    }

    public int ffmpegJpegQuality() {
        return ffmpegJpegQuality;
    }

    public String storageDirectory() {
        return storageDirectory;
    }

    public boolean diagnosticLoggingEnabled() {
        return diagnosticLoggingEnabled;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String requireTextOrDefault(String value, String fallback, String fieldName) {
        String selected = value == null || value.isBlank() ? fallback : value;
        return requireText(selected, fieldName);
    }

    private static int requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero");
        }
        return value;
    }

    private static double requireConfidenceThreshold(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0.0 || value > 1.0) {
            throw new IllegalArgumentException("confidenceThreshold must be greater than 0.0 and less than or equal to 1.0");
        }
        return value;
    }
}
