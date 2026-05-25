package spaghettichef.monitoring;

import spaghettichef.OperationMessages;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MonitoringEventPolicy {

    private final Clock clock;
    private final Duration dedupWindow;
    private final ConcurrentMap<String, PersistedEventSignature> lastPersistedEvents =
            new ConcurrentHashMap<>();

    public MonitoringEventPolicy(Clock clock, Duration dedupWindow) {
        if (clock == null) {
            throw new IllegalArgumentException(OperationMessages.CLOCK_MUST_NOT_BE_NULL);
        }
        if (dedupWindow == null) {
            throw new IllegalArgumentException(OperationMessages.DEDUP_WINDOW_MUST_NOT_BE_NULL);
        }
        if (dedupWindow.isNegative()) {
            throw new IllegalArgumentException(OperationMessages.DEDUP_WINDOW_MUST_NOT_BE_NEGATIVE);
        }

        this.clock = clock;
        this.dedupWindow = dedupWindow;
    }

    public boolean shouldPersistEvent(String printerId, String eventType, String eventMessage) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_ID_MUST_NOT_BE_BLANK);
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.EVENT_TYPE_MUST_NOT_BE_BLANK);
        }
        if (eventMessage == null || eventMessage.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.EVENT_MESSAGE_MUST_NOT_BE_BLANK);
        }

        PersistedEventSignature previous = lastPersistedEvents.get(printerId.trim());

        if (previous == null) {
            return true;
        }

        if (!previous.eventType.equals(eventType)) {
            return true;
        }

        if (!previous.eventMessage.equals(eventMessage)) {
            return true;
        }

        long ageSeconds = Duration.between(previous.persistedAt, Instant.now(clock)).toSeconds();
        return ageSeconds >= dedupWindow.toSeconds();
    }

    public void rememberPersistedEvent(String printerId, String eventType, String eventMessage) {
        if (printerId == null || printerId.isBlank()) {
            return;
        }

        lastPersistedEvents.put(
                printerId.trim(),
                new PersistedEventSignature(
                        eventType,
                        eventMessage,
                        Instant.now(clock)
                )
        );
    }

    public void clearPrinter(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            return;
        }

        lastPersistedEvents.remove(printerId.trim());
    }

    private static final class PersistedEventSignature {
        private final String eventType;
        private final String eventMessage;
        private final Instant persistedAt;

        private PersistedEventSignature(String eventType, String eventMessage, Instant persistedAt) {
            this.eventType = Objects.requireNonNull(eventType);
            this.eventMessage = Objects.requireNonNull(eventMessage);
            this.persistedAt = Objects.requireNonNull(persistedAt);
        }
    }
}