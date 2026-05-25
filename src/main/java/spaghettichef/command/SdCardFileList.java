package spaghettichef.command;

import java.util.List;

public record SdCardFileList(
        String printerId,
        List<SdCardFile> files,
        String rawResponse
) {
    public SdCardFileList {
        files = files == null ? List.of() : List.copyOf(files);
        rawResponse = rawResponse == null ? "" : rawResponse;
    }
}
