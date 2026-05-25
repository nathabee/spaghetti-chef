package spaghettichef.runtime;

import spaghettichef.OperationMessages;
import spaghettichef.PrinterSnapshot;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class PrinterRuntimeStateCache {

    private final Map<String, PrinterSnapshot> snapshotsByPrinterId = new ConcurrentHashMap<>();
    private final Clock clock;

    public PrinterRuntimeStateCache() {
        this(Clock.systemUTC());
    }

    public PrinterRuntimeStateCache(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException(OperationMessages.CLOCK_MUST_NOT_BE_NULL);
        }

        this.clock = clock;
    }

    public void initializePrinter(String printerId) {
        update(printerId, PrinterSnapshot.disconnected(Instant.now(clock)));
    }

    public void update(String printerId, PrinterSnapshot snapshot) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_ID_MUST_NOT_BE_BLANK);
        }
        if (snapshot == null) {
            throw new IllegalArgumentException(OperationMessages.SNAPSHOT_MUST_NOT_BE_NULL);
        }

        snapshotsByPrinterId.put(printerId.trim(), snapshot);
    }

    public Optional<PrinterSnapshot> findByPrinterId(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(snapshotsByPrinterId.get(printerId.trim()));
    }

    public PrinterSnapshot currentOrDisconnected(String printerId) {
        return findByPrinterId(printerId)
                .orElseGet(() -> PrinterSnapshot.disconnected(Instant.now(clock)));
    }

    public Collection<PrinterSnapshot> findAll() {
        return snapshotsByPrinterId.values();
    }

    public void clear() {
        snapshotsByPrinterId.clear();
    }

    public void remove(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            return;
        }

        snapshotsByPrinterId.remove(printerId.trim());
    }
}