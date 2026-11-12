package net.prismarin.shared.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DayClockTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void theDayFollowsTheZoneNotUtc() {
        DayClock berlin = new DayClock(BERLIN, LocalTime.MIDNIGHT);
        DayClock utc = DayClock.utc();
        Instant lateEvening = Instant.parse("2026-11-12T23:30:00Z");

        assertEquals(LocalDate.of(2026, 11, 13), berlin.day(lateEvening));
        assertEquals(LocalDate.of(2026, 11, 12), utc.day(lateEvening));
        assertEquals(LocalDate.of(2026, 11, 12), new DayClock(ZoneId.of("America/New_York"), LocalTime.MIDNIGHT)
                .day(Instant.parse("2026-11-13T04:59:00Z")));
    }

    @Test
    void aLaterResetTimeKeepsTheEarlyMorningOnThePreviousDay() {
        DayClock clock = new DayClock(BERLIN, LocalTime.of(6, 0));

        assertEquals(LocalDate.of(2026, 11, 11), clock.day(Instant.parse("2026-11-12T04:59:59Z")));
        assertEquals(LocalDate.of(2026, 11, 12), clock.day(Instant.parse("2026-11-12T05:00:00Z")));
        assertEquals(Instant.parse("2026-11-13T05:00:00Z"), clock.nextReset(Instant.parse("2026-11-12T05:00:00Z")));
    }

    @Test
    void theResetTimeIsWallClockTimeAcrossDaylightSaving() {
        DayClock clock = new DayClock(BERLIN, LocalTime.of(6, 0));
        Instant afterSwitch = Instant.parse("2026-03-29T04:30:00Z");

        assertEquals(LocalDate.of(2026, 3, 29), clock.day(afterSwitch));
        assertEquals(LocalDate.of(2026, 3, 28), clock.day(Instant.parse("2026-03-29T03:59:00Z")));
        assertEquals(Instant.parse("2026-03-30T04:00:00Z"), clock.nextReset(afterSwitch));
    }

    @Test
    void epochDaysCountWholeDays() {
        DayClock clock = DayClock.utc();

        assertEquals(1L, clock.epochDay(Instant.parse("2026-11-13T00:00:00Z"))
                - clock.epochDay(Instant.parse("2026-11-12T23:59:59Z")));
    }

    @Test
    void zonesAndTimesAreParsedLeniently() {
        assertEquals(Optional.of(BERLIN), DayClock.zone("Europe/Berlin"));
        assertEquals(Optional.of(ZoneId.systemDefault()), DayClock.zone("server"));
        assertTrue(DayClock.zone("Mars/Base").isEmpty());
        assertEquals(Optional.of(LocalTime.of(6, 0)), DayClock.time("6"));
        assertEquals(Optional.of(LocalTime.of(6, 30)), DayClock.time("6:30"));
        assertEquals(Optional.of(LocalTime.of(18, 5)), DayClock.time("18:05"));
        assertTrue(DayClock.time("25").isEmpty());
        assertTrue(DayClock.time("noon").isEmpty());
    }
}
