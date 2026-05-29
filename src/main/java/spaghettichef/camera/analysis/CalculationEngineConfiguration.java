package spaghettichef.camera.analysis;

import java.nio.file.Path;
import java.time.Duration;

public record CalculationEngineConfiguration(
        CalculationEngineAdapterType adapterType,
        Path executablePath,
        String cliMethod,
        Duration timeout) {

    public CalculationEngineConfiguration {
        adapterType = adapterType == null ? CalculationEngineAdapterType.JAVA_BASIC_DELTA : adapterType;
        cliMethod = cliMethod == null || cliMethod.isBlank() ? null : cliMethod.trim();
        timeout = timeout == null || timeout.isZero() || timeout.isNegative()
                ? Duration.ofSeconds(10)
                : timeout;
    }
}
