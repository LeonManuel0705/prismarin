package net.prismarin.shared.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TimeAgoTest {

    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    @Test
    void picksAReadableUnit() {
        assertEquals(new TimeAgo(TimeAgo.Unit.NOW, 0), TimeAgo.of(30_000L));
        assertEquals(new TimeAgo(TimeAgo.Unit.MINUTES, 5), TimeAgo.of(5 * MINUTE + 10_000L));
        assertEquals(new TimeAgo(TimeAgo.Unit.HOURS, 3), TimeAgo.of(3 * HOUR));
        assertEquals(new TimeAgo(TimeAgo.Unit.HOURS, 47), TimeAgo.of(2 * DAY - 1));
        assertEquals(new TimeAgo(TimeAgo.Unit.DAYS, 2), TimeAgo.of(2 * DAY));
        assertEquals(new TimeAgo(TimeAgo.Unit.MONTHS, 3), TimeAgo.of(90 * DAY));
    }

    @Test
    void unknownWhenNeverSeen() {
        assertEquals(TimeAgo.Unit.UNKNOWN, TimeAgo.since(0L, 1_000L).unit());
        assertEquals(new TimeAgo(TimeAgo.Unit.MINUTES, 2), TimeAgo.since(1_000L, 1_000L + 2 * MINUTE));
        assertEquals(TimeAgo.Unit.NOW, TimeAgo.since(5_000L, 1_000L).unit());
    }

    @Test
    void remainingTimeNeverSaysNow() {
        assertEquals(new TimeAgo(TimeAgo.Unit.MINUTES, 1), TimeAgo.remaining(5_000L));
        assertEquals(new TimeAgo(TimeAgo.Unit.DAYS, 3), TimeAgo.remaining(72 * HOUR));
        assertEquals(new TimeAgo(TimeAgo.Unit.MINUTES, 1), TimeAgo.remaining(-10L));
        assertEquals("minutes", TimeAgo.Unit.MINUTES.key());
    }
}
