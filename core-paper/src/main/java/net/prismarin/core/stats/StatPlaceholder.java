package net.prismarin.core.stats;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.prismarin.api.stats.StatKeys;
import net.prismarin.api.stats.StatPeriod;

public record StatPlaceholder(Kind kind, String key, StatPeriod period, int position, Field field) {

    public static final String STAT_PREFIX = "stat_";
    public static final String TOP_PREFIX = "top_";
    public static final int MAX_POSITION = 100;

    public enum Kind {
        VALUE,
        TOP
    }

    public enum Field {
        VALUE,
        RAW,
        RANK,
        NAME,
        UUID
    }

    public static boolean candidate(String params) {
        String lower = params == null ? "" : params.toLowerCase(Locale.ROOT);
        return lower.startsWith(STAT_PREFIX) || lower.startsWith(TOP_PREFIX);
    }

    public static Optional<StatPlaceholder> parse(String params) {
        if (params == null) {
            return Optional.empty();
        }
        String lower = params.toLowerCase(Locale.ROOT);
        if (lower.startsWith(STAT_PREFIX)) {
            return value(List.of(lower.substring(STAT_PREFIX.length()).split("_", -1)));
        }
        if (lower.startsWith(TOP_PREFIX)) {
            return top(List.of(lower.substring(TOP_PREFIX.length()).split("_", -1)));
        }
        return Optional.empty();
    }

    private static Optional<StatPlaceholder> value(List<String> parts) {
        if (parts.isEmpty() || parts.size() > 3 || !StatKeys.valid(parts.getFirst())) {
            return Optional.empty();
        }
        String key = StatKeys.key(parts.getFirst());
        if (parts.size() == 1) {
            return Optional.of(new StatPlaceholder(Kind.VALUE, key, StatPeriod.ALL_TIME, 0, Field.VALUE));
        }
        Optional<StatPeriod> period = StatPeriod.byId(parts.get(1));
        if (parts.size() == 2) {
            if (period.isPresent()) {
                return Optional.of(new StatPlaceholder(Kind.VALUE, key, period.get(), 0, Field.VALUE));
            }
            return valueField(parts.get(1))
                    .map(field -> new StatPlaceholder(Kind.VALUE, key, StatPeriod.ALL_TIME, 0, field));
        }
        if (period.isEmpty()) {
            return Optional.empty();
        }
        return valueField(parts.get(2)).map(field -> new StatPlaceholder(Kind.VALUE, key, period.get(), 0, field));
    }

    private static Optional<StatPlaceholder> top(List<String> parts) {
        if (parts.size() < 3 || parts.size() > 4 || !StatKeys.valid(parts.getFirst())) {
            return Optional.empty();
        }
        String key = StatKeys.key(parts.getFirst());
        StatPeriod period = StatPeriod.ALL_TIME;
        int index = 1;
        if (parts.size() == 4) {
            Optional<StatPeriod> parsed = StatPeriod.byId(parts.get(1));
            if (parsed.isEmpty()) {
                return Optional.empty();
            }
            period = parsed.get();
            index = 2;
        }
        int position;
        try {
            position = Integer.parseInt(parts.get(index));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        if (position < 1 || position > MAX_POSITION) {
            return Optional.empty();
        }
        Optional<Field> field = switch (parts.get(index + 1)) {
            case "name" -> Optional.of(Field.NAME);
            case "value" -> Optional.of(Field.VALUE);
            case "raw" -> Optional.of(Field.RAW);
            case "uuid" -> Optional.of(Field.UUID);
            default -> Optional.empty();
        };
        StatPeriod chosen = period;
        return field.map(value -> new StatPlaceholder(Kind.TOP, key, chosen, position, value));
    }

    private static Optional<Field> valueField(String part) {
        return switch (part) {
            case "raw" -> Optional.of(Field.RAW);
            case "rank" -> Optional.of(Field.RANK);
            default -> Optional.empty();
        };
    }
}
