package printerhub.runtime;

import org.junit.jupiter.api.Test;
import printerhub.PrinterPort;
import printerhub.SerialIOMode;

import static org.junit.jupiter.api.Assertions.*;

class PrinterRegistryTest {

    @Test
    void registerStoresNodeAndFindByIdReturnsIt() {
        PrinterRegistry registry = new PrinterRegistry();
        PrinterRuntimeNode node = createNode("printer-1");

        registry.register(node);

        assertTrue(registry.findById("printer-1").isPresent());
        assertSame(node, registry.findById("printer-1").orElseThrow());
        assertFalse(registry.isEmpty());
        assertEquals(1, registry.all().size());
    }

    @Test
    void findByIdTrimsInput() {
        PrinterRegistry registry = new PrinterRegistry();
        PrinterRuntimeNode node = createNode("printer-1");

        registry.register(node);

        assertTrue(registry.findById("  printer-1  ").isPresent());
        assertSame(node, registry.findById("  printer-1  ").orElseThrow());
    }

    @Test
    void findByIdReturnsEmptyForNullOrBlankId() {
        PrinterRegistry registry = new PrinterRegistry();

        assertTrue(registry.findById(null).isEmpty());
        assertTrue(registry.findById("").isEmpty());
        assertTrue(registry.findById("   ").isEmpty());
    }

    @Test
    void removeReturnsRemovedNodeAndClosesIt() {
        PrinterRegistry registry = new PrinterRegistry();
        TestPrinterPort port = new TestPrinterPort();
        PrinterRuntimeNode node = createNode("printer-1", port);

        registry.register(node);

        PrinterRuntimeNode removed = registry.remove("printer-1");

        assertSame(node, removed);
        assertEquals(1, port.disconnectCalls);
        assertTrue(registry.findById("printer-1").isEmpty());
        assertTrue(registry.isEmpty());
    }

    @Test
    void removeTrimsPrinterId() {
        PrinterRegistry registry = new PrinterRegistry();
        TestPrinterPort port = new TestPrinterPort();
        PrinterRuntimeNode node = createNode("printer-1", port);

        registry.register(node);

        PrinterRuntimeNode removed = registry.remove("  printer-1  ");

        assertSame(node, removed);
        assertEquals(1, port.disconnectCalls);
        assertTrue(registry.findById("printer-1").isEmpty());
    }

    @Test
    void removeReturnsNullWhenPrinterDoesNotExist() {
        PrinterRegistry registry = new PrinterRegistry();

        PrinterRuntimeNode removed = registry.remove("missing-printer");

        assertNull(removed);
    }

    @Test
    void removeFailsForNullOrBlankPrinterId() {
        PrinterRegistry registry = new PrinterRegistry();

        IllegalArgumentException ex1 = assertThrows(
                IllegalArgumentException.class,
                () -> registry.remove(null));
        assertEquals("printerId must not be blank", ex1.getMessage());

        IllegalArgumentException ex2 = assertThrows(
                IllegalArgumentException.class,
                () -> registry.remove(""));
        assertEquals("printerId must not be blank", ex2.getMessage());

        IllegalArgumentException ex3 = assertThrows(
                IllegalArgumentException.class,
                () -> registry.remove("   "));
        assertEquals("printerId must not be blank", ex3.getMessage());
    }

    @Test
    void registerFailsForNullNode() {
        PrinterRegistry registry = new PrinterRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(null));

        assertEquals("node must not be null", exception.getMessage());
    }

    @Test
    void closeClosesAllRegisteredPrinters() {
        PrinterRegistry registry = new PrinterRegistry();

        TestPrinterPort port1 = new TestPrinterPort();
        TestPrinterPort port2 = new TestPrinterPort();

        registry.register(createNode("printer-1", port1));
        registry.register(createNode("printer-2", port2));

        registry.close();

        assertEquals(1, port1.disconnectCalls);
        assertEquals(1, port2.disconnectCalls);
    }

    @Test
    void initializeDoesNotFail() {
        PrinterRegistry registry = new PrinterRegistry();

        assertDoesNotThrow(registry::initialize);
    }

    private PrinterRuntimeNode createNode(String id) {
        return createNode(id, new TestPrinterPort());
    }

    private PrinterRuntimeNode createNode(String id, TestPrinterPort port) {
        return new PrinterRuntimeNode(
                id,
                "Display " + id,
                "PORT_" + id,
                "sim",
                port,
                true);
    }

    private static final class TestPrinterPort implements PrinterPort {
        private int disconnectCalls;

        @Override
        public void connect() {
            // not needed in this test
        }

        @Override
        public String sendCommand(String command) {
            return "ok";
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
            // not needed in this test
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
            // not needed in this test
        }

        @Override
        public void disconnect() {
            disconnectCalls++;
        }
    }
}
