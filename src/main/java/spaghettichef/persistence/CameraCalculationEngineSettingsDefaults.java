package spaghettichef.persistence;

import java.time.Instant;
import java.util.List;

import spaghettichef.config.RuntimeDefaults;

public final class CameraCalculationEngineSettingsDefaults {

    private CameraCalculationEngineSettingsDefaults() {
    }

    public static List<CameraCalculationEngineSettings> builtInDefaults(Instant now) {
        Instant timestamp = now == null ? Instant.now() : now;
        return List.of(
                new CameraCalculationEngineSettings(
                        "JAVA_BASIC_DELTA",
                        "Java basic delta",
                        true,
                        RuntimeDefaults.DEFAULT_CAMERA_CALCULATION_METHOD_NAME,
                        RuntimeDefaults.DEFAULT_CAMERA_CALCULATION_CONFIDENCE_THRESHOLD,
                        RuntimeDefaults.DEFAULT_CAMERA_CALCULATION_PARAMETER_JSON,
                        null,
                        null,
                        RuntimeDefaults.DEFAULT_CAMERA_RUST_CLI_TIMEOUT_MS,
                        10,
                        timestamp,
                        timestamp),
                new CameraCalculationEngineSettings(
                        "RUST_CLI_DELTA",
                        "Rust CLI delta",
                        true,
                        RuntimeDefaults.DEFAULT_CAMERA_CALCULATION_METHOD_NAME,
                        RuntimeDefaults.DEFAULT_CAMERA_CALCULATION_CONFIDENCE_THRESHOLD,
                        RuntimeDefaults.DEFAULT_CAMERA_CALCULATION_PARAMETER_JSON,
                        RuntimeDefaults.DEFAULT_CAMERA_RUST_CLI_METHOD,
                        null,
                        RuntimeDefaults.DEFAULT_CAMERA_RUST_CLI_TIMEOUT_MS,
                        20,
                        timestamp,
                        timestamp));
    }
}
