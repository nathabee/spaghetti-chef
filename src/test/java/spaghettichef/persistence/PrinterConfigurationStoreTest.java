package spaghettichef.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spaghettichef.runtime.PrinterRuntimeNode;
import spaghettichef.runtime.PrinterRuntimeNodeFactory;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PrinterConfigurationStoreTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearDatabaseProperty() {
        System.clearProperty("spaghettichef.databaseFile");
    }

    @Test
    void saveInsertsPrinter() {
        useDatabase("config-insert.db");

        PrinterConfigurationStore store = new PrinterConfigurationStore();
        PrinterRuntimeNode node = PrinterRuntimeNodeFactory.create(
                "printer-1",
                "Printer 1",
                "SIM_PORT",
                "sim",
                true
        );

        store.save(node);

        List<PrinterRuntimeNode> printers = store.findAll();
        assertEquals(1, printers.size());
        assertEquals("printer-1", printers.get(0).id());
        assertEquals("Printer 1", printers.get(0).displayName());
        assertEquals("SIM_PORT", printers.get(0).portName());
        assertEquals("sim", printers.get(0).mode());
        assertTrue(printers.get(0).enabled());
    }

    @Test
    void savePreservesStableLinuxSerialPath() {
        useDatabase("config-stable-serial-path.db");

        String stablePath = "/dev/serial/by-id/usb-1a86_USB_Serial-if00-port0";
        PrinterConfigurationStore store = new PrinterConfigurationStore();
        PrinterRuntimeNode node = PrinterRuntimeNodeFactory.create(
                "printer-1",
                "Printer 1",
                stablePath,
                "real",
                true
        );

        store.save(node);

        List<PrinterRuntimeNode> printers = store.findAll();
        assertEquals(1, printers.size());
        assertEquals(stablePath, printers.get(0).portName());
        assertEquals("real", printers.get(0).mode());
    }

    @Test
    void saveUpdatesExistingPrinterOnConflict() {
        useDatabase("config-update.db");

        PrinterConfigurationStore store = new PrinterConfigurationStore();

        store.save(PrinterRuntimeNodeFactory.create(
                "printer-1",
                "Printer 1",
                "SIM_PORT",
                "sim",
                true
        ));

        store.save(PrinterRuntimeNodeFactory.create(
                "printer-1",
                "Updated Printer",
                "SIM_PORT_2",
                "sim-error",
                false
        ));

        List<PrinterRuntimeNode> printers = store.findAll();
        assertEquals(1, printers.size());
        assertEquals("printer-1", printers.get(0).id());
        assertEquals("Updated Printer", printers.get(0).displayName());
        assertEquals("SIM_PORT_2", printers.get(0).portName());
        assertEquals("sim-error", printers.get(0).mode());
        assertFalse(printers.get(0).enabled());
    }

    @Test
    void findAllReturnsAllPrintersOrderedById() {
        useDatabase("config-findall.db");

        PrinterConfigurationStore store = new PrinterConfigurationStore();

        store.save(PrinterRuntimeNodeFactory.create(
                "printer-b",
                "Printer B",
                "SIM_B",
                "sim",
                true
        ));
        store.save(PrinterRuntimeNodeFactory.create(
                "printer-a",
                "Printer A",
                "SIM_A",
                "sim",
                false
        ));

        List<PrinterRuntimeNode> printers = store.findAll();

        assertEquals(2, printers.size());
        assertEquals("printer-a", printers.get(0).id());
        assertEquals("printer-b", printers.get(1).id());
    }

    @Test
    void hasAnyPrinterReflectsDatabaseContent() {
        useDatabase("config-any.db");

        PrinterConfigurationStore store = new PrinterConfigurationStore();

        assertFalse(store.hasAnyPrinter());

        store.save(PrinterRuntimeNodeFactory.create(
                "printer-1",
                "Printer 1",
                "SIM_PORT",
                "sim",
                true
        ));

        assertTrue(store.hasAnyPrinter());
    }

    @Test
    void deleteRemovesPrinter() {
        useDatabase("config-delete.db");

        PrinterConfigurationStore store = new PrinterConfigurationStore();

        store.save(PrinterRuntimeNodeFactory.create(
                "printer-1",
                "Printer 1",
                "SIM_PORT",
                "sim",
                true
        ));

        store.delete("printer-1");

        assertTrue(store.findAll().isEmpty());
        assertFalse(store.hasAnyPrinter());
    }

    @Test
    void enableSetsEnabledFlagToTrue() {
        useDatabase("config-enable.db");

        PrinterConfigurationStore store = new PrinterConfigurationStore();

        store.save(PrinterRuntimeNodeFactory.create(
                "printer-1",
                "Printer 1",
                "SIM_PORT",
                "sim",
                false
        ));

        store.enable("printer-1");

        List<PrinterRuntimeNode> printers = store.findAll();
        assertEquals(1, printers.size());
        assertTrue(printers.get(0).enabled());
    }

    @Test
    void disableSetsEnabledFlagToFalse() {
        useDatabase("config-disable.db");

        PrinterConfigurationStore store = new PrinterConfigurationStore();

        store.save(PrinterRuntimeNodeFactory.create(
                "printer-1",
                "Printer 1",
                "SIM_PORT",
                "sim",
                true
        ));

        store.disable("printer-1");

        List<PrinterRuntimeNode> printers = store.findAll();
        assertEquals(1, printers.size());
        assertFalse(printers.get(0).enabled());
    }

    @Test
    void saveFailsForNullNode() {
        PrinterConfigurationStore store = new PrinterConfigurationStore();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> store.save(null)
        );

        assertEquals("node must not be null", exception.getMessage());
    }

    @Test
    void deleteFailsForBlankPrinterId() {
        PrinterConfigurationStore store = new PrinterConfigurationStore();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> store.delete("   ")
        );

        assertEquals("printerId must not be blank", exception.getMessage());
    }

    @Test
    void enableFailsForBlankPrinterId() {
        PrinterConfigurationStore store = new PrinterConfigurationStore();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> store.enable("   ")
        );

        assertEquals("printerId must not be blank", exception.getMessage());
    }

    @Test
    void disableFailsForBlankPrinterId() {
        PrinterConfigurationStore store = new PrinterConfigurationStore();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> store.disable("   ")
        );

        assertEquals("printerId must not be blank", exception.getMessage());
    }

    @Test
    void saveWrapsDatabaseFailure() {
        System.setProperty("spaghettichef.databaseFile", tempDir.resolve("not-a-db-dir").toString());
        assertDoesNotThrow(() -> java.nio.file.Files.createDirectories(tempDir.resolve("not-a-db-dir")));

        PrinterConfigurationStore store = new PrinterConfigurationStore();
        PrinterRuntimeNode node = PrinterRuntimeNodeFactory.create(
                "printer-1",
                "Printer 1",
                "SIM_PORT",
                "sim",
                true
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> store.save(node)
        );

        assertEquals("Failed to save printer configuration", exception.getMessage());
    }

    private void useDatabase(String fileName) {
        Path dbFile = tempDir.resolve(fileName);
        System.setProperty("spaghettichef.databaseFile", dbFile.toString());
        new DatabaseInitializer().initialize();
    }
}
