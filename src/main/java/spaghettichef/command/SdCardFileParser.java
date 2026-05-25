package spaghettichef.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SdCardFileParser {

    public List<SdCardFile> parse(String response) {
        if (response == null || response.isBlank()) {
            return List.of();
        }

        List<SdCardFile> files = new ArrayList<>();
        boolean insideList = false;

        for (String rawLine : response.split("\\R")) {
            String line = rawLine.trim();

            if (line.isBlank()) {
                continue;
            }

            String normalized = line.toLowerCase(Locale.ROOT);

            if (normalized.equals("begin file list")) {
                insideList = true;
                continue;
            }

            if (normalized.equals("end file list")) {
                break;
            }

            if (normalized.equals("ok") || normalized.startsWith("echo:")) {
                continue;
            }

            if (!insideList && normalized.contains("file list")) {
                continue;
            }

            if (!insideList) {
                continue;
            }

            files.add(parseFileLine(line));
        }

        return List.copyOf(files);
    }

    private SdCardFile parseFileLine(String line) {
        String[] parts = line.split("\\s+");
        String filename = parts.length == 0 ? line : parts[0];
        Long sizeBytes = null;

        if (parts.length > 1) {
            try {
                sizeBytes = Long.parseLong(parts[1]);
            } catch (NumberFormatException ignored) {
                sizeBytes = null;
            }
        }

        return new SdCardFile(filename, sizeBytes, line);
    }
}
