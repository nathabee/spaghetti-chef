package spaghettichef.camera;

import java.util.Locale;

public enum CameraSourceType {

    DISABLED("disabled"),
    SIMULATED("simulated"),
    SNAPSHOT_FOLDER("snapshot-folder"),
    FFMPEG("ffmpeg");

    private final String wireValue;

    CameraSourceType(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static CameraSourceType fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            return DISABLED;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (CameraSourceType type : values()) {
            if (type.wireValue.equals(normalized) || type.name().equalsIgnoreCase(normalized)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Unsupported camera source type: " + value);
    }
}
