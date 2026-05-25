package spaghettichef.runtime;

import org.junit.jupiter.api.Test;
import spaghettichef.SerialConnection;
import spaghettichef.serial.SimulatedPrinterPort;

import static org.junit.jupiter.api.Assertions.*;

class PrinterRuntimeNodeFactoryTest {

    @Test
    void createRealNodeBuildsSerialConnectionBackedNode() {
        PrinterRuntimeNode node = PrinterRuntimeNodeFactory.create(
                "printer-1",
                "Printer 1",
                "/dev/ttyUSB0",
                "real",
                true
        );

        assertEquals("printer-1", node.id());
        assertEquals("Printer 1", node.displayName());
        assertEquals("/dev/ttyUSB0", node.portName());
        assertEquals("real", node.mode());
        assertTrue(node.enabled());
        assertInstanceOf(SerialConnection.class, node.printerPort());
    }

    @Test
    void createRealNodeDoesNotOpenConnectionDuringFactoryCreation() {
        assertDoesNotThrow(() -> PrinterRuntimeNodeFactory.create(
                "printer-1",
                "Printer 1",
                "/dev/ttyUSB0",
                "real",
                true
        ));
    }

    @Test
    void createSimNodeBuildsSimulatedPrinterPortBackedNode() {
        PrinterRuntimeNode node = PrinterRuntimeNodeFactory.create(
                "printer-1",
                "Printer 1",
                "SIM_PORT",
                "sim",
                true
        );

        assertEquals("printer-1", node.id());
        assertEquals("Printer 1", node.displayName());
        assertEquals("SIM_PORT", node.portName());
        assertEquals("sim", node.mode());
        assertTrue(node.enabled());
        assertInstanceOf(SimulatedPrinterPort.class, node.printerPort());
    }

    @Test
    void createSupportsAllSimulationModes() {
        assertInstanceOf(
                SimulatedPrinterPort.class,
                PrinterRuntimeNodeFactory.create("p1", "P1", "SIM1", "simulated", true).printerPort()
        );
        assertInstanceOf(
                SimulatedPrinterPort.class,
                PrinterRuntimeNodeFactory.create("p2", "P2", "SIM2", "sim-disconnected", true).printerPort()
        );
        assertInstanceOf(
                SimulatedPrinterPort.class,
                PrinterRuntimeNodeFactory.create("p3", "P3", "SIM3", "sim-timeout", true).printerPort()
        );
        assertInstanceOf(
                SimulatedPrinterPort.class,
                PrinterRuntimeNodeFactory.create("p4", "P4", "SIM4", "sim-error", true).printerPort()
        );
    }

    @Test
    void createNormalizesModeForPortCreationButKeepsTrimmedOriginalMode() {
        PrinterRuntimeNode node = PrinterRuntimeNodeFactory.create(
                "printer-1",
                "Printer 1",
                "SIM_PORT",
                "  SIM-ERROR  ",
                true
        );

        assertEquals("SIM-ERROR", node.mode());
        assertInstanceOf(SimulatedPrinterPort.class, node.printerPort());
    }

    @Test
    void createTrimsRequiredFields() {
        PrinterRuntimeNode node = PrinterRuntimeNodeFactory.create(
                "  printer-1  ",
                "  Printer 1  ",
                "  SIM_PORT  ",
                "  sim  ",
                false
        );

        assertEquals("printer-1", node.id());
        assertEquals("Printer 1", node.displayName());
        assertEquals("SIM_PORT", node.portName());
        assertEquals("sim", node.mode());
        assertFalse(node.enabled());
    }

    @Test
    void createPreservesEnabledFlagFalse() {
        PrinterRuntimeNode node = PrinterRuntimeNodeFactory.create(
                "printer-1",
                "Printer 1",
                "SIM_PORT",
                "sim",
                false
        );

        assertFalse(node.enabled());
    }

    @Test
    void createFailsForInvalidMode() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PrinterRuntimeNodeFactory.create(
                        "printer-1",
                        "Printer 1",
                        "SIM_PORT",
                        "bluetooth",
                        true
                )
        );

        assertEquals(
                "mode must be one of: real, sim, simulated, sim-disconnected, sim-timeout, sim-error",
                exception.getMessage()
        );
    }

    @Test
    void createFailsForBlankId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PrinterRuntimeNodeFactory.create(
                        "   ",
                        "Printer 1",
                        "SIM_PORT",
                        "sim",
                        true
                )
        );

        assertEquals("id must not be blank", exception.getMessage());
    }

    @Test
    void createFailsForBlankDisplayName() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PrinterRuntimeNodeFactory.create(
                        "printer-1",
                        "   ",
                        "SIM_PORT",
                        "sim",
                        true
                )
        );

        assertEquals("displayName must not be blank", exception.getMessage());
    }

    @Test
    void createFailsForBlankPortName() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PrinterRuntimeNodeFactory.create(
                        "printer-1",
                        "Printer 1",
                        "   ",
                        "sim",
                        true
                )
        );

        assertEquals("portName must not be blank", exception.getMessage());
    }

    @Test
    void createFailsForBlankMode() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PrinterRuntimeNodeFactory.create(
                        "printer-1",
                        "Printer 1",
                        "SIM_PORT",
                        "   ",
                        true
                )
        );

        assertEquals("mode must not be blank", exception.getMessage());
    }

    @Test
    void createFailsForNullId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PrinterRuntimeNodeFactory.create(
                        null,
                        "Printer 1",
                        "SIM_PORT",
                        "sim",
                        true
                )
        );

        assertEquals("id must not be blank", exception.getMessage());
    }

    @Test
    void createFailsForNullDisplayName() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PrinterRuntimeNodeFactory.create(
                        "printer-1",
                        null,
                        "SIM_PORT",
                        "sim",
                        true
                )
        );

        assertEquals("displayName must not be blank", exception.getMessage());
    }

    @Test
    void createFailsForNullPortName() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PrinterRuntimeNodeFactory.create(
                        "printer-1",
                        "Printer 1",
                        null,
                        "sim",
                        true
                )
        );

        assertEquals("portName must not be blank", exception.getMessage());
    }

    @Test
    void createFailsForNullMode() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> PrinterRuntimeNodeFactory.create(
                        "printer-1",
                        "Printer 1",
                        "SIM_PORT",
                        null,
                        true
                )
        );

        assertEquals("mode must not be blank", exception.getMessage());
    }
}