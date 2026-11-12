package net.prismarin.shared.stats;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.prismarin.api.stats.PlayerStats;
import net.prismarin.api.stats.StatChange;
import net.prismarin.api.stats.StatOrder;
import net.prismarin.api.stats.StatPeriod;

public final class StatUpdates {

    public record Applied(String json, StatChange change, PlayerStats stats, boolean improved) {
    }

    public record Mirrored(String json, List<StatChange> changes, PlayerStats stats) {

        public Mirrored {
            changes = List.copyOf(changes);
        }

        public boolean changed() {
            return json != null;
        }
    }

    private StatUpdates() {
        throw new AssertionError();
    }

    public static Applied apply(String json, UUID player, String name, String key, StatChange.Type type, long amount,
                                StatOrder order, String weekId, String monthId, long now) {
        StatDocument document = StatDocument.decode(json, player);
        if (name != null && !name.isBlank()) {
            document.name = name;
        }
        StatDocument.Bucket week = weekId == null ? null : document.bucket(StatPeriod.WEEKLY, weekId);
        StatDocument.Bucket month = monthId == null ? null : document.bucket(StatPeriod.MONTHLY, monthId);
        boolean known = document.all.containsKey(key);
        long previous = document.all.getOrDefault(key, 0L);
        long value = switch (type) {
            case INCREMENT -> add(previous, amount);
            case SET -> amount;
            case RECORD -> !known || order.better(amount, previous) ? amount : previous;
        };
        boolean improved = type == StatChange.Type.RECORD && (!known || value != previous);
        if (value != previous || !known) {
            document.all.put(key, value);
            document.at.put(key, now);
        }
        long delta = subtract(value, previous);
        long weekly = week == null ? 0L : period(week, key, type, amount, delta, order, now);
        long monthly = month == null ? 0L : period(month, key, type, amount, delta, order, now);
        StatChange change = new StatChange(player, key, type, previous, value, weekly, monthly);
        PlayerStats stats = document.view(player, weekId == null ? "" : weekId, monthId == null ? "" : monthId);
        return new Applied(document.encode(), change, stats, improved);
    }

    public static Mirrored mirror(String json, UUID player, String name, Map<String, Long> all,
                                  Map<String, Long> weekly, Map<String, Long> monthly, String weekId, String monthId,
                                  long now) {
        StatDocument document = StatDocument.decode(json, player);
        StatDocument.Bucket week = weekId == null ? null : document.bucket(StatPeriod.WEEKLY, weekId);
        StatDocument.Bucket month = monthId == null ? null : document.bucket(StatPeriod.MONTHLY, monthId);
        boolean dirty = false;
        List<StatChange> changes = new ArrayList<>();
        for (Map.Entry<String, Long> entry : all.entrySet()) {
            String key = entry.getKey();
            long value = entry.getValue();
            boolean known = document.all.containsKey(key);
            long previous = document.all.getOrDefault(key, 0L);
            boolean changed = !known || previous != value;
            if (changed) {
                document.all.put(key, value);
                document.at.put(key, now);
                dirty = true;
            }
            long delta = known ? subtract(value, previous) : 0L;
            dirty |= mirrorPeriod(week, key, weekly, delta, now);
            dirty |= mirrorPeriod(month, key, monthly, delta, now);
            if (changed) {
                changes.add(new StatChange(player, key, StatChange.Type.SET, previous, value,
                        week == null ? 0L : week.values.getOrDefault(key, 0L),
                        month == null ? 0L : month.values.getOrDefault(key, 0L)));
            }
        }
        dirty |= absolute(week, weekly, all, now);
        dirty |= absolute(month, monthly, all, now);
        if (dirty && name != null && !name.isBlank()) {
            document.name = name;
        }
        PlayerStats stats = document.view(player, weekId == null ? "" : weekId, monthId == null ? "" : monthId);
        return new Mirrored(dirty ? document.encode() : null, changes, stats);
    }

    public static PlayerStats read(String json, UUID player, String weekId, String monthId) {
        return StatDocument.decode(json, player).view(player, weekId, monthId);
    }

    private static long period(StatDocument.Bucket bucket, String key, StatChange.Type type, long amount, long delta,
                               StatOrder order, long now) {
        Map<String, Long> values = bucket.values;
        boolean known = values.containsKey(key);
        long previous = values.getOrDefault(key, 0L);
        long value = switch (type) {
            case INCREMENT -> add(previous, amount);
            case SET -> add(previous, delta);
            case RECORD -> !known || order.better(amount, previous) ? amount : previous;
        };
        if (type == StatChange.Type.SET && delta == 0L && known) {
            return previous;
        }
        if (value != previous || !known) {
            values.put(key, value);
            bucket.at.put(key, now);
        }
        return value;
    }

    private static boolean mirrorPeriod(StatDocument.Bucket bucket, String key, Map<String, Long> given, long delta,
                                        long now) {
        if (bucket == null) {
            return false;
        }
        Long wanted = given.get(key);
        if (wanted != null) {
            return put(bucket, key, wanted, now);
        }
        if (delta == 0L) {
            return false;
        }
        return put(bucket, key, add(bucket.values.getOrDefault(key, 0L), delta), now);
    }

    private static boolean absolute(StatDocument.Bucket bucket, Map<String, Long> given, Map<String, Long> all,
                                    long now) {
        if (bucket == null) {
            return false;
        }
        boolean dirty = false;
        for (Map.Entry<String, Long> entry : given.entrySet()) {
            if (!all.containsKey(entry.getKey())) {
                dirty |= put(bucket, entry.getKey(), entry.getValue(), now);
            }
        }
        return dirty;
    }

    private static boolean put(StatDocument.Bucket bucket, String key, long value, long now) {
        Long current = bucket.values.get(key);
        if (current != null && current == value) {
            return false;
        }
        bucket.values.put(key, value);
        bucket.at.put(key, now);
        return true;
    }

    static long add(long first, long second) {
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException e) {
            return second > 0L ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    private static long subtract(long first, long second) {
        try {
            return Math.subtractExact(first, second);
        } catch (ArithmeticException e) {
            return first > second ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }
}
