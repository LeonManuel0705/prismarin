package net.prismarin.shared.text;

import java.util.Locale;

public record TimeAgo(Unit unit, long amount) {

    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60L * MINUTE;
    private static final long DAY = 24L * HOUR;

    public enum Unit {
        UNKNOWN,
        NOW,
        MINUTES,
        HOURS,
        DAYS,
        MONTHS;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static TimeAgo since(long then, long now) {
        if (then <= 0L) {
            return new TimeAgo(Unit.UNKNOWN, 0L);
        }
        return of(Math.max(0L, now - then));
    }

    public static TimeAgo of(long millis) {
        if (millis < MINUTE) {
            return new TimeAgo(Unit.NOW, 0L);
        }
        return span(millis);
    }

    public static TimeAgo remaining(long millis) {
        return span(Math.max(MINUTE, millis));
    }

    private static TimeAgo span(long millis) {
        if (millis < HOUR) {
            return new TimeAgo(Unit.MINUTES, millis / MINUTE);
        }
        if (millis < 2L * DAY) {
            return new TimeAgo(Unit.HOURS, millis / HOUR);
        }
        if (millis < 60L * DAY) {
            return new TimeAgo(Unit.DAYS, millis / DAY);
        }
        return new TimeAgo(Unit.MONTHS, millis / (30L * DAY));
    }
}
