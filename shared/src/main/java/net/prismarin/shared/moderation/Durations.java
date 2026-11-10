package net.prismarin.shared.moderation;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Durations {

    private static final Pattern PART = Pattern.compile("(\\d+)([smhdwy])");

    private Durations() {
        throw new AssertionError();
    }

    public static Optional<Duration> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        Matcher matcher = PART.matcher(lower);
        Duration total = Duration.ZERO;
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) {
                return Optional.empty();
            }
            long amount = Long.parseLong(matcher.group(1));
            total = total.plus(switch (matcher.group(2)) {
                case "s" -> Duration.ofSeconds(amount);
                case "m" -> Duration.ofMinutes(amount);
                case "h" -> Duration.ofHours(amount);
                case "d" -> Duration.ofDays(amount);
                case "w" -> Duration.ofDays(amount * 7L);
                default -> Duration.ofDays(amount * 365L);
            });
            end = matcher.end();
        }
        return end == lower.length() && !total.isZero() ? Optional.of(total) : Optional.empty();
    }

    public static String format(long millis) {
        Duration remaining = Duration.ofMillis(Math.max(0L, millis));
        long days = remaining.toDays();
        long hours = remaining.toHoursPart();
        long minutes = remaining.toMinutesPart();
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return Math.max(1L, minutes) + "m";
    }
}
