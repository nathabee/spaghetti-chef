package spaghettichef.command;

import spaghettichef.OperationMessages;

public record SdCardFile(
        String filename,
        Long sizeBytes,
        String rawLine
) {
    public SdCardFile {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("filename"));
        }

        filename = filename.trim();
        rawLine = rawLine == null ? "" : rawLine.trim();
    }
}
