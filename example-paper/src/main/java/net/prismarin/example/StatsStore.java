package net.prismarin.example;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.prismarin.api.storage.DataStorage;
import net.prismarin.api.storage.TypedCollection;

final class StatsStore {

    private final TypedCollection<Stats> stats;
    private final Map<UUID, Stats> cache = new ConcurrentHashMap<>();

    StatsStore(DataStorage storage) {
        this.stats = storage.namespace("example").collection("stats", Stats.class);
    }

    CompletableFuture<Stats> load(UUID player, String name) {
        return stats.find(player.toString()).thenApply(found -> {
            Stats current = found.orElseGet(() -> Stats.fresh(name));
            cache.put(player, current);
            return current;
        });
    }

    CompletableFuture<Stats> recordWin(UUID player, String name) {
        return load(player, name).thenCompose(current -> {
            Stats updated = current.won();
            cache.put(player, updated);
            return stats.save(player.toString(), updated).thenApply(saved -> updated);
        });
    }

    CompletableFuture<List<Stats>> top(int size) {
        return stats.list().thenApply(all -> all.values().stream()
                .sorted(Comparator.comparingInt(Stats::wins).reversed())
                .limit(size)
                .toList());
    }

    Optional<Stats> cached(UUID player) {
        return Optional.ofNullable(cache.get(player));
    }

    void forget(UUID player) {
        cache.remove(player);
    }
}
