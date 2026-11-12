package net.prismarin.shared.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import net.prismarin.api.stats.StatPeriod;
import net.prismarin.shared.time.DayClock;
import org.junit.jupiter.api.Test;

class StatPeriodsTest {

    private static final DayClock BERLIN = new DayClock(ZoneId.of("Europe/Berlin"), LocalTime.MIDNIGHT);

    @Test
    void weeksStartOnTheConfiguredDay() {
        StatPeriods monday = new StatPeriods(BERLIN, DayOfWeek.MONDAY, true, true);
        StatPeriods sunday = new StatPeriods(BERLIN, DayOfWeek.SUNDAY, true, true);
        Instant thursday = Instant.parse("2026-11-12T12:00:00Z");

        assertEquals("2026-11-09", monday.id(StatPeriod.WEEKLY, thursday));
        assertEquals("2026-11-08", sunday.id(StatPeriod.WEEKLY, thursday));
        assertEquals("2026-11", monday.id(StatPeriod.MONTHLY, thursday));
        assertEquals(StatPeriods.ALL_TIME_ID, monday.id(StatPeriod.ALL_TIME, thursday));
    }

    @Test
    void theWeekChangesAtTheResetInTheZone() {
        StatPeriods periods = new StatPeriods(BERLIN, DayOfWeek.MONDAY, true, true);

        assertEquals("2026-11-09", periods.id(StatPeriod.WEEKLY, Instant.parse("2026-11-15T22:59:59Z")));
        assertEquals("2026-11-16", periods.id(StatPeriod.WEEKLY, Instant.parse("2026-11-15T23:00:00Z")));
        assertEquals(Instant.parse("2026-11-15T23:00:00Z"),
                periods.end(StatPeriod.WEEKLY, Instant.parse("2026-11-12T12:00:00Z")));
    }

    @Test
    void theMonthChangesAtTheResetTime() {
        StatPeriods late = new StatPeriods(new DayClock(ZoneId.of("UTC"), LocalTime.of(6, 0)), DayOfWeek.MONDAY,
                true, true);

        assertEquals("2026-11", late.id(StatPeriod.MONTHLY, Instant.parse("2026-12-01T05:59:00Z")));
        assertEquals("2026-12", late.id(StatPeriod.MONTHLY, Instant.parse("2026-12-01T06:00:00Z")));
        assertEquals(Instant.parse("2026-12-01T06:00:00Z"),
                late.end(StatPeriod.MONTHLY, Instant.parse("2026-11-20T00:00:00Z")));
    }

    @Test
    void switchedOffPeriodsAreNotActive() {
        StatPeriods allTimeOnly = new StatPeriods(BERLIN, DayOfWeek.MONDAY, false, false);

        assertEquals(Set.of(StatPeriod.ALL_TIME), allTimeOnly.active());
        assertFalse(allTimeOnly.enabled(StatPeriod.WEEKLY));
        assertTrue(allTimeOnly.enabled(StatPeriod.ALL_TIME));
    }

    @Test
    void weekdaysParseInEnglishAndGerman() {
        assertEquals(Optional.of(DayOfWeek.MONDAY), StatPeriods.weekday("monday"));
        assertEquals(Optional.of(DayOfWeek.SUNDAY), StatPeriods.weekday("Sun"));
        assertEquals(Optional.of(DayOfWeek.MONDAY), StatPeriods.weekday("Montag"));
        assertTrue(StatPeriods.weekday("someday").isEmpty());
    }
}
