package spaghettichef.camera.analysis;

import java.nio.file.Path;
import java.time.Duration;

public record CalculationEngineConfiguration(
        CalculationEngineName engineName,
        Path executablePath,
        String cliMethod,
        Duration timeout) {

    public CalculationEngineConfiguration {
        engineName = engineName == null ? CalculationEngineName.JAVA_BASIC_DELTA : engineName;
        cliMethod = cliMethod == null || cliMethod.isBlank() ? null : cliMethod.trim();
        timeout = timeout == null || timeout.isZero() || timeout.isNegative()
                ? Duration.ofSeconds(10)
                : timeout;
    }
}
