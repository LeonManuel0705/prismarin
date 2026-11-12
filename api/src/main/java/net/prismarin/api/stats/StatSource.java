package net.prismarin.api.stats;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface StatSource {

    String game();

    CompletableFuture<Map<String, Long>> values(UUID player);

    default Set<StatPeriod> periods() {
        return Set.of(StatPeriod.ALL_TIME);
    }

    default CompletableFuture<Map<String, Long>> values(UUID player, StatPeriod period) {
        return period == StatPeriod.ALL_TIME ? values(player) : CompletableFuture.completedFuture(Map.of());
    }

    default boolean topLists() {
        return false;
    }

    default CompletableFuture<List<LeaderboardEntry>> top(String key, StatPeriod period, int size) {
        return CompletableFuture.completedFuture(List.of());
    }

    static StatSource of(String game, Function<UUID, CompletableFuture<Map<String, Long>>> values) {
        String id = StatKeys.gameId(game);
        Objects.requireNonNull(values, "values");
        return new StatSource() {
            @Override
            public String game() {
                return id;
            }

            @Override
            public CompletableFuture<Map<String, Long>> values(UUID player) {
                return values.apply(player);
            }
        };
    }
}
