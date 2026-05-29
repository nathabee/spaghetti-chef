package spaghettichef.camera.analysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ExternalCliAnalyzerProcess {

    private static final Pattern STRING_FIELD = Pattern.compile(
            "\"%s\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern NUMBER_FIELD = Pattern.compile(
            "\"%s\"\\s*:\\s*(-?(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:[eE][+-]?\\d+)?)");
    private static final Pattern BOOLEAN_FIELD = Pattern.compile(
            "\"%s\"\\s*:\\s*(true|false)");
    private static final Pattern REASON_CODES = Pattern.compile(
            "\"reasonCodes\"\\s*:\\s*\\[(.*?)]",
            Pattern.DOTALL);

    public ExternalCliAnalyzerResponse analyze(ExternalCliAnalyzerRequest request) {
        List<String> command = commandFor(request);
        Process process = start(command);
        CompletableFuture<String> stdout = readAsync(process.getInputStream());
        CompletableFuture<String> stderr = readAsync(process.getErrorStream());

        boolean completed;
        try {
            completed = process.waitFor(request.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new ExternalCliAnalyzerException("External analyzer process was interrupted", exception);
        }

        if (!completed) {
            process.destroyForcibly();
            throw new ExternalCliAnalyzerException(
                    "External analyzer process timed out after " + request.timeout(),
                    ExternalCliAnalyzerExitCode.UNKNOWN,
                    joinOutput(stdout, Duration.ofSeconds(1)),
                    joinOutput(stderr, Duration.ofSeconds(1)));
        }

        String stdoutText = joinOutput(stdout, request.timeout());
        String stderrText = joinOutput(stderr, request.timeout());
        ExternalCliAnalyzerExitCode exitCode = ExternalCliAnalyzerExitCode.fromCode(process.exitValue());

        if (process.exitValue() != 0) {
            throw new ExternalCliAnalyzerException(
                    "External analyzer exited with code " + process.exitValue() + " (" + exitCode.label() + ")",
                    exitCode,
                    stdoutText,
                    stderrText);
        }

        try {
            return parseResponse(stdoutText, stderrText, exitCode);
        } catch (RuntimeException exception) {
            throw new ExternalCliAnalyzerException(
                    "External analyzer returned invalid JSON: " + exception.getMessage(),
                    exitCode,
                    stdoutText,
                    stderrText);
        }
    }

    public List<String> commandFor(ExternalCliAnalyzerRequest request) {
        List<String> command = new ArrayList<>();
        command.add(request.executablePath().toString());
        command.add("--from-snapshot");
        command.add(request.fromSnapshotPath().toString());
        command.add("--to-snapshot");
        command.add(request.toSnapshotPath().toString());
        request.deltaFramePath().ifPresent(path -> {
            command.add("--delta-frame");
            command.add(path.toString());
        });
        command.add("--method");
        command.add(request.method());
        command.add("--threshold");
        command.add(Double.toString(request.threshold()));
        return List.copyOf(command);
    }

    private Process start(List<String> command) {
        try {
            return new ProcessBuilder(command).start();
        } catch (IOException exception) {
            throw new ExternalCliAnalyzerException("Failed to start External analyzer process", exception);
        }
    }

    private static CompletableFuture<String> readAsync(java.io.InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try (stream) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to read analyzer process output", exception);
            }
        });
    }

    private static String joinOutput(CompletableFuture<String> output, Duration timeout) {
        try {
            return output.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ExternalCliAnalyzerException("Interrupted while reading analyzer process output", exception);
        } catch (ExecutionException exception) {
            throw new ExternalCliAnalyzerException("Failed to read analyzer process output", exception);
        } catch (TimeoutException exception) {
            return "";
        }
    }

    private static ExternalCliAnalyzerResponse parseResponse(
            String stdout,
            String stderr,
            ExternalCliAnalyzerExitCode exitCode) {
        String json = stdout == null ? "" : stdout.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new IllegalArgumentException("expected one JSON object on stdout");
        }

        return new ExternalCliAnalyzerResponse(
                stringField(json, "engineName"),
                stringField(json, "engineVersion"),
                stringField(json, "algorithmVariant"),
                numberField(json, "confidence"),
                booleanField(json, "suspected"),
                reasonCodes(json),
                stringField(json, "message"),
                numberField(json, "changedPixelRatio"),
                numberField(json, "averagePixelDelta"),
                exitCode,
                stdout,
                stderr);
    }

    private static String stringField(String json, String fieldName) {
        Matcher matcher = Pattern.compile(STRING_FIELD.pattern().formatted(Pattern.quote(fieldName))).matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException("missing string field " + fieldName);
        }
        return unescapeJsonString(matcher.group(1));
    }

    private static double numberField(String json, String fieldName) {
        Matcher matcher = Pattern.compile(NUMBER_FIELD.pattern().formatted(Pattern.quote(fieldName))).matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException("missing number field " + fieldName);
        }
        return Double.parseDouble(matcher.group(1));
    }

    private static boolean booleanField(String json, String fieldName) {
        Matcher matcher = Pattern.compile(BOOLEAN_FIELD.pattern().formatted(Pattern.quote(fieldName))).matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException("missing boolean field " + fieldName);
        }
        return Boolean.parseBoolean(matcher.group(1));
    }

    private static List<String> reasonCodes(String json) {
        Matcher matcher = REASON_CODES.matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException("missing reasonCodes");
        }

        String content = matcher.group(1).trim();
        if (content.isEmpty()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        Matcher itemMatcher = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"").matcher(content);
        while (itemMatcher.find()) {
            values.add(unescapeJsonString(itemMatcher.group(1)));
        }
        return List.copyOf(values);
    }

    private static String unescapeJsonString(String value) {
        StringBuilder builder = new StringBuilder();
        boolean escaped = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (escaped) {
                builder.append(switch (character) {
                    case '"', '\\', '/' -> character;
                    case 'b' -> '\b';
                    case 'f' -> '\f';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> character;
                });
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else {
                builder.append(character);
            }
        }
        return builder.toString();
    }
}
