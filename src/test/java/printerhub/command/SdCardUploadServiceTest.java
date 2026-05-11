package printerhub.command;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import printerhub.PrinterPort;
import printerhub.SerialIOMode;
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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

            assertTrue(exception.getMessage().contains("outside the recoverable resend window"));
            assertTrue(exception.getMessage().contains("line 1"));
            assertTrue(printerPort.operations().contains("raw:N1 M28 TEST8.GCO*115"));
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
                    () -> false,
                    () -> 5,
                    () -> 2,
                    () -> 10_000,
                    () -> 10,
                    () -> 0);
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
                    () -> true,
                    () -> 5,
                    () -> 2,
                    () -> 10_000,
                    () -> 10,
                    () -> 0);
            enabledTraceService.uploadToPrinterSd("printer-1", printFile.id(), "TEST4.GCO");
            assertTrue(output.toString().contains("SD upload wire"));
        } finally {
            System.setOut(originalOut);
            monitoringScheduler.stop();
        }
    }

    @Test
    void uploadRecoversActiveBatchResendLineByLineAndSucceeds() throws Exception {
        initializeDatabase("sd-upload-active-batch-recovery.db");

        Path hostFile = tempDir.resolve("upload-active-batch-recovery.gcode");
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
                printerEventStore,
                () -> false,
                () -> 2,
                () -> 2,
                () -> 10_000,
                () -> 10,
                () -> 0);

        PrintFile printFile = printFileService.registerHostFile(hostFile.toString());
        ActiveBatchRecoveryPrinterPort printerPort = new ActiveBatchRecoveryPrinterPort();
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
                    "TEST9.GCO");

            assertTrue(result.success());
            assertEquals(2L, result.uploadedLineCount());
            assertEquals(1L, result.rejectedLineCount());

            assertEquals(2, countRawOperationsStartingWith(printerPort.operations(), "raw:N2 "));
            assertEquals(2, countRawOperationsStartingWith(printerPort.operations(), "raw:N3 "));
            assertTrue(containsRawOperationStartingWith(printerPort.operations(), "raw:N4 M29"));
            assertTrue(containsRawOperationStartingWith(printerPort.operations(), "raw:N5 M20"));

            SdCardUploadService.UploadProgress progress = uploadService.uploadProgress("printer-1").orElseThrow();
            assertFalse(progress.active());
            assertEquals("success", progress.state());
            assertEquals(1L, progress.rejectedLineCount());
        } finally {
            monitoringScheduler.stop();
        }
    }

    @Test
    void uploadRecoversFromRecentSentLineBufferAndSucceeds() throws Exception {
        initializeDatabase("sd-upload-recent-buffer-recovery.db");

        Path hostFile = tempDir.resolve("upload-recent-buffer-recovery.gcode");
        Files.writeString(hostFile, """
                M104 S0
                M105
                M106 S1
                M107
                M114
                G92 E0
                """);

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
                printerEventStore,
                () -> false,
                () -> 2,
                () -> 2,
                () -> 10_000,
                () -> 10,
                () -> 0);

        PrintFile printFile = printFileService.registerHostFile(hostFile.toString());
        RecentBufferRecoveryPrinterPort printerPort = new RecentBufferRecoveryPrinterPort();
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
                    "TESTA.GCO");

            assertTrue(result.success());
            assertEquals(6L, result.uploadedLineCount());
            assertEquals(1L, result.rejectedLineCount());

            assertEquals(2, countRawOperationsStartingWith(printerPort.operations(), "raw:N4 "));
            assertEquals(2, countRawOperationsStartingWith(printerPort.operations(), "raw:N5 "));
            assertEquals(2, countRawOperationsStartingWith(printerPort.operations(), "raw:N6 "));
            assertEquals(2, countRawOperationsStartingWith(printerPort.operations(), "raw:N7 "));
            assertTrue(containsRawOperationStartingWith(printerPort.operations(), "raw:N8 M29"));
            assertTrue(containsRawOperationStartingWith(printerPort.operations(), "raw:N9 M20"));

            assertTrue(printerEventStore.findRecentByPrinterId("printer-1", 20)
                    .stream()
                    .anyMatch(event -> "SD_CARD_UPLOAD_RECOVERY_STARTED".equals(event.eventType())));

            assertTrue(printerEventStore.findRecentByPrinterId("printer-1", 20)
                    .stream()
                    .anyMatch(event -> "SD_CARD_UPLOAD_RECOVERY_COMPLETED".equals(event.eventType())));
        } finally {
            monitoringScheduler.stop();
        }
    }

    @Test
    void uploadFailsWhenResendFallsOutsideRecentSentLineBuffer() throws Exception {
        initializeDatabase("sd-upload-recent-buffer-miss.db");

        Path hostFile = tempDir.resolve("upload-recent-buffer-miss.gcode");
        Files.writeString(hostFile, """
                M104 S0
                M105
                M106 S1
                M107
                M114
                G92 E0
                M82
                M83
                """);

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
                printerEventStore,
                () -> false,
                () -> 2,
                () -> 2,
                () -> 10_000,
                () -> 10,
                () -> 0);

        PrintFile printFile = printFileService.registerHostFile(hostFile.toString());
        RecentBufferMissPrinterPort printerPort = new RecentBufferMissPrinterPort();
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
                    () -> uploadService.uploadToPrinterSd("printer-1", printFile.id(), "TESTB.GCO"));

            assertTrue(exception.getMessage().contains("outside the recoverable resend window"));
            assertTrue(exception.getMessage().contains("line 2"));

            SdCardUploadService.UploadProgress progress = uploadService.uploadProgress("printer-1").orElseThrow();
            assertFalse(progress.active());
            assertEquals("error", progress.state());
            assertEquals(1L, progress.rejectedLineCount());
        } finally {
            monitoringScheduler.stop();
        }
    }

    private void initializeDatabase(String fileName) {
        String databaseFile = tempDir.resolve(fileName).toString();
        System.setProperty(RuntimeDefaults.DATABASE_FILE_PROPERTY, databaseFile);
        new DatabaseInitializer().initialize();
    }

    private int countRawOperationsStartingWith(List<String> operations, String prefix) {
        int count = 0;

        for (String operation : operations) {
            if (operation.startsWith(prefix)) {
                count++;
            }
        }

        return count;
    }

    private boolean containsRawOperationStartingWith(List<String> operations, String prefix) {
        for (String operation : operations) {
            if (operation.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }

    private abstract static class BaseTestPrinterPort implements PrinterPort {

        protected final List<String> pendingResponses = new ArrayList<>();
        protected boolean connected;

        @Override
        public void connect() {
            connected = true;
        }

        @Override
        public String sendRawLine(String line) {
            return sendRawLine(line, SerialIOMode.COMMAND_RESPONSE);
        }

        @Override
        public String sendRawLine(String line, SerialIOMode mode) {
            ensureConnected();

            if (line == null) {
                throw new IllegalArgumentException("line must not be null");
            }
            if (mode == null) {
                throw new IllegalArgumentException("mode must not be null");
            }

            return handleRawLine(line);
        }

        @Override
        public void writeRawLine(String line, SerialIOMode mode) {
            ensureConnected();

            if (line == null) {
                throw new IllegalArgumentException("line must not be null");
            }
            if (mode == null) {
                throw new IllegalArgumentException("mode must not be null");
            }

            pendingResponses.add(handleRawLine(line));
        }

        @Override
        public String readRawResponse(SerialIOMode mode) {
            ensureConnected();

            if (mode == null) {
                throw new IllegalArgumentException("mode must not be null");
            }
            if (pendingResponses.isEmpty()) {
                throw new IllegalStateException("No pending response");
            }

            return pendingResponses.remove(0);
        }

        @Override
        public List<String> sendRawLinesPipelined(List<String> lines, SerialIOMode mode) {
            ensureConnected();

            if (lines == null || lines.isEmpty()) {
                return List.of();
            }
            if (mode == null) {
                throw new IllegalArgumentException("mode must not be null");
            }

            List<String> responses = new ArrayList<>(lines.size());
            for (String line : lines) {
                responses.add(sendRawLine(line, mode));
            }
            return responses;
        }

        @Override
        public void discardPendingInput(int quietPeriodMs, int maxDrainMs) {
            ensureConnected();
            pendingResponses.clear();
        }

        @Override
        public void disconnect() {
            connected = false;
            pendingResponses.clear();
        }

        protected void ensureConnected() {
            if (!connected) {
                throw new IllegalStateException("not connected");
            }
        }

        protected abstract String handleRawLine(String line);
    }

    private static final class RecordingUploadPrinterPort extends BaseTestPrinterPort {

        private final List<String> operations = new ArrayList<>();

        @Override
        public synchronized void connect() {
            super.connect();
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
        protected synchronized String handleRawLine(String line) {
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
            super.disconnect();
            operations.add("disconnect");
        }

        private synchronized List<String> operations() {
            return List.copyOf(operations);
        }
    }

    private static final class ResendThenOkPrinterPort extends BaseTestPrinterPort {

        private final AtomicInteger payloadAttempts = new AtomicInteger();

        @Override
        public String sendCommand(String command) {
            ensureConnected();
            return "ok";
        }

        @Override
        protected String handleRawLine(String line) {
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

        private int payloadAttempts() {
            return payloadAttempts.get();
        }
    }

    private static final class ResendMismatchPrinterPort extends BaseTestPrinterPort {

        private final List<String> operations = new ArrayList<>();

        @Override
        public String sendCommand(String command) {
            ensureConnected();
            return "ok";
        }

        @Override
        protected String handleRawLine(String line) {
            operations.add("raw:" + line);

            return switch (line) {
                case "N0 M110 N0*125", "N1 M28 TEST8.GCO*115", "N2 M104 S0*103", "N2 M29*26" -> "ok";
                case "N3 M105*36" -> """
                        Error:No Checksum with line number, Last Line: 20313
                        Resend: 1
                        ok
                        """;
                default -> throw new IllegalStateException("Unexpected raw line: " + line);
            };
        }

        private List<String> operations() {
            return List.copyOf(operations);
        }
    }

    private static final class MissingFileListingPrinterPort extends BaseTestPrinterPort {

        @Override
        public String sendCommand(String command) {
            ensureConnected();
            return "ok";
        }

        @Override
        protected String handleRawLine(String line) {
            return switch (line) {
                case "N0 M110 N0*125", "N2 M104 S0*103", "N3 M29*27" -> "ok";
                case "N1 M28 TEST6.GCO*125" -> """
                        echo:Now fresh file: TEST6.GCO
                        Writing to file: TEST6.GCO
                        ok
                        """;
                case "N4 M20*21" -> """
                        Begin file list
                        TEST4.GCO 9
                        End file list
                        ok
                        """;
                default -> throw new IllegalStateException("Unexpected raw line: " + line);
            };
        }
    }

    private static final class CommentAwarePrinterPort extends BaseTestPrinterPort {

        private final List<String> operations = new ArrayList<>();

        @Override
        public String sendCommand(String command) {
            ensureConnected();
            return "ok";
        }

        @Override
        protected String handleRawLine(String line) {
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

        private List<String> operations() {
            return List.copyOf(operations);
        }
    }

    private static final class ActiveBatchRecoveryPrinterPort extends BaseTestPrinterPort {

        private final List<String> operations = new ArrayList<>();
        private final AtomicInteger line2Attempts = new AtomicInteger();

        @Override
        public String sendCommand(String command) {
            ensureConnected();
            return "ok";
        }

        @Override
        protected String handleRawLine(String line) {
            operations.add("raw:" + line);

            if (line.startsWith("N0 M110 N0*")) {
                return "ok";
            }
            if (line.startsWith("N1 M28 TEST9.GCO*")) {
                return """
                        echo:Now fresh file: TEST9.GCO
                        Writing to file: TEST9.GCO
                        ok
                        """;
            }
            if (line.startsWith("N2 ")) {
                if (line2Attempts.incrementAndGet() == 1) {
                    return """
                            Error:checksum mismatch
                            Resend: 2
                            ok
                            """;
                }
                return "ok";
            }
            if (line.startsWith("N3 ")) {
                return "ok";
            }
            if (line.startsWith("N4 M29*")) {
                return "ok";
            }
            if (line.startsWith("N5 M20*")) {
                return """
                        Begin file list
                        TEST9.GCO 14
                        End file list
                        ok
                        """;
            }

            throw new IllegalStateException("Unexpected raw line: " + line);
        }

        private List<String> operations() {
            return List.copyOf(operations);
        }
    }

    private static final class RecentBufferRecoveryPrinterPort extends BaseTestPrinterPort {

        private final List<String> operations = new ArrayList<>();
        private final AtomicInteger line6Attempts = new AtomicInteger();

        @Override
        public String sendCommand(String command) {
            ensureConnected();
            return "ok";
        }

        @Override
        protected String handleRawLine(String line) {
            operations.add("raw:" + line);

            if (line.startsWith("N0 M110 N0*")) {
                return "ok";
            }
            if (line.startsWith("N1 M28 TESTA.GCO*")) {
                return """
                        echo:Now fresh file: TESTA.GCO
                        Writing to file: TESTA.GCO
                        ok
                        """;
            }
            if (line.startsWith("N2 ") || line.startsWith("N3 ")
                    || line.startsWith("N4 ") || line.startsWith("N5 ")
                    || line.startsWith("N7 ")) {
                return "ok";
            }
            if (line.startsWith("N6 ")) {
                if (line6Attempts.incrementAndGet() == 1) {
                    return """
                            Error:Line Number is not Last Line Number+1, Last Line: 3
                            Resend: 4
                            ok
                            """;
                }
                return "ok";
            }
            if (line.startsWith("N8 M29*")) {
                return "ok";
            }
            if (line.startsWith("N9 M20*")) {
                return """
                        Begin file list
                        TESTA.GCO 36
                        End file list
                        ok
                        """;
            }

            throw new IllegalStateException("Unexpected raw line: " + line);
        }

        private List<String> operations() {
            return List.copyOf(operations);
        }
    }

    private static final class RecentBufferMissPrinterPort extends BaseTestPrinterPort {

        private final List<String> operations = new ArrayList<>();
        private final AtomicInteger line8Attempts = new AtomicInteger();

        @Override
        public String sendCommand(String command) {
            ensureConnected();
            return "ok";
        }

        @Override
        protected String handleRawLine(String line) {
            operations.add("raw:" + line);

            if (line.startsWith("N0 M110 N0*")) {
                return "ok";
            }
            if (line.startsWith("N1 M28 TESTB.GCO*")) {
                return """
                        echo:Now fresh file: TESTB.GCO
                        Writing to file: TESTB.GCO
                        ok
                        """;
            }
            if (line.startsWith("N2 ") || line.startsWith("N3 ")
                    || line.startsWith("N4 ") || line.startsWith("N5 ")
                    || line.startsWith("N6 ") || line.startsWith("N7 ")
                    || line.startsWith("N9 ")) {
                return "ok";
            }
            if (line.startsWith("N8 ")) {
                if (line8Attempts.incrementAndGet() == 1) {
                    return """
                            Error:Line Number is not Last Line Number+1, Last Line: 7
                            Resend: 2
                            ok
                            """;
                }
                return "ok";
            }
            if (line.startsWith("N2 M29*")) {
                return "ok";
            }

            throw new IllegalStateException("Unexpected raw line: " + line);
        }

        private List<String> operations() {
            return List.copyOf(operations);
        }
    }
}