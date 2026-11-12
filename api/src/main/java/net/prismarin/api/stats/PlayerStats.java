package net.prismarin.api.stats;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public record PlayerStats(UUID player, String name, Map<String, Long> allTime, Map<String, Long> weekly,
                          Map<String, Long> monthly) {

    public PlayerStats {
        Objects.requireNonNull(player, "player");
        name = name == null ? "" : name;
        allTime = copy(allTime);
        weekly = copy(weekly);
        monthly = copy(monthly);
    }

    public static PlayerStats empty(UUID player, String name) {
        return new PlayerStats(player, name, Map.of(), Map.of(), Map.of());
    }

    public Map<String, Long> values(StatPeriod period) {
        return switch (period) {
            case ALL_TIME -> allTime;
            case WEEKLY -> weekly;
            case MONTHLY -> monthly;
        };
    }

    public long value(String key, StatPeriod period) {
        return values(period).getOrDefault(StatKeys.key(key), 0L);
    }

    public long value(String key) {
        return value(key, StatPeriod.ALL_TIME);
    }

    public boolean has(String key) {
        return allTime.containsKey(StatKeys.key(key));
    }

    public Set<String> keys() {
        return Collections.unmodifiableSet(new TreeSet<>(allTime.keySet()));
    }

    public boolean isEmpty() {
        return allTime.isEmpty();
    }

    private static Map<String, Long> copy(Map<String, Long> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
