package net.prismarin.shared.time;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public record DayClock(ZoneId zone, LocalTime reset) {

    public static final String SERVER_ZONE = "server";

    public DayClock {
        Objects.requireNonNull(zone, "zone");
        reset = reset == null ? LocalTime.MIDNIGHT : reset.withNano(0);
    }

    public static DayClock utc() {
        return new DayClock(ZoneId.of("UTC"), LocalTime.MIDNIGHT);
    }

    public LocalDate day(Instant instant) {
        return instant.atZone(zone).toLocalDateTime().minusSeconds(reset.toSecondOfDay()).toLocalDate();
    }

    public long epochDay(Instant instant) {
        return day(instant).toEpochDay();
    }

    public Instant start(LocalDate day) {
        return day.atTime(reset).atZone(zone).toInstant();
    }

    public Instant nextReset(Instant instant) {
        return start(day(instant).plusDays(1));
    }

    public static Optional<ZoneId> zone(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String trimmed = value.strip();
        if (trimmed.equalsIgnoreCase(SERVER_ZONE) || trimmed.equalsIgnoreCase("system")) {
            return Optional.of(ZoneId.systemDefault());
        }
        try {
            return Optional.of(ZoneId.of(trimmed));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }

    public static Optional<LocalTime> time(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String trimmed = value.strip().toLowerCase(Locale.ROOT);
        if (trimmed.matches("\\d{1,2}")) {
            int hour = Integer.parseInt(trimmed);
            return hour < 24 ? Optional.of(LocalTime.of(hour, 0)) : Optional.empty();
        }
        try {
            return Optional.of(LocalTime.parse(trimmed.length() == 4 ? "0" + trimmed : trimmed));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
