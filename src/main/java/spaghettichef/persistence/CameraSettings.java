package spaghettichef.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import spaghettichef.camera.CameraSourceType;
import spaghettichef.config.RuntimeDefaults;

public final class CameraSettings {

    public static final int DEFAULT_CAPTURE_INTERVAL_SECONDS = 10;
    public static final int DEFAULT_RETENTION_SNAPSHOT_COUNT = 20;
    public static final boolean DEFAULT_PURGE_AUTOMATICALLY = false;
    public static final int DEFAULT_PURGE_RETENTION_FREQUENCY = 5;
    public static final boolean DEFAULT_CAPTURE_CROP_ENABLED = false;
    public static final int DEFAULT_CAPTURE_CROP_X1_PERCENT = 0;
    public static final int DEFAULT_CAPTURE_CROP_Y1_PERCENT = 0;
    public static final int DEFAULT_CAPTURE_CROP_X2_PERCENT = 100;
    public static final int DEFAULT_CAPTURE_CROP_Y2_PERCENT = 100;
    public static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.85;
    public static final int DEFAULT_CONFIRMATIONS_REQUIRED = 3;

    private final String printerId;
    private final boolean enabled;
    private final CameraSourceType sourceType;
    private final String sourceValue;
    private final int captureIntervalSeconds;
    private final int retentionSnapshotCount;
    private final boolean purgeAutomatically;
    private final int purgeRetentionFrequency;
    private final boolean captureCropEnabled;
    private final int captureCropX1Percent;
    private final int captureCropY1Percent;
    private final int captureCropX2Percent;
    private final int captureCropY2Percent;
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
                DEFAULT_PURGE_AUTOMATICALLY,
                DEFAULT_PURGE_RETENTION_FREQUENCY,
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
                DEFAULT_PURGE_AUTOMATICALLY,
                DEFAULT_PURGE_RETENTION_FREQUENCY,
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
                diagnosticLoggingEnabled,
                DEFAULT_PURGE_AUTOMATICALLY,
                DEFAULT_PURGE_RETENTION_FREQUENCY,
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
            boolean purgeAutomatically,
            int purgeRetentionFrequency,
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
                diagnosticLoggingEnabled,
                purgeAutomatically,
                purgeRetentionFrequency,
                DEFAULT_CAPTURE_CROP_ENABLED,
                DEFAULT_CAPTURE_CROP_X1_PERCENT,
                DEFAULT_CAPTURE_CROP_Y1_PERCENT,
                DEFAULT_CAPTURE_CROP_X2_PERCENT,
                DEFAULT_CAPTURE_CROP_Y2_PERCENT,
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
            boolean purgeAutomatically,
            int purgeRetentionFrequency,
            boolean captureCropEnabled,
            int captureCropX1Percent,
            int captureCropY1Percent,
            int captureCropX2Percent,
            int captureCropY2Percent,
            Instant updatedAt) {
        this.printerId = requireText(printerId, "printerId");
        this.enabled = enabled;
        this.sourceType = Objects.requireNonNull(sourceType, "sourceType");
        this.sourceValue = normalizeNullableText(sourceValue);
        this.captureIntervalSeconds = requirePositive(captureIntervalSeconds, "captureIntervalSeconds");
        this.retentionSnapshotCount = requirePositive(retentionSnapshotCount, "retentionSnapshotCount");
        this.purgeAutomatically = purgeAutomatically;
        this.purgeRetentionFrequency = requirePositive(purgeRetentionFrequency, "purgeRetentionFrequency");
        this.captureCropEnabled = captureCropEnabled;
        this.captureCropX1Percent = requirePercent(captureCropX1Percent, "captureCropX1Percent");
        this.captureCropY1Percent = requirePercent(captureCropY1Percent, "captureCropY1Percent");
        this.captureCropX2Percent = requirePercent(captureCropX2Percent, "captureCropX2Percent");
        this.captureCropY2Percent = requirePercent(captureCropY2Percent, "captureCropY2Percent");
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

        if (this.captureCropX1Percent >= this.captureCropX2Percent) {
            throw new IllegalArgumentException("captureCropX1Percent must be smaller than captureCropX2Percent");
        }
        if (this.captureCropY1Percent >= this.captureCropY2Percent) {
            throw new IllegalArgumentException("captureCropY1Percent must be smaller than captureCropY2Percent");
        }
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
                DEFAULT_PURGE_AUTOMATICALLY,
                DEFAULT_PURGE_RETENTION_FREQUENCY,
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
                DEFAULT_PURGE_AUTOMATICALLY,
                DEFAULT_PURGE_RETENTION_FREQUENCY,
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

    public boolean purgeAutomatically() {
        return purgeAutomatically;
    }

    public int purgeRetentionFrequency() {
        return purgeRetentionFrequency;
    }

    public boolean captureCropEnabled() {
        return captureCropEnabled;
    }

    public int captureCropX1Percent() {
        return captureCropX1Percent;
    }

    public int captureCropY1Percent() {
        return captureCropY1Percent;
    }

    public int captureCropX2Percent() {
        return captureCropX2Percent;
    }

    public int captureCropY2Percent() {
        return captureCropY2Percent;
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

    private static int requirePercent(int value, String fieldName) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException(fieldName + " must be between 0 and 100");
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
