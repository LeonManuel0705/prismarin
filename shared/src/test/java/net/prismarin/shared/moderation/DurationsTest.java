package net.prismarin.shared.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DurationsTest {

    @Test
    void parsesSingleAndCombinedUnits() {
        assertEquals(Optional.of(Duration.ofMinutes(30)), Durations.parse("30m"));
        assertEquals(Optional.of(Duration.ofDays(7)), Durations.parse("1w"));
        assertEquals(Optional.of(Duration.ofDays(1).plusHours(12)), Durations.parse("1d12h"));
        assertEquals(Optional.of(Duration.ofDays(365)), Durations.parse("1Y"));
    }

    @Test
    void rejectsReasonsAndGarbage() {
        assertTrue(Durations.parse("hacking").isEmpty());
        assertTrue(Durations.parse("10").isEmpty());
        assertTrue(Durations.parse("5m spam").isEmpty());
        assertTrue(Durations.parse("0m").isEmpty());
        assertTrue(Durations.parse("").isEmpty());
    }

    @Test
    void formatsRemainingTime() {
        assertEquals("2d 3h", Durations.format(Duration.ofDays(2).plusHours(3).toMillis()));
        assertEquals("1h 5m", Durations.format(Duration.ofMinutes(65).toMillis()));
        assertEquals("1m", Durations.format(10_000L));
    }
}
