package net.prismarin.velocity.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public final class DurationText {

    public enum Unit {
        YEAR(365L * 86_400_000L),
        DAY(86_400_000L),
        HOUR(3_600_000L),
        MINUTE(60_000L),
        SECOND(1_000L);

        private final long millis;

        Unit(long millis) {
            this.millis = millis;
        }

        public long millis() {
            return millis;
        }

        public String key(long count) {
            String key = name().toLowerCase(Locale.ROOT);
            return count == 1L ? key : key + "s";
        }
    }

    public record Part(Unit unit, long count) {
    }

    private static final int PARTS = 2;

    private DurationText() {
        throw new AssertionError();
    }

    public static List<Part> parts(long millis) {
        long rest = Math.max(1_000L, millis);
        Unit[] units = Unit.values();
        int first = 0;
        while (first < units.length - 1 && rest < units[first].millis()) {
            first++;
        }
        List<Part> parts = new ArrayList<>(PARTS);
        for (int index = first; index < units.length && parts.size() < PARTS; index++) {
            long count = rest / units[index].millis();
            if (count == 0L) {
                break;
            }
            parts.add(new Part(units[index], count));
            rest -= count * units[index].millis();
        }
        return parts;
    }

    public static String compact(long millis) {
        return format(millis, key -> "separator".equals(key) ? " " : "%count%" + key.charAt(0));
    }

    public static String format(long millis, Function<String, String> texts) {
        StringBuilder text = new StringBuilder();
        String separator = texts.apply("separator");
        for (Part part : parts(millis)) {
            if (!text.isEmpty()) {
                text.append(separator);
            }
            text.append(texts.apply(part.unit().key(part.count())).replace("%count%", String.valueOf(part.count())));
        }
        return text.toString();
    }
}
