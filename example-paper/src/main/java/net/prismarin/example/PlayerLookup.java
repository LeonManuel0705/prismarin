package net.prismarin.example;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.player.NetworkPlayer;
import net.prismarin.api.player.PlayerService;
import net.prismarin.api.social.Party;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class PlayerLookup {

    private static final UUID CONSOLE = new UUID(0L, 0L);

    private final PlayerService players;
    private final StatsStore stats;
    private final Texts texts;

    PlayerLookup(PlayerService players, StatsStore stats, Texts texts) {
        this.players = players;
        this.stats = stats;
        this.texts = texts;
    }

    CompletableFuture<Optional<NetworkPlayer>> resolve(CommandSender viewer, String typed) {
        return players.resolve(viewerId(viewer), typed);
    }

    void whois(CommandSender viewer, String typed) {
        UUID viewerId = viewerId(viewer);
        resolve(viewer, typed).thenAccept(found -> {
            if (found.isEmpty()) {
                texts.send(viewer, "unknown-player", "name", typed);
                return;
            }
            NetworkPlayer target = found.get();
            texts.send(viewer, "whois",
                    "name", players.displayName(viewerId, target),
                    "rank", players.rank(viewerId, target).label(),
                    "server", target.currentServer().orElse("-"),
                    "level", String.valueOf(target.level()),
                    "wins", stats.cached(target.uuid()).map(value -> String.valueOf(value.wins())).orElse("-"));
            Optional<Party> party = players.party(target.uuid());
            party.ifPresent(value -> texts.send(viewer, "whois-party",
                    "size", String.valueOf(value.size()),
                    "leader", players.displayName(viewerId, value.leader())));
            players.friendIds(target.uuid()).thenCombine(players.areFriends(viewerId, target.uuid()),
                    (friends, together) -> {
                        texts.send(viewer, "whois-friends", "count", String.valueOf(friends.size()),
                                "friends", String.valueOf(together));
                        return null;
                    });
        });
    }

    static UUID viewerId(CommandSender viewer) {
        return viewer instanceof Player player ? player.getUniqueId() : CONSOLE;
    }
}
