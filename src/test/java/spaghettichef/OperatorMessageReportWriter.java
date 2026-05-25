package spaghettichef;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class OperatorMessageReportWriter {

    private static final Path REPORT_PATH = Path.of("target", "operator-message-report.md");
    private static boolean initialized = false;

    private OperatorMessageReportWriter() {
    }

    public static synchronized void appendScenario(
            String scenarioName,
            String summary,
            String observedMessages,
            String apiEvidence,
            String persistenceEvidence
    ) throws IOException {
        initializeIfNeeded();

        StringBuilder block = new StringBuilder();
        block.append("## Scenario: ").append(normalizeInline(scenarioName)).append(System.lineSeparator()).append(System.lineSeparator());

        block.append("### Summary").append(System.lineSeparator()).append(System.lineSeparator());
        block.append(indentBlock(normalizeBlock(summary))).append(System.lineSeparator()).append(System.lineSeparator());

        block.append("### Observed operator-facing messages").append(System.lineSeparator()).append(System.lineSeparator());
        block.append(indentBlock(normalizeBlock(observedMessages))).append(System.lineSeparator()).append(System.lineSeparator());

        block.append("### API evidence").append(System.lineSeparator()).append(System.lineSeparator());
        block.append(indentBlock(normalizeBlock(apiEvidence))).append(System.lineSeparator()).append(System.lineSeparator());

        block.append("### Persistence evidence").append(System.lineSeparator()).append(System.lineSeparator());
        block.append(indentBlock(normalizeBlock(persistenceEvidence))).append(System.lineSeparator()).append(System.lineSeparator());

        Files.writeString(
                REPORT_PATH,
                block.toString(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    public static synchronized void appendSimpleScenario(
            String scenarioName,
            String details
    ) throws IOException {
        initializeIfNeeded();

        StringBuilder block = new StringBuilder();
        block.append("## Scenario: ").append(normalizeInline(scenarioName)).append(System.lineSeparator()).append(System.lineSeparator());
        block.append(indentBlock(normalizeBlock(details))).append(System.lineSeparator()).append(System.lineSeparator());

        Files.writeString(
                REPORT_PATH,
                block.toString(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    private static void initializeIfNeeded() throws IOException {
        if (initialized) {
            return;
        }

        Files.createDirectories(REPORT_PATH.getParent());

        Files.writeString(
                REPORT_PATH,
                """
                # Operator message report

                This report is generated during automated verification.

                It summarizes operator-facing runtime and API evidence that is relevant
                for CI/CD review of monitoring, lifecycle handling, persistence, and
                robustness scenarios.

                """,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );

        initialized = true;
    }

    private static String normalizeInline(String text) {
        if (text == null || text.isBlank()) {
            return "unnamed scenario";
        }

        return text.trim();
    }

    private static String normalizeBlock(String text) {
        if (text == null || text.isBlank()) {
            return "empty";
        }

        return text.stripTrailing();
    }

    private static String indentBlock(String text) {
        String[] lines = text.split("\\R", -1);
        StringBuilder out = new StringBuilder();

        for (String line : lines) {
            out.append("    ").append(line).append(System.lineSeparator());
        }

        return out.toString().stripTrailing();
    }
}