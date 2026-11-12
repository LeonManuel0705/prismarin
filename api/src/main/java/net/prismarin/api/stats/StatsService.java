package net.prismarin.api.stats;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.event.Subscription;

public interface StatsService {

    CompletableFuture<StatChange> increment(UUID player, String key, long amount);

    default CompletableFuture<StatChange> increment(UUID player, String key) {
        return increment(player, key, 1L);
    }

    CompletableFuture<StatChange> set(UUID player, String key, long value);

    CompletableFuture<StatChange> record(UUID player, String key, long value);

    CompletableFuture<PlayerStats> stats(UUID player);

    default CompletableFuture<Long> value(UUID player, String key, StatPeriod period) {
        String normalized = StatKeys.key(key);
        return stats(player).thenApply(found -> found.value(normalized, period));
    }

    Optional<PlayerStats> cached(UUID player);

    CompletableFuture<Leaderboard> top(String key, StatPeriod period, int size);

    Optional<Leaderboard> cachedTop(String key, StatPeriod period);

    CompletableFuture<OptionalInt> rank(UUID player, String key, StatPeriod period);

    OptionalInt cachedRank(UUID player, String key, StatPeriod period);

    Set<StatPeriod> periods();

    String periodId(StatPeriod period);

    StatDefinition definition(String key);

    List<StatDefinition> definitions();

    Subscription define(StatDefinition definition);

    StatGame game(String id);

    List<StatGame> games();

    Subscription defineGame(StatGame game);

    Subscription registerSource(StatSource source);

    Optional<StatSource> source(String game);

    default String format(String key, long value, Locale locale) {
        return definition(key).format().format(value, locale);
    }
}
