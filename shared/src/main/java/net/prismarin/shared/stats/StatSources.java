package net.prismarin.shared.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.prismarin.api.stats.LeaderboardEntry;
import net.prismarin.api.stats.PlayerStats;
import net.prismarin.api.stats.StatKeys;

public final class StatSources {

    public record Read(String game, boolean ranked, Map<String, Long> all, Map<String, Long> weekly,
                       Map<String, Long> monthly) {

        public Read {
            game = StatKeys.gameId(game);
            all = normalize(game, all);
            weekly = normalize(game, weekly);
            monthly = normalize(game, monthly);
        }

        public boolean empty() {
            return all.isEmpty() && weekly.isEmpty() && monthly.isEmpty();
        }
    }

    public record Plan(Map<String, Long> all, Map<String, Long> weekly, Map<String, Long> monthly) {

        public Plan {
            all = Collections.unmodifiableMap(new LinkedHashMap<>(all));
            weekly = Collections.unmodifiableMap(new LinkedHashMap<>(weekly));
            monthly = Collections.unmodifiableMap(new LinkedHashMap<>(monthly));
        }

        public boolean empty() {
            return all.isEmpty() && weekly.isEmpty() && monthly.isEmpty();
        }
    }

    private StatSources() {
        throw new AssertionError();
    }

    public static Map<String, Long> normalize(String game, Map<String, Long> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        String id = StatKeys.gameId(game);
        Map<String, Long> clean = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key == null || value == null || !StatKeys.valid(key)) {
                return;
            }
            String normalized = StatKeys.key(key);
            String full = StatKeys.game(normalized).equals(id) ? normalized : id + "." + normalized;
            if (StatKeys.valid(full)) {
                clean.put(full, value);
            }
        });
        return Collections.unmodifiableMap(clean);
    }

    public static PlayerStats merge(PlayerStats base, List<Read> reads) {
        if (reads.isEmpty()) {
            return base;
        }
        Map<String, Long> all = new LinkedHashMap<>(base.allTime());
        Map<String, Long> weekly = new LinkedHashMap<>(base.weekly());
        Map<String, Long> monthly = new LinkedHashMap<>(base.monthly());
        for (Read read : reads) {
            all.putAll(read.all());
            weekly.putAll(read.weekly());
            monthly.putAll(read.monthly());
        }
        return new PlayerStats(base.player(), base.name(), all, weekly, monthly);
    }

    public static Plan changes(PlayerStats stored, List<Read> reads) {
        Map<String, Long> all = new LinkedHashMap<>();
        Map<String, Long> weekly = new LinkedHashMap<>();
        Map<String, Long> monthly = new LinkedHashMap<>();
        for (Read read : reads) {
            if (read.ranked()) {
                continue;
            }
            read.all().forEach((key, value) -> {
                boolean changed = !Objects.equals(stored.allTime().get(key), value)
                        || differs(read.weekly(), stored.weekly(), key)
                        || differs(read.monthly(), stored.monthly(), key);
                if (changed) {
                    all.put(key, value);
                    if (read.weekly().containsKey(key)) {
                        weekly.put(key, read.weekly().get(key));
                    }
                    if (read.monthly().containsKey(key)) {
                        monthly.put(key, read.monthly().get(key));
                    }
                }
            });
            read.weekly().forEach((key, value) -> {
                if (!read.all().containsKey(key) && !Objects.equals(stored.weekly().get(key), value)) {
                    weekly.put(key, value);
                }
            });
            read.monthly().forEach((key, value) -> {
                if (!read.all().containsKey(key) && !Objects.equals(stored.monthly().get(key), value)) {
                    monthly.put(key, value);
                }
            });
        }
        return new Plan(all, weekly, monthly);
    }

    public static List<LeaderboardEntry> entries(List<LeaderboardEntry> given, int size) {
        if (given == null || given.isEmpty()) {
            return List.of();
        }
        List<LeaderboardEntry> entries = new ArrayList<>();
        for (LeaderboardEntry entry : given) {
            if (entry == null) {
                continue;
            }
            if (entries.size() >= Math.max(0, size)) {
                break;
            }
            int rank = entry.rank() > 0 ? entry.rank() : entries.size() + 1;
            entries.add(new LeaderboardEntry(rank, entry.player(), entry.name(), entry.value()));
        }
        return List.copyOf(entries);
    }

    public static List<LeaderboardEntry> named(List<LeaderboardEntry> entries, Map<UUID, String> shown) {
        List<LeaderboardEntry> named = new ArrayList<>(entries.size());
        for (LeaderboardEntry entry : entries) {
            String name = shown == null ? entry.name() : shown.getOrDefault(entry.player(), entry.name());
            if (name.isBlank()) {
                name = entry.player().toString().substring(0, 8);
            }
            named.add(new LeaderboardEntry(entry.rank(), entry.player(), name, entry.value()));
        }
        return List.copyOf(named);
    }

    private static boolean differs(Map<String, Long> read, Map<String, Long> stored, String key) {
        return read.containsKey(key) && !Objects.equals(stored.get(key), read.get(key));
    }
}
