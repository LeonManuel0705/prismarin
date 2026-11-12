package net.prismarin.api.stats;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;

public enum StatFormat {
    NUMBER("number"),
    PLAIN("plain"),
    DURATION("duration"),
    MILLIS("millis"),
    DECIMAL("decimal");

    private final String id;

    StatFormat(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String format(long value, Locale locale) {
        Locale target = locale == null ? Locale.ROOT : locale;
        return switch (this) {
            case NUMBER -> NumberFormat.getIntegerInstance(target).format(value);
            case PLAIN -> Long.toString(value);
            case DURATION -> clock(value, false);
            case MILLIS -> clock(value, true);
            case DECIMAL -> decimal(value, target);
        };
    }

    public static Optional<StatFormat> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.strip().toLowerCase(Locale.ROOT);
        for (StatFormat format : values()) {
            if (format.id.equals(normalized)) {
                return Optional.of(format);
            }
        }
        return switch (normalized) {
            case "time", "seconds" -> Optional.of(DURATION);
            case "milliseconds", "ms" -> Optional.of(MILLIS);
            default -> Optional.empty();
        };
    }

    public static StatFormat guess(String key) {
        if (!StatKeys.valid(key)) {
            return NUMBER;
        }
        String normalized = StatKeys.key(key);
        String last = normalized.substring(normalized.lastIndexOf('.') + 1);
        if (last.equals("millis") || last.endsWith("-millis") || last.endsWith("-ms")) {
            return MILLIS;
        }
        if (last.equals("seconds") || last.endsWith("-seconds") || last.endsWith("-secs")) {
            return DURATION;
        }
        return NUMBER;
    }

    private static String clock(long value, boolean millis) {
        if (value < 0L) {
            return "-" + clock(value == Long.MIN_VALUE ? Long.MAX_VALUE : -value, millis);
        }
        long seconds = millis ? value / 1000L : value;
        long hours = seconds / 3600L;
        long minutes = seconds % 3600L / 60L;
        long rest = seconds % 60L;
        String text = hours > 0L
                ? hours + ":" + two(minutes) + ":" + two(rest)
                : minutes + ":" + two(rest);
        return millis ? text + "." + String.format(Locale.ROOT, "%03d", value % 1000L) : text;
    }

    private static String two(long value) {
        return value < 10L ? "0" + value : Long.toString(value);
    }

    private static String decimal(long value, Locale locale) {
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(value / 100.0d);
    }
}
