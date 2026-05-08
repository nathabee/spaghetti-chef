package printerhub.command;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import printerhub.PrinterPort;
import printerhub.config.RuntimeDefaults;
import printerhub.job.PrintFile;
import printerhub.job.PrintFileService;
import printerhub.job.PrinterActionGuard;
import printerhub.job.PrinterSdFile;
import printerhub.job.PrinterSdFileService;
import printerhub.monitoring.PrinterMonitoringScheduler;
import printerhub.persistence.DatabaseInitializer;
import printerhub.persistence.PrintFileStore;
import printerhub.persistence.PrinterEventStore;
import printerhub.persistence.PrinterSdFileStore;
import printerhub.runtime.PrinterRegistry;
import printerhub.runtime.PrinterRuntimeNode;
import printerhub.runtime.PrinterRuntimeStateCache;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SdCardUploadServiceTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        System.clearProperty(RuntimeDefaults.DATABASE_FILE_PROPERTY);
    }

    @Test
    void uploadUsesChecksummedSessionForCloseAndVerification() throws Exception {
        initializeDatabase("sd-upload-session.db");

        Path hostFile = tempDir.resolve("upload-test.gcode");
        Files.writeString(hostFile, "M104 S0\n");

        PrinterRegistry printerRegistry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler monitoringScheduler = new PrinterMonitoringScheduler(printerRegistry, stateCache);
        PrinterEventStore printerEventStore = new PrinterEventStore();
        PrintFileStore printFileStore = new PrintFileStore();
        PrintFileService printFileService = new PrintFileService(printFileStore);
        PrinterSdFileService printerSdFileService = new PrinterSdFileService(new PrinterSdFileStore(), printFileStore);
        SdCardUploadService uploadService = new SdCardUploadService(
                printerRegistry,
                monitoringScheduler,
                new PrinterActionGuard(),
                printFileService,
                new SdCardService(printerEventStore),
                printerSdFileService,
                printerEventStore);

        PrintFile printFile = printFileService.registerHostFile(hostFile.toString());
        RecordingUploadPrinterPort printerPort = new RecordingUploadPrinterPort();
        printerRegistry.register(new PrinterRuntimeNode(
                "printer-1",
                "Printer 1",
                "/dev/ttyUSB0",
                "real",
                printerPort,
                true));

        try {
            SdCardUploadService.UploadResult result = uploadService.uploadToPrinterSd(
                    "printer-1",
                    printFile.id(),
                    "TEST4.GCO");

            assertTrue(result.success());
            assertEquals("TEST4.GCO", result.linkedFirmwarePath());
            assertEquals(1L, result.uploadedLineCount());
            assertEquals(1L, result.totalLineCount());
            assertEquals(Files.size(hostFile), result.totalByteCount());

            SdCardUploadService.UploadProgress progress = uploadService.uploadProgress("printer-1").orElseThrow();
            assertFalse(progress.active());
            assertEquals("success", progress.state());
            assertEquals(1L, progress.uploadedLineCount());
            assertEquals(1L, progress.totalLineCount());
            assertEquals(Files.size(hostFile), progress.totalByteCount());

            Optional<PrinterSdFile> storedFile = printerSdFileService.findByPrinterIdAndFirmwarePath(
                    "printer-1",
                    "TEST4.GCO");
            assertTrue(storedFile.isPresent());
            assertEquals(Long.valueOf(9L), storedFile.orElseThrow().sizeBytes());
            assertEquals(printFile.id(), storedFile.orElseThrow().printFileId());
            assertNotNull(result.printerSdFileId());

            assertEquals(
                    List.of(
                            "connect",
                            "raw:N0 M110 N0*125",
                            "raw:N1 M28 TEST4.GCO*127",
                            "raw:N2 M104 S0*103",
                            "raw:N3 M29*27",
                            "raw:N4 M20*21"),
                    printerPort.operations().subList(0, 6));
            assertTrue(printerEventStore.findRecentByPrinterId("printer-1", 20)
                    .stream()
                    .anyMatch(event -> "SD_CARD_UPLOAD_PROGRESS".equals(event.eventType())));
        } finally {
            monitoringScheduler.stop();
        }
    }

    @Test
    void uploadRetriesWhenPrinterRequestsResendBeforeOk() throws Exception {
        initializeDatabase("sd-upload-resend.db");

        Path hostFile = tempDir.resolve("upload-resend.gcode");
        Files.writeString(hostFile, "M104 S0\n");

        PrinterRegistry printerRegistry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler monitoringScheduler = new PrinterMonitoringScheduler(printerRegistry, stateCache);
        PrinterEventStore printerEventStore = new PrinterEventStore();
        PrintFileStore printFileStore = new PrintFileStore();
        PrintFileService printFileService = new PrintFileService(printFileStore);
        PrinterSdFileService printerSdFileService = new PrinterSdFileService(new PrinterSdFileStore(), printFileStore);
        SdCardUploadService uploadService = new SdCardUploadService(
                printerRegistry,
                monitoringScheduler,
                new PrinterActionGuard(),
                printFileService,
                new SdCardService(printerEventStore),
                printerSdFileService,
                printerEventStore);

        PrintFile printFile = printFileService.registerHostFile(hostFile.toString());
        ResendThenOkPrinterPort printerPort = new ResendThenOkPrinterPort();
        printerRegistry.register(new PrinterRuntimeNode(
                "printer-1",
                "Printer 1",
                "/dev/ttyUSB0",
                "real",
                printerPort,
                true));

        try {
            SdCardUploadService.UploadResult result = uploadService.uploadToPrinterSd(
                    "printer-1",
                    printFile.id(),
                    "TEST5.GCO");

            assertTrue(result.success());
            assertEquals(1L, result.rejectedLineCount());
            assertEquals(2, printerPort.payloadAttempts());

            SdCardUploadService.UploadProgress progress = uploadService.uploadProgress("printer-1").orElseThrow();
            assertEquals(1L, progress.rejectedLineCount());
        } finally {
            monitoringScheduler.stop();
        }
    }

    @Test
    void uploadFailsWhenPrinterRequestsDifferentResendLineEvenWithOk() throws Exception {
        initializeDatabase("sd-upload-resend-mismatch.db");

        Path hostFile = tempDir.resolve("upload-resend-mismatch.gcode");
        Files.writeString(hostFile, "M104 S0\nM105\n");

        PrinterRegistry printerRegistry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler monitoringScheduler = new PrinterMonitoringScheduler(printerRegistry, stateCache);
        PrinterEventStore printerEventStore = new PrinterEventStore();
        PrintFileStore printFileStore = new PrintFileStore();
        PrintFileService printFileService = new PrintFileService(printFileStore);
        PrinterSdFileService printerSdFileService = new PrinterSdFileService(new PrinterSdFileStore(), printFileStore);
        SdCardUploadService uploadService = new SdCardUploadService(
                printerRegistry,
                monitoringScheduler,
                new PrinterActionGuard(),
                printFileService,
                new SdCardService(printerEventStore),
                printerSdFileService,
                printerEventStore);

        PrintFile printFile = printFileService.registerHostFile(hostFile.toString());
        ResendMismatchPrinterPort printerPort = new ResendMismatchPrinterPort();
        printerRegistry.register(new PrinterRuntimeNode(
                "printer-1",
                "Printer 1",
                "/dev/ttyUSB0",
                "real",
                printerPort,
                true));

        try {
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> uploadService.uploadToPrinterSd("printer-1", printFile.id(), "TEST8.GCO"));

            assertTrue(exception.getMessage().contains("Printer requested resend for line 2"));
            assertTrue(exception.getMessage().contains("uploading line 3"));
            assertTrue(printerPort.operations().contains("raw:N2 M29*26"));

            SdCardUploadService.UploadProgress progress = uploadService.uploadProgress("printer-1").orElseThrow();
            assertFalse(progress.active());
            assertEquals("error", progress.state());
        } finally {
            monitoringScheduler.stop();
        }
    }

    @Test
    void uploadFailsWhenPrinterDoesNotConfirmUploadedFileInSdListing() throws Exception {
        initializeDatabase("sd-upload-missing-confirmation.db");

        Path hostFile = tempDir.resolve("upload-missing.gcode");
        Files.writeString(hostFile, "M104 S0\n");

        PrinterRegistry printerRegistry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler monitoringScheduler = new PrinterMonitoringScheduler(printerRegistry, stateCache);
        PrinterEventStore printerEventStore = new PrinterEventStore();
        PrintFileStore printFileStore = new PrintFileStore();
        PrintFileService printFileService = new PrintFileService(printFileStore);
        PrinterSdFileService printerSdFileService = new PrinterSdFileService(new PrinterSdFileStore(), printFileStore);
        SdCardUploadService uploadService = new SdCardUploadService(
                printerRegistry,
                monitoringScheduler,
                new PrinterActionGuard(),
                printFileService,
                new SdCardService(printerEventStore),
                printerSdFileService,
                printerEventStore);

        PrintFile printFile = printFileService.registerHostFile(hostFile.toString());
        MissingFileListingPrinterPort printerPort = new MissingFileListingPrinterPort();
        printerRegistry.register(new PrinterRuntimeNode(
                "printer-1",
                "Printer 1",
                "/dev/ttyUSB0",
                "real",
                printerPort,
                true));

        try {
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> uploadService.uploadToPrinterSd("printer-1", printFile.id(), "TEST6.GCO"));

            assertTrue(exception.getMessage().contains("not confirmed by printer SD listing"));
            assertFalse(printerSdFileService.findByPrinterIdAndFirmwarePath("printer-1", "TEST6.GCO").isPresent());
        } finally {
            monitoringScheduler.stop();
        }
    }

    @Test
    void uploadStripsSemicolonCommentsAndSkipsBlankLines() throws Exception {
        initializeDatabase("sd-upload-comments.db");

        Path hostFile = tempDir.resolve("upload-comments.gcode");
        Files.writeString(hostFile, "M105 ; status check\n\n; upload test\nM104 S0\n");

        PrinterRegistry printerRegistry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler monitoringScheduler = new PrinterMonitoringScheduler(printerRegistry, stateCache);
        PrinterEventStore printerEventStore = new PrinterEventStore();
        PrintFileStore printFileStore = new PrintFileStore();
        PrintFileService printFileService = new PrintFileService(printFileStore);
        PrinterSdFileService printerSdFileService = new PrinterSdFileService(new PrinterSdFileStore(), printFileStore);
        SdCardUploadService uploadService = new SdCardUploadService(
                printerRegistry,
                monitoringScheduler,
                new PrinterActionGuard(),
                printFileService,
                new SdCardService(printerEventStore),
                printerSdFileService,
                printerEventStore);

        PrintFile printFile = printFileService.registerHostFile(hostFile.toString());
        CommentAwarePrinterPort printerPort = new CommentAwarePrinterPort();
        printerRegistry.register(new PrinterRuntimeNode(
                "printer-1",
                "Printer 1",
                "/dev/ttyUSB0",
                "real",
                printerPort,
                true));

        try {
            SdCardUploadService.UploadResult result = uploadService.uploadToPrinterSd(
                    "printer-1",
                    printFile.id(),
                    "TEST7.GCO");

            assertTrue(result.success());
            assertEquals(2L, result.uploadedLineCount());
            assertEquals(
                    List.of(
                            "raw:N0 M110 N0*125",
                            "raw:N1 M28 TEST7.GCO*124",
                            "raw:N2 M105*37",
                            "raw:N3 M104 S0*102",
                            "raw:N4 M29*28",
                            "raw:N5 M20*20"),
                    printerPort.operations());
        } finally {
            monitoringScheduler.stop();
        }
    }

    @Test
    void uploadWireTraceLoggingOnlyPrintsWhenEnabled() throws Exception {
        initializeDatabase("sd-upload-trace-flag.db");

        Path hostFile = tempDir.resolve("upload-trace.gcode");
        Files.writeString(hostFile, "M104 S0\n");

        PrinterRegistry printerRegistry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler monitoringScheduler = new PrinterMonitoringScheduler(printerRegistry, stateCache);
        PrinterEventStore printerEventStore = new PrinterEventStore();
        PrintFileStore printFileStore = new PrintFileStore();
        PrintFileService printFileService = new PrintFileService(printFileStore);
        PrinterSdFileService printerSdFileService = new PrinterSdFileService(new PrinterSdFileStore(), printFileStore);

        PrintFile printFile = printFileService.registerHostFile(hostFile.toString());
        RecordingUploadPrinterPort printerPort = new RecordingUploadPrinterPort();
        printerRegistry.register(new PrinterRuntimeNode(
                "printer-1",
                "Printer 1",
                "/dev/ttyUSB0",
                "real",
                printerPort,
                true));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output));

            SdCardUploadService disabledTraceService = new SdCardUploadService(
                    printerRegistry,
                    monitoringScheduler,
                    new PrinterActionGuard(),
                    printFileService,
                    new SdCardService(printerEventStore),
                    printerSdFileService,
                    printerEventStore,
                    () -> false);
            disabledTraceService.uploadToPrinterSd("printer-1", printFile.id(), "TEST4.GCO");
            assertFalse(output.toString().contains("SD upload wire"));

            output.reset();

            SdCardUploadService enabledTraceService = new SdCardUploadService(
                    printerRegistry,
                    monitoringScheduler,
                    new PrinterActionGuard(),
                    printFileService,
                    new SdCardService(printerEventStore),
                    printerSdFileService,
                    printerEventStore,
                    () -> true);
            enabledTraceService.uploadToPrinterSd("printer-1", printFile.id(), "TEST4.GCO");
            assertTrue(output.toString().contains("SD upload wire"));
        } finally {
            System.setOut(originalOut);
            monitoringScheduler.stop();
        }
    }

    private void initializeDatabase(String fileName) {
        String databaseFile = tempDir.resolve(fileName).toString();
        System.setProperty(RuntimeDefaults.DATABASE_FILE_PROPERTY, databaseFile);
        new DatabaseInitializer().initialize();
    }

    private static final class RecordingUploadPrinterPort implements PrinterPort {

        private final List<String> operations = new ArrayList<>();
        private boolean connected;

        @Override
        public synchronized void connect() {
            connected = true;
            operations.add("connect");
        }

        @Override
        public synchronized String sendCommand(String command) {
            ensureConnected();
            operations.add("command:" + command);

            return switch (command) {
                case "M105" -> "ok T:21.80 /0.00 B:21.52 /0.00 @:0 B@:0";
                default -> "ok";
            };
        }

        @Override
        public synchronized String sendRawLine(String line) {
            ensureConnected();
            operations.add("raw:" + line);

            return switch (line) {
                case "N0 M110 N0*125" -> "ok";
                case "N1 M28 TEST4.GCO*127" -> """
                        echo:Now fresh file: TEST4.GCO
                        Writing to file: TEST4.GCO
                        ok
                        """;
                case "N2 M104 S0*103", "N3 M29*27" -> "ok";
                case "N4 M20*21" -> """
                        Begin file list
                        TEST4.GCO 9
                        End file list
                        ok
                        """;
                default -> throw new IllegalStateException("Unexpected raw line: " + line);
            };
        }

        @Override
        public synchronized void disconnect() {
            connected = false;
            operations.add("disconnect");
        }

        private void ensureConnected() {
            if (!connected) {
                throw new IllegalStateException("not connected");
            }
        }

        private synchronized List<String> operations() {
            return List.copyOf(operations);
        }
    }

    private static final class ResendThenOkPrinterPort implements PrinterPort {

        private final AtomicInteger payloadAttempts = new AtomicInteger();
        private boolean connected;

        @Override
        public void connect() {
            connected = true;
        }

        @Override
        public String sendCommand(String command) {
            ensureConnected();

            return "ok";
        }

        @Override
        public String sendRawLine(String line) {
            ensureConnected();

            return switch (line) {
                case "N0 M110 N0*125", "N1 M28 TEST5.GCO*126" -> "ok";
                case "N2 M104 S0*103" -> {
                    if (payloadAttempts.incrementAndGet() == 1) {
                        yield "Resend: 2\nok";
                    }
                    yield "ok";
                }
                case "N3 M29*27" -> "ok";
                case "N4 M20*21" -> """
                        Begin file list
                        TEST5.GCO 9
                        End file list
                        ok
                        """;
                default -> throw new IllegalStateException("Unexpected raw line: " + line);
            };
        }

        @Override
        public void disconnect() {
            connected = false;
        }

        private void ensureConnected() {
            if (!connected) {
                throw new IllegalStateException("not connected");
            }
        }

        private int payloadAttempts() {
            return payloadAttempts.get();
        }
    }

    private static final class ResendMismatchPrinterPort implements PrinterPort {

        private final List<String> operations = new ArrayList<>();
        private boolean connected;

        @Override
        public void connect() {
            connected = true;
        }

        @Override
        public String sendCommand(String command) {
            ensureConnected();
            return "ok";
        }

        @Override
        public String sendRawLine(String line) {
            ensureConnected();
            operations.add("raw:" + line);

            return switch (line) {
                case "N0 M110 N0*125", "N1 M28 TEST8.GCO*115", "N2 M104 S0*103", "N2 M29*26" -> "ok";
                case "N3 M105*36" -> """
                        Error:No Checksum with line number, Last Line: 20313
                        Resend: 2
                        ok
                        """;
                default -> throw new IllegalStateException("Unexpected raw line: " + line);
            };
        }

        @Override
        public void disconnect() {
            connected = false;
        }

        private void ensureConnected() {
            if (!connected) {
                throw new IllegalStateException("not connected");
            }
        }

        private List<String> operations() {
            return List.copyOf(operations);
        }
    }

    private static final class MissingFileListingPrinterPort implements PrinterPort {

        private boolean connected;

        @Override
        public void connect() {
            connected = true;
        }

        @Override
        public String sendCommand(String command) {
            ensureConnected();
            return "ok";
        }

        @Override
        public String sendRawLine(String line) {
            ensureConnected();

            return switch (line) {
                case "N0 M110 N0*125", "N1 M28 TEST6.GCO*125", "N2 M104 S0*103", "N3 M29*27" -> "ok";
                case "N4 M20*21" -> """
                        Begin file list
                        TEST4.GCO 9
                        End file list
                        ok
                        """;
                default -> throw new IllegalStateException("Unexpected raw line: " + line);
            };
        }

        @Override
        public void disconnect() {
            connected = false;
        }

        private void ensureConnected() {
            if (!connected) {
                throw new IllegalStateException("not connected");
            }
        }
    }

    private static final class CommentAwarePrinterPort implements PrinterPort {

        private final List<String> operations = new ArrayList<>();
        private boolean connected;

        @Override
        public void connect() {
            connected = true;
        }

        @Override
        public String sendCommand(String command) {
            ensureConnected();
            return "ok";
        }

        @Override
        public String sendRawLine(String line) {
            ensureConnected();
            operations.add("raw:" + line);

            return switch (line) {
                case "N0 M110 N0*125", "N2 M105*37", "N3 M104 S0*102", "N4 M29*28" -> "ok";
                case "N1 M28 TEST7.GCO*124" -> """
                        echo:Now fresh file: TEST7.GCO
                        Writing to file: TEST7.GCO
                        ok
                        """;
                case "N5 M20*20" -> """
                        Begin file list
                        TEST7.GCO 14
                        End file list
                        ok
                        """;
                default -> throw new IllegalStateException("Unexpected raw line: " + line);
            };
        }

        @Override
        public void disconnect() {
            connected = false;
        }

        private void ensureConnected() {
            if (!connected) {
                throw new IllegalStateException("not connected");
            }
        }

        private List<String> operations() {
            return List.copyOf(operations);
        }
    }
}
