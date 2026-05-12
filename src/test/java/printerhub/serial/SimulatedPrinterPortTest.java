package printerhub.serial;

import org.junit.jupiter.api.Test;
import printerhub.config.PrinterProtocolDefaults;

import static org.junit.jupiter.api.Assertions.*;

class SimulatedPrinterPortTest {

    @Test
    void constructorFailsForBlankPortName() {
        IllegalArgumentException ex1 = assertThrows(
                IllegalArgumentException.class,
                () -> new SimulatedPrinterPort(null));
        assertEquals("portName must not be blank", ex1.getMessage());

        IllegalArgumentException ex2 = assertThrows(
                IllegalArgumentException.class,
                () -> new SimulatedPrinterPort(""));
        assertEquals("portName must not be blank", ex2.getMessage());

        IllegalArgumentException ex3 = assertThrows(
                IllegalArgumentException.class,
                () -> new SimulatedPrinterPort("   "));
        assertEquals("portName must not be blank", ex3.getMessage());
    }

    @Test
    void constructorDefaultsBlankModeToSim() {
        SimulatedPrinterPort port = new SimulatedPrinterPort("SIM_PORT", "   ");

        assertDoesNotThrow(port::connect);
        assertEquals(
                PrinterProtocolDefaults.SIMULATED_RESPONSE_M105,
                port.sendCommand("M105"));
    }

    @Test
    void connectSucceedsInNormalSimMode() {
        SimulatedPrinterPort port = new SimulatedPrinterPort("SIM_PORT", "sim");

        assertDoesNotThrow(port::connect);
    }

    @Test
    void connectFailsInSimDisconnectedMode() {
        SimulatedPrinterPort port = new SimulatedPrinterPort(
                "SIM_PORT",
                PrinterProtocolDefaults.SIM_DISCONNECTED_MODE);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                port::connect);

        assertEquals("Simulated printer is disconnected: SIM_PORT", exception.getMessage());
    }

    @Test
    void sendCommandFailsWhenNotConnected() {
        SimulatedPrinterPort port = new SimulatedPrinterPort("SIM_PORT", "sim");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> port.sendCommand("M105"));

        assertEquals("Simulated printer is not connected: SIM_PORT", exception.getMessage());
    }

    @Test
    void sendCommandFailsForBlankCommand() {
        SimulatedPrinterPort port = new SimulatedPrinterPort("SIM_PORT", "sim");
        port.connect();

        IllegalArgumentException ex1 = assertThrows(
                IllegalArgumentException.class,
                () -> port.sendCommand(null));
        assertEquals("command must not be blank", ex1.getMessage());

        IllegalArgumentException ex2 = assertThrows(
                IllegalArgumentException.class,
                () -> port.sendCommand(""));
        assertEquals("command must not be blank", ex2.getMessage());

        IllegalArgumentException ex3 = assertThrows(
                IllegalArgumentException.class,
                () -> port.sendCommand("   "));
        assertEquals("command must not be blank", ex3.getMessage());
    }

    @Test
    void simModeReturnsExpectedM105Response() {
        SimulatedPrinterPort port = new SimulatedPrinterPort("SIM_PORT", "sim");
        port.connect();

        String response = port.sendCommand("M105");

        assertEquals(PrinterProtocolDefaults.SIMULATED_RESPONSE_M105, response);
    }

    @Test
    void simModeReturnsExpectedM114Response() {
        SimulatedPrinterPort port = new SimulatedPrinterPort("SIM_PORT", "sim");
        port.connect();

        String response = port.sendCommand("M114");

        assertEquals(PrinterProtocolDefaults.SIMULATED_RESPONSE_M114, response);
    }

    @Test
    void simModeReturnsExpectedM115Response() {
        SimulatedPrinterPort port = new SimulatedPrinterPort("SIM_PORT", "sim");
        port.connect();

        String response = port.sendCommand("M115");

        assertEquals(PrinterProtocolDefaults.SIMULATED_RESPONSE_M115, response);
    }

    @Test
    void simModeReturnsDefaultOkForUnknownCommand() {
        SimulatedPrinterPort port = new SimulatedPrinterPort("SIM_PORT", "sim");
        port.connect();

        String response = port.sendCommand("G28");

        assertEquals(PrinterProtocolDefaults.SIMULATED_RESPONSE_DEFAULT_OK, response);
    }

    @Test
    void commandMatchingIsCaseInsensitiveAndTrimmed() {
        SimulatedPrinterPort port = new SimulatedPrinterPort("SIM_PORT", "sim");
        port.connect();

        String response = port.sendCommand("  m105  ");

        assertEquals(PrinterProtocolDefaults.SIMULATED_RESPONSE_M105, response);
    }

    @Test
    void simErrorModeReturnsSimulatedFailureResponse() {
        SimulatedPrinterPort port = new SimulatedPrinterPort(
                "SIM_PORT",
                PrinterProtocolDefaults.SIM_ERROR_MODE);
        port.connect();

        String response = port.sendCommand("M105");

        assertEquals("Error: Simulated printer failure", response);
    }

    @Test
    void simTimeoutModeReturnsBlankResponse() {
        SimulatedPrinterPort port = new SimulatedPrinterPort(
                "SIM_PORT",
                PrinterProtocolDefaults.SIM_TIMEOUT_MODE);
        port.connect();

        String response = port.sendCommand("M105");

        assertEquals("", response);
    }

    @Test
    void disconnectResetsConnectionState() {
        SimulatedPrinterPort port = new SimulatedPrinterPort("SIM_PORT", "sim");
        port.connect();
        port.disconnect();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> port.sendCommand("M105"));

        assertEquals("Simulated printer is not connected: SIM_PORT", exception.getMessage());
    }

    @Test
    void simulatedAliasModeAlsoWorks() {
        SimulatedPrinterPort port = new SimulatedPrinterPort("SIM_PORT", "simulated");
        port.connect();

        String response = port.sendCommand("M105");

        assertEquals(PrinterProtocolDefaults.SIMULATED_RESPONSE_M105, response);
    }

    @Test
    void rawSdUploadSessionMakesUploadedFileVisibleInM20Listing() {
        SimulatedPrinterPort port = new SimulatedPrinterPort("SIM_PORT");

        port.connect();

        assertEquals("ok", port.sendRawLine("N0 M110 N0*125"));
        String openResponse = port.sendRawLine("N1 M28 TESTHEXA.GCO*0");
        assertTrue(openResponse.contains("Writing to file: TESTHEXA.GCO"));

        assertEquals("ok", port.sendRawLine("N2 M104 S0*0"));
        assertEquals("ok", port.sendRawLine("N3 M105*0"));
        assertEquals("ok", port.sendRawLine("N4 M29*0"));

        String listing = port.sendRawLine("N5 M20*0");
        assertTrue(listing.contains("Begin file list"));
        assertTrue(listing.contains("TESTHEXA.GCO"));
        assertTrue(listing.contains("End file list"));
    }
}