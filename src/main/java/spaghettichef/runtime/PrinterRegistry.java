package spaghettichef.runtime;

import spaghettichef.OperationMessages;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PrinterRegistry implements AutoCloseable {

    private final ConcurrentMap<String, PrinterRuntimeNode> printers = new ConcurrentHashMap<>();

    public void initialize() {
        // 0.1.x runtime bootstrap.
    }

    public void register(PrinterRuntimeNode node) {
        validateNode(node);
        printers.put(node.id(), node);
    }

    public Optional<PrinterRuntimeNode> findById(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(printers.get(printerId.trim()));
    }

    public Collection<PrinterRuntimeNode> all() {
        return printers.values();
    }

    public PrinterRuntimeNode remove(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_ID_MUST_NOT_BE_BLANK);
        }

        PrinterRuntimeNode removed = printers.remove(printerId.trim());

        if (removed != null) {
            removed.close();
        }

        return removed;
    }

    private void validateNode(PrinterRuntimeNode node) {
        if (node == null) {
            throw new IllegalArgumentException(OperationMessages.NODE_MUST_NOT_BE_NULL);
        }
        if (node.id() == null || node.id().isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_ID_MUST_NOT_BE_BLANK);
        }
    }

    public boolean isEmpty() {
        return printers.isEmpty();
    }

    @Override
    public void close() {
        for (PrinterRuntimeNode node : printers.values()) {
            node.close();
        }
    }
}