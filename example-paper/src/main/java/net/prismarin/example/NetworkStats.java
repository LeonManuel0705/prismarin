package net.prismarin.example;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.Prismarin;
import net.prismarin.api.event.Subscription;
import net.prismarin.api.stats.LeaderboardEntry;
import net.prismarin.api.stats.StatChange;
import net.prismarin.api.stats.StatDefinition;
import net.prismarin.api.stats.StatGame;
import net.prismarin.api.stats.StatPeriod;

final class NetworkStats {

    static final String WINS = "example.wins";
    static final String GAMES = "example.games";

    static final String GAME = "example";

    private final Prismarin prismarin;
    private Subscription definition;
    private Subscription game;

    NetworkStats(Prismarin prismarin) {
        this.prismarin = prismarin;
    }

    void define() {
        prismarin.stats().ifPresent(stats -> {
            definition = stats.define(StatDefinition.of(WINS, "Wins").withProfile(true));
            game = stats.defineGame(StatGame.of(GAME, "Example").withName("de", "Beispiel")
                    .withIcon("DIAMOND_SWORD").withColor("#F472B6").withOrder(20).withFeatured("wins", "games"));
        });
    }

    void undefine() {
        if (definition != null) {
            definition.unsubscribe();
            definition = null;
        }
        if (game != null) {
            game.unsubscribe();
            game = null;
        }
    }

    CompletableFuture<Optional<StatChange>> countWin(UUID player) {
        return prismarin.stats()
                .map(stats -> stats.increment(player, GAMES)
                        .thenCompose(games -> stats.increment(player, WINS))
                        .thenApply(Optional::of))
                .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty()));
    }

    CompletableFuture<List<LeaderboardEntry>> weeklyTop(int size) {
        return prismarin.stats()
                .map(stats -> stats.top(WINS, StatPeriod.WEEKLY, size).thenApply(board -> board.entries()))
                .orElseGet(() -> CompletableFuture.completedFuture(List.of()));
    }
}
