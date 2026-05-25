package spaghettichef.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SdCardFileParserTest {

    private final SdCardFileParser parser = new SdCardFileParser();

    @Test
    void parseReadsMarlinFileListWithSizes() {
        List<SdCardFile> files = parser.parse("""
                Begin file list
                CUBE.GCO 12345
                BENCHY.GCO 67890
                End file list
                ok
                """);

        assertEquals(2, files.size());
        assertEquals("CUBE.GCO", files.get(0).filename());
        assertEquals(12345L, files.get(0).sizeBytes());
        assertEquals("CUBE.GCO 12345", files.get(0).rawLine());
        assertEquals("BENCHY.GCO", files.get(1).filename());
        assertEquals(67890L, files.get(1).sizeBytes());
    }

    @Test
    void parseKeepsRawLineWhenSizeIsNotAvailable() {
        List<SdCardFile> files = parser.parse("""
                Begin file list
                LONGNAME.GCO
                End file list
                ok
                """);

        assertEquals(1, files.size());
        assertEquals("LONGNAME.GCO", files.get(0).filename());
        assertNull(files.get(0).sizeBytes());
        assertEquals("LONGNAME.GCO", files.get(0).rawLine());
    }

    @Test
    void parseIgnoresNoiseOutsideActualFileList() {
        List<SdCardFile> files = parser.parse("""
                Resend: 3
                Error:Line Number is not Last Line Number+1, Last Line: 2
                ok
                Begin file list
                TEST4.GCO 9
                End file list
                ok
                """);

        assertEquals(1, files.size());
        assertEquals("TEST4.GCO", files.get(0).filename());
        assertEquals(9L, files.get(0).sizeBytes());
    }
}
