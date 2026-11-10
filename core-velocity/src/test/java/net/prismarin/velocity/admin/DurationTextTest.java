package net.prismarin.velocity.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DurationTextTest {

    private static final Map<String, String> GERMAN = Map.ofEntries(
            Map.entry("separator", " "), Map.entry("year", "1 Jahr"), Map.entry("years", "%count% Jahre"),
            Map.entry("day", "1 Tag"), Map.entry("days", "%count% Tage"), Map.entry("hour", "1 Stunde"),
            Map.entry("hours", "%count% Stunden"), Map.entry("minute", "1 Minute"),
            Map.entry("minutes", "%count% Minuten"), Map.entry("second", "1 Sekunde"),
            Map.entry("seconds", "%count% Sekunden"));

    private static String german(Duration duration) {
        return DurationText.format(duration.toMillis(), GERMAN::get);
    }

    @Test
    void showsAtMostTwoNeighbouringUnits() {
        assertEquals("30 Tage", german(Duration.ofDays(30)));
        assertEquals("1 Tag 6 Stunden", german(Duration.ofHours(30)));
        assertEquals("1 Jahr", german(Duration.ofDays(365).plusHours(3)));
        assertEquals("2 Stunden 1 Minute", german(Duration.ofMinutes(121).plusSeconds(5)));
        assertEquals("1 Tag", german(Duration.ofDays(1).plusMinutes(5)));
        assertEquals("45 Sekunden", german(Duration.ofSeconds(45)));
    }

    @Test
    void neverShowsLessThanOneSecond() {
        assertEquals("1 Sekunde", german(Duration.ZERO));
        assertEquals(List.of(new DurationText.Part(DurationText.Unit.SECOND, 1)), DurationText.parts(-5L));
    }

    @Test
    void compactUnitsServeTheAuditLog() {
        assertEquals("30d", DurationText.compact(Duration.ofDays(30).toMillis()));
        assertEquals("1h 30m", DurationText.compact(Duration.ofMinutes(90).toMillis()));
    }
}
