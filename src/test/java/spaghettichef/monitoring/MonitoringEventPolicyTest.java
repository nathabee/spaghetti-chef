package spaghettichef.monitoring;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class MonitoringEventPolicyTest {

    @Test
    void constructorFailsWhenClockIsNull() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new MonitoringEventPolicy(null, Duration.ofSeconds(60))
        );

        assertEquals("clock must not be null", exception.getMessage());
    }

    @Test
    void constructorFailsWhenDedupWindowIsNull() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new MonitoringEventPolicy(Clock.systemUTC(), null)
        );

        assertEquals("dedupWindow must not be null", exception.getMessage());
    }

    @Test
    void constructorFailsWhenDedupWindowIsNegative() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new MonitoringEventPolicy(Clock.systemUTC(), Duration.ofSeconds(-1))
        );

        assertEquals("dedupWindow must not be negative", exception.getMessage());
    }

    @Test
    void shouldPersistEventReturnsTrueWhenNoPreviousEventExists() {
        MonitoringEventPolicy policy = new MonitoringEventPolicy(
                Clock.fixed(Instant.parse("2026-04-29T10:00:00Z"), ZoneOffset.UTC),
                Duration.ofSeconds(60)
        );

        boolean result = policy.shouldPersistEvent(
                "printer-1",
                "PRINTER_TIMEOUT",
                "No response for command M105"
        );

        assertTrue(result);
    }

    @Test
    void shouldPersistEventReturnsFalseForSameEventWithinDedupWindow() {
        Instant fixedInstant = Instant.parse("2026-04-29T10:00:00Z");
        MonitoringEventPolicy policy = new MonitoringEventPolicy(
                Clock.fixed(fixedInstant, ZoneOffset.UTC),
                Duration.ofSeconds(60)
        );

        policy.rememberPersistedEvent(
                "printer-1",
                "PRINTER_TIMEOUT",
                "No response for command M105"
        );

        boolean result = policy.shouldPersistEvent(
                "printer-1",
                "PRINTER_TIMEOUT",
                "No response for command M105"
        );

        assertFalse(result);
    }

    @Test
    void shouldPersistEventReturnsTrueForSameEventAfterDedupWindow() {
        Instant baseInstant = Instant.parse("2026-04-29T10:00:00Z");
        MutableClock clock = new MutableClock(baseInstant);
        MonitoringEventPolicy policy = new MonitoringEventPolicy(clock, Duration.ofSeconds(60));

        policy.rememberPersistedEvent(
                "printer-1",
                "PRINTER_TIMEOUT",
                "No response for command M105"
        );

        clock.setInstant(baseInstant.plusSeconds(61));

        boolean result = policy.shouldPersistEvent(
                "printer-1",
                "PRINTER_TIMEOUT",
                "No response for command M105"
        );

        assertTrue(result);
    }

    @Test
    void shouldPersistEventReturnsTrueForDifferentEventType() {
        MonitoringEventPolicy policy = new MonitoringEventPolicy(
                Clock.fixed(Instant.parse("2026-04-29T10:00:00Z"), ZoneOffset.UTC),
                Duration.ofSeconds(60)
        );

        policy.rememberPersistedEvent(
                "printer-1",
                "PRINTER_TIMEOUT",
                "No response for command M105"
        );

        boolean result = policy.shouldPersistEvent(
                "printer-1",
                "PRINTER_ERROR",
                "Printer returned error response"
        );

        assertTrue(result);
    }

    @Test
    void shouldPersistEventReturnsTrueForDifferentMessage() {
        MonitoringEventPolicy policy = new MonitoringEventPolicy(
                Clock.fixed(Instant.parse("2026-04-29T10:00:00Z"), ZoneOffset.UTC),
                Duration.ofSeconds(60)
        );

        policy.rememberPersistedEvent(
                "printer-1",
                "PRINTER_TIMEOUT",
                "No response for command M105"
        );

        boolean result = policy.shouldPersistEvent(
                "printer-1",
                "PRINTER_TIMEOUT",
                "No response for command M114"
        );

        assertTrue(result);
    }

    @Test
    void shouldPersistEventUsesTrimmedPrinterId() {
        MonitoringEventPolicy policy = new MonitoringEventPolicy(
                Clock.fixed(Instant.parse("2026-04-29T10:00:00Z"), ZoneOffset.UTC),
                Duration.ofSeconds(60)
        );

        policy.rememberPersistedEvent(
                "  printer-1  ",
                "PRINTER_TIMEOUT",
                "No response for command M105"
        );

        boolean result = policy.shouldPersistEvent(
                "printer-1",
                "PRINTER_TIMEOUT",
                "No response for command M105"
        );

        assertFalse(result);
    }

    @Test
    void clearPrinterRemovesRememberedEventState() {
        MonitoringEventPolicy policy = new MonitoringEventPolicy(
                Clock.fixed(Instant.parse("2026-04-29T10:00:00Z"), ZoneOffset.UTC),
                Duration.ofSeconds(60)
        );

        policy.rememberPersistedEvent(
                "printer-1",
                "PRINTER_TIMEOUT",
                "No response for command M105"
        );

        policy.clearPrinter("printer-1");

        boolean result = policy.shouldPersistEvent(
                "printer-1",
                "PRINTER_TIMEOUT",
                "No response for command M105"
        );

        assertTrue(result);
    }

    @Test
    void clearPrinterIgnoresNullOrBlankInput() {
        MonitoringEventPolicy policy = new MonitoringEventPolicy(
                Clock.fixed(Instant.parse("2026-04-29T10:00:00Z"), ZoneOffset.UTC),
                Duration.ofSeconds(60)
        );

        assertDoesNotThrow(() -> policy.clearPrinter(null));
        assertDoesNotThrow(() -> policy.clearPrinter(""));
        assertDoesNotThrow(() -> policy.clearPrinter("   "));
    }

    @Test
    void rememberPersistedEventIgnoresNullOrBlankPrinterId() {
        MonitoringEventPolicy policy = new MonitoringEventPolicy(
                Clock.fixed(Instant.parse("2026-04-29T10:00:00Z"), ZoneOffset.UTC),
                Duration.ofSeconds(60)
        );

        assertDoesNotThrow(() -> policy.rememberPersistedEvent(null, "TYPE", "message"));
        assertDoesNotThrow(() -> policy.rememberPersistedEvent("", "TYPE", "message"));
        assertDoesNotThrow(() -> policy.rememberPersistedEvent("   ", "TYPE", "message"));
    }

    @Test
    void shouldPersistEventFailsForNullOrBlankPrinterId() {
        MonitoringEventPolicy policy = new MonitoringEventPolicy(
                Clock.systemUTC(),
                Duration.ofSeconds(60)
        );

        IllegalArgumentException ex1 = assertThrows(
                IllegalArgumentException.class,
                () -> policy.shouldPersistEvent(null, "TYPE", "message")
        );
        assertEquals("printerId must not be blank", ex1.getMessage());

        IllegalArgumentException ex2 = assertThrows(
                IllegalArgumentException.class,
                () -> policy.shouldPersistEvent("", "TYPE", "message")
        );
        assertEquals("printerId must not be blank", ex2.getMessage());

        IllegalArgumentException ex3 = assertThrows(
                IllegalArgumentException.class,
                () -> policy.shouldPersistEvent("   ", "TYPE", "message")
        );
        assertEquals("printerId must not be blank", ex3.getMessage());
    }

    @Test
    void shouldPersistEventFailsForNullOrBlankEventType() {
        MonitoringEventPolicy policy = new MonitoringEventPolicy(
                Clock.systemUTC(),
                Duration.ofSeconds(60)
        );

        IllegalArgumentException ex1 = assertThrows(
                IllegalArgumentException.class,
                () -> policy.shouldPersistEvent("printer-1", null, "message")
        );
        assertEquals("eventType must not be blank", ex1.getMessage());

        IllegalArgumentException ex2 = assertThrows(
                IllegalArgumentException.class,
                () -> policy.shouldPersistEvent("printer-1", "", "message")
        );
        assertEquals("eventType must not be blank", ex2.getMessage());

        IllegalArgumentException ex3 = assertThrows(
                IllegalArgumentException.class,
                () -> policy.shouldPersistEvent("printer-1", "   ", "message")
        );
        assertEquals("eventType must not be blank", ex3.getMessage());
    }

    @Test
    void shouldPersistEventFailsForNullOrBlankEventMessage() {
        MonitoringEventPolicy policy = new MonitoringEventPolicy(
                Clock.systemUTC(),
                Duration.ofSeconds(60)
        );

        IllegalArgumentException ex1 = assertThrows(
                IllegalArgumentException.class,
                () -> policy.shouldPersistEvent("printer-1", "TYPE", null)
        );
        assertEquals("eventMessage must not be blank", ex1.getMessage());

        IllegalArgumentException ex2 = assertThrows(
                IllegalArgumentException.class,
                () -> policy.shouldPersistEvent("printer-1", "TYPE", "")
        );
        assertEquals("eventMessage must not be blank", ex2.getMessage());

        IllegalArgumentException ex3 = assertThrows(
                IllegalArgumentException.class,
                () -> policy.shouldPersistEvent("printer-1", "TYPE", "   ")
        );
        assertEquals("eventMessage must not be blank", ex3.getMessage());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}