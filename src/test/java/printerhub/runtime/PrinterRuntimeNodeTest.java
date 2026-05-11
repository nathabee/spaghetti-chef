package printerhub.runtime;

import org.junit.jupiter.api.Test;
import printerhub.PrinterPort;
import printerhub.SerialIOMode;

import static org.junit.jupiter.api.Assertions.*;

class PrinterRuntimeNodeTest {

    @Test
    void initialExecutionStateIsIdle() {
        PrinterRuntimeNode node = createNode();

        assertFalse(node.executionInProgress());
        assertNull(node.activeJobId());
    }

    @Test
    void beginJobExecutionMarksNodeBusy() {
        PrinterRuntimeNode node = createNode();

        node.beginJobExecution("job-1");

        assertTrue(node.executionInProgress());
        assertEquals("job-1", node.activeJobId());
    }

    @Test
    void secondBeginWhileBusyThrows() {
        PrinterRuntimeNode node = createNode();
        node.beginJobExecution("job-1");

        assertThrows(IllegalStateException.class, () -> node.beginJobExecution("job-2"));
    }

    @Test
    void endJobExecutionClearsBusyState() {
        PrinterRuntimeNode node = createNode();
        node.beginJobExecution("job-1");

        node.endJobExecution();

        assertFalse(node.executionInProgress());
        assertNull(node.activeJobId());
    }

    private PrinterRuntimeNode createNode() {
        return new PrinterRuntimeNode(
                "printer-1",
                "Printer 1",
                "SIM_PORT",
                "sim",
                new NoOpPrinterPort(),
                true);
    }

    private static final class NoOpPrinterPort implements PrinterPort {
        @Override
        public void connect() {
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
        }

        @Override
        public void disconnect() {
        }
    }
}
