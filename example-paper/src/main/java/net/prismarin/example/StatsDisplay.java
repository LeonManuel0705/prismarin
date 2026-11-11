package net.prismarin.example;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.prismarin.api.event.Subscription;
import net.prismarin.api.player.PlayerAttribute;
import net.prismarin.api.player.PlayerService;
import net.prismarin.api.player.RankDisplay;

final class StatsDisplay {

    private static final int CHAMPION_WINS = 10;
    private static final RankDisplay CHAMPION = new RankDisplay("Champion",
            Component.text("[Champion] ", NamedTextColor.GOLD), Component.empty(), NamedTextColor.GOLD, null, 50);

    private final StatsStore stats;
    private final List<Subscription> registrations = new ArrayList<>();

    StatsDisplay(StatsStore stats) {
        this.stats = stats;
    }

    void register(PlayerService players) {
        registrations.add(players.attributes().register(PlayerAttribute.of("example_wins",
                player -> stats.cached(player.uuid()).map(found -> String.valueOf(found.wins())).orElse(""))));
        registrations.add(players.ranks().register(player -> stats.cached(player)
                .filter(found -> found.wins() >= CHAMPION_WINS)
                .map(found -> CHAMPION), -10));
    }

    void unregister() {
        registrations.forEach(Subscription::unsubscribe);
        registrations.clear();
    }
}
