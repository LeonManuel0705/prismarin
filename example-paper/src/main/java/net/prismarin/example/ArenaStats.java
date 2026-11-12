package net.prismarin.example;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.Prismarin;
import net.prismarin.api.event.Subscription;
import net.prismarin.api.stats.StatGame;
import net.prismarin.api.stats.StatSource;

final class ArenaStats implements StatSource {

    static final String GAME = "arena";

    private final StatsStore store;
    private Subscription source;
    private Subscription game;

    ArenaStats(StatsStore store) {
        this.store = store;
    }

    void register(Prismarin prismarin) {
        prismarin.stats().ifPresent(stats -> {
            source = stats.registerSource(this);
            game = stats.defineGame(StatGame.of(GAME, "Arena").withIcon("IRON_SWORD").withColor("#FB923C")
                    .withOrder(30).withFeatured("wins"));
        });
    }

    void unregister() {
        if (source != null) {
            source.unsubscribe();
            source = null;
        }
        if (game != null) {
            game.unsubscribe();
            game = null;
        }
    }

    @Override
    public String game() {
        return GAME;
    }

    @Override
    public CompletableFuture<Map<String, Long>> values(UUID player) {
        return store.find(player).thenApply(found -> found.map(stats -> {
            Map<String, Long> values = new LinkedHashMap<>();
            values.put("wins", (long) stats.wins());
            values.put("games", (long) stats.games());
            return values;
        }).orElse(Map.of()));
    }
}
