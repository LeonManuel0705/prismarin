package net.prismarin.shared.stats;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.prismarin.api.stats.StatPeriod;
import net.prismarin.shared.time.DayClock;

public record StatPeriods(DayClock clock, DayOfWeek weekStart, boolean weekly, boolean monthly) {

    public static final String ALL_TIME_ID = "all";

    public StatPeriods {
        Objects.requireNonNull(clock, "clock");
        weekStart = weekStart == null ? DayOfWeek.MONDAY : weekStart;
    }

    public static StatPeriods defaults() {
        return new StatPeriods(new DayClock(ZoneId.systemDefault(), LocalTime.MIDNIGHT), DayOfWeek.MONDAY, true, true);
    }

    public boolean enabled(StatPeriod period) {
        return switch (period) {
            case ALL_TIME -> true;
            case WEEKLY -> weekly;
            case MONTHLY -> monthly;
        };
    }

    public Set<StatPeriod> active() {
        Set<StatPeriod> active = EnumSet.of(StatPeriod.ALL_TIME);
        if (weekly) {
            active.add(StatPeriod.WEEKLY);
        }
        if (monthly) {
            active.add(StatPeriod.MONTHLY);
        }
        return Set.copyOf(active);
    }

    public String id(StatPeriod period, Instant now) {
        LocalDate day = clock.day(now);
        return switch (period) {
            case ALL_TIME -> ALL_TIME_ID;
            case WEEKLY -> day.with(TemporalAdjusters.previousOrSame(weekStart)).toString();
            case MONTHLY -> day.withDayOfMonth(1).toString().substring(0, 7);
        };
    }

    public Instant end(StatPeriod period, Instant now) {
        LocalDate day = clock.day(now);
        return switch (period) {
            case ALL_TIME -> Instant.MAX;
            case WEEKLY -> clock.start(day.with(TemporalAdjusters.previousOrSame(weekStart)).plusWeeks(1));
            case MONTHLY -> clock.start(day.withDayOfMonth(1).plusMonths(1));
        };
    }

    public static Optional<DayOfWeek> weekday(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        for (DayOfWeek day : DayOfWeek.values()) {
            if (day.name().equals(normalized) || day.name().startsWith(normalized) && normalized.length() >= 2) {
                return Optional.of(day);
            }
        }
        return switch (normalized) {
            case "MONTAG" -> Optional.of(DayOfWeek.MONDAY);
            case "SONNTAG" -> Optional.of(DayOfWeek.SUNDAY);
            case "SAMSTAG" -> Optional.of(DayOfWeek.SATURDAY);
            default -> Optional.empty();
        };
    }
}
