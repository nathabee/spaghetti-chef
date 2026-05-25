package spaghettichef.monitoring;

import org.junit.jupiter.api.Test;
import spaghettichef.PrinterPort;
import spaghettichef.PrinterSnapshot;
import spaghettichef.PrinterState;
import spaghettichef.SerialIOMode;
import spaghettichef.runtime.PrinterRegistry;
import spaghettichef.runtime.PrinterRuntimeNode;
import spaghettichef.runtime.PrinterRuntimeStateCache;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PrinterMonitoringSchedulerTest {

        @Test
        void constructorFailsForNullRegistry() {
                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> new PrinterMonitoringScheduler(
                                                null,
                                                new PrinterRuntimeStateCache()));

                assertEquals("printerRegistry must not be null", exception.getMessage());
        }

        @Test
        void constructorFailsForNullStateCache() {
                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> new PrinterMonitoringScheduler(
                                                new PrinterRegistry(),
                                                null));

                assertEquals("stateCache must not be null", exception.getMessage());
        }

        @Test
        void startInitializesDisabledPrintersAsDisconnected() {
                PrinterRegistry registry = new PrinterRegistry();
                PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();

                PrinterRuntimeNode disabledNode = new PrinterRuntimeNode(
                                "printer-disabled",
                                "Disabled Printer",
                                "SIM_DISABLED",
                                "sim",
                                new TestPrinterPort(),
                                false);

                registry.register(disabledNode);

                PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(
                                registry,
                                stateCache);

                scheduler.start();
                scheduler.stop();

                PrinterSnapshot snapshot = stateCache.findByPrinterId("printer-disabled").orElseThrow();
                assertEquals(PrinterState.DISCONNECTED, snapshot.state());
                assertEquals("Printer node is disabled.", snapshot.errorMessage());
        }

        @Test
        void startMonitoringInitializesEnabledPrinterState() throws InterruptedException {
                PrinterRegistry printerRegistry = new PrinterRegistry();
                PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();

                PrinterRuntimeNode node = new PrinterRuntimeNode(
                                "printer-1",
                                "Printer 1",
                                "SIM_PORT",
                                "sim",
                                new spaghettichef.serial.SimulatedPrinterPort("SIM_PORT"),
                                true);

                printerRegistry.register(node);

                PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(
                                printerRegistry,
                                stateCache);

                try {
                        scheduler.startMonitoring(node);

                        PrinterSnapshot snapshot = waitForState(
                                        stateCache,
                                        "printer-1",
                                        PrinterState.IDLE,
                                        1000);

                        assertNotNull(snapshot);
                        assertEquals(PrinterState.IDLE, snapshot.state());
                } finally {
                        scheduler.stop();
                }
        }

        private PrinterSnapshot waitForState(
                        PrinterRuntimeStateCache stateCache,
                        String printerId,
                        PrinterState expectedState,
                        long timeoutMs) throws InterruptedException {
                long deadline = System.currentTimeMillis() + timeoutMs;

                while (System.currentTimeMillis() < deadline) {
                        PrinterSnapshot snapshot = stateCache.findByPrinterId(printerId).orElse(null);

                        if (snapshot != null && snapshot.state() == expectedState) {
                                return snapshot;
                        }

                        Thread.sleep(10);
                }

                return stateCache.findByPrinterId(printerId).orElse(null);
        }

        @Test
        void startMonitoringWithDisabledNodeCreatesDisconnectedStateAndDoesNotFail() {
                PrinterRegistry registry = new PrinterRegistry();
                PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();

                PrinterRuntimeNode disabledNode = new PrinterRuntimeNode(
                                "printer-disabled",
                                "Disabled Printer",
                                "SIM_DISABLED",
                                "sim",
                                new TestPrinterPort(),
                                false);

                PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(
                                registry,
                                stateCache);

                assertDoesNotThrow(() -> scheduler.startMonitoring(disabledNode));

                PrinterSnapshot snapshot = stateCache.findByPrinterId("printer-disabled").orElseThrow();
                assertEquals(PrinterState.DISCONNECTED, snapshot.state());
                assertEquals("Printer node is disabled.", snapshot.errorMessage());

                scheduler.stop();
        }

        @Test
        void startMonitoringFailsForNullNode() {
                PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(
                                new PrinterRegistry(),
                                new PrinterRuntimeStateCache());

                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> scheduler.startMonitoring(null));

                assertEquals("node must not be null", exception.getMessage());

                scheduler.stop();
        }

        @Test
        void restartMonitoringFailsForNullNode() {
                PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(
                                new PrinterRegistry(),
                                new PrinterRuntimeStateCache());

                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> scheduler.restartMonitoring(null));

                assertEquals("node must not be null", exception.getMessage());

                scheduler.stop();
        }

        @Test
        void stopMonitoringIgnoresNullOrBlankPrinterId() {
                PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(
                                new PrinterRegistry(),
                                new PrinterRuntimeStateCache());

                assertDoesNotThrow(() -> scheduler.stopMonitoring(null));
                assertDoesNotThrow(() -> scheduler.stopMonitoring(""));
                assertDoesNotThrow(() -> scheduler.stopMonitoring("   "));

                scheduler.stop();
        }

        @Test
        void stopCanBeCalledWithoutStart() {
                PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(
                                new PrinterRegistry(),
                                new PrinterRuntimeStateCache());

                assertDoesNotThrow(scheduler::stop);
        }

        @Test
        void startCanBeCalledTwiceWithoutFailure() {
                PrinterRegistry registry = new PrinterRegistry();
                PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();

                registry.register(new PrinterRuntimeNode(
                                "printer-1",
                                "Printer 1",
                                "SIM_PORT",
                                "sim",
                                new TestPrinterPort(),
                                false));

                PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(
                                registry,
                                stateCache);

                assertDoesNotThrow(scheduler::start);
                assertDoesNotThrow(scheduler::start);

                scheduler.stop();
        }

        @Test
        void stopAfterStartDoesNotFail() {
                PrinterRegistry registry = new PrinterRegistry();
                PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();

                PrinterRuntimeNode node = new PrinterRuntimeNode(
                                "printer-1",
                                "Printer 1",
                                "SIM_PORT",
                                "sim",
                                new TestPrinterPort(),
                                false);

                registry.register(node);

                PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(
                                registry,
                                stateCache);

                scheduler.start();

                assertDoesNotThrow(scheduler::stop);
        }

        @Test
        void restartMonitoringAfterStopRecreatesMonitoringWithoutFailure() {
                PrinterRegistry registry = new PrinterRegistry();
                PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();

                PrinterRuntimeNode node = new PrinterRuntimeNode(
                                "printer-1",
                                "Printer 1",
                                "SIM_PORT",
                                "sim",
                                new TestPrinterPort(),
                                true);

                PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(
                                registry,
                                stateCache);

                scheduler.startMonitoring(node);
                scheduler.stop();

                assertDoesNotThrow(() -> scheduler.startMonitoring(node));
                assertTrue(stateCache.findByPrinterId("printer-1").isPresent());

                scheduler.stop();
        }

        @Test
        void restartMonitoringReinitializesPrinterState() {
                PrinterRegistry registry = new PrinterRegistry();
                PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();

                PrinterRuntimeNode node = new PrinterRuntimeNode(
                                "printer-1",
                                "Printer 1",
                                "SIM_PORT",
                                "sim",
                                new TestPrinterPort(),
                                true);

                stateCache.update(
                                "printer-1",
                                PrinterSnapshot.fromResponse(
                                                PrinterState.ERROR,
                                                99.0,
                                                50.0,
                                                "old response",
                                                Instant.parse("2026-04-29T10:00:00Z")));

                PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(
                                registry,
                                stateCache);

                scheduler.restartMonitoring(node);

                PrinterSnapshot snapshot = stateCache.findByPrinterId("printer-1").orElseThrow();
                assertTrue(
                                snapshot.state() == PrinterState.DISCONNECTED
                                                || snapshot.state() == PrinterState.CONNECTING
                                                || snapshot.state() == PrinterState.IDLE);
                assertNull(snapshot.hotendTemperature());
                assertNull(snapshot.bedTemperature());
                assertNotEquals("old response", snapshot.lastResponse());

                scheduler.stop();
        }

        @Test
        void multiPrinterStartHandlesEnabledAndDisabledTogether() {
                PrinterRegistry registry = new PrinterRegistry();
                PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();

                registry.register(new PrinterRuntimeNode(
                                "printer-1",
                                "Printer 1",
                                "SIM_1",
                                "sim",
                                new TestPrinterPort(),
                                true));
                registry.register(new PrinterRuntimeNode(
                                "printer-2",
                                "Printer 2",
                                "SIM_2",
                                "sim",
                                new TestPrinterPort(),
                                false));

                PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(
                                registry,
                                stateCache);

                scheduler.start();

                assertTrue(stateCache.findByPrinterId("printer-1").isPresent());
                assertTrue(stateCache.findByPrinterId("printer-2").isPresent());
                assertEquals(
                                PrinterState.DISCONNECTED,
                                stateCache.findByPrinterId("printer-2").orElseThrow().state());

                scheduler.stop();
        }

        private static final class TestPrinterPort implements PrinterPort {
                @Override
                public void connect() {
                        // no-op
                }

                @Override
                public String sendRawLine(String line) {
                        return sendRawLine(line, SerialIOMode.COMMAND_RESPONSE);
                }

                @Override
                public String sendRawLine(String line, SerialIOMode mode) {
                        return "ok";
                }

                @Override
                public void writeRawLine(String line, SerialIOMode mode) {
                        // no-op
                }

                @Override
                public String readRawResponse(SerialIOMode mode) {
                        return "ok";
                }

                @Override
                public java.util.List<String> sendRawLinesPipelined(java.util.List<String> lines, SerialIOMode mode) {
                        if (lines == null || lines.isEmpty()) {
                                return java.util.List.of();
                        }

                        java.util.List<String> responses = new java.util.ArrayList<>(lines.size());
                        for (int i = 0; i < lines.size(); i++) {
                                responses.add("ok");
                        }
                        return responses;
                }

                @Override
                public void discardPendingInput(int quietPeriodMs, int maxDrainMs) {
                        // no-op
                }

                @Override
                public String sendCommand(String command) {
                        return "ok";
                }

                @Override
                public void disconnect() {
                        // no-op
                }
        }
}
