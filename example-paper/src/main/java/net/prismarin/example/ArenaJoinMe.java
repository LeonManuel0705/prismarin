package net.prismarin.example;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.Prismarin;
import net.prismarin.api.event.Subscription;
import net.prismarin.api.joinme.JoinMeArrival;
import net.prismarin.api.joinme.JoinMeArrivedEvent;
import net.prismarin.api.joinme.JoinMeHandler;
import net.prismarin.api.joinme.JoinMeOutcome;
import net.prismarin.api.joinme.JoinMeRequest;
import net.prismarin.api.joinme.JoinMeResult;
import net.prismarin.api.joinme.JoinMeScope;
import net.prismarin.api.joinme.JoinMeSpot;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class ArenaJoinMe {

    static final String HANDLER = "example-arena";
    private static final String ARENA = "arena";
    private static final int ARENA_SIZE = 8;

    private final Plugin plugin;
    private final Prismarin prismarin;
    private final Texts texts;
    private final List<Subscription> subscriptions = new ArrayList<>();

    ArenaJoinMe(Plugin plugin, Prismarin prismarin, Texts texts) {
        this.plugin = plugin;
        this.prismarin = prismarin;
        this.texts = texts;
    }

    void register() {
        prismarin.joinMe().ifPresent(joinMe -> subscriptions.add(joinMe.registerHandler(
                JoinMeScope.when(spot -> spot.world().toLowerCase(Locale.ROOT).startsWith(ARENA)), new Handler())));
        subscriptions.add(prismarin.events().subscribe(JoinMeArrivedEvent.class, event ->
                plugin.getLogger().info(event.player() + " followed the JoinMe of " + event.joinMe().creatorName()
                        + " (" + event.placement().id() + ")")));
    }

    void unregister() {
        subscriptions.forEach(Subscription::unsubscribe);
        subscriptions.clear();
    }

    CompletableFuture<JoinMeResult> invite(Player player) {
        return prismarin.joinMe()
                .map(joinMe -> joinMe.create(JoinMeRequest.of(player.getUniqueId())
                        .withMaxJoins(ARENA_SIZE - player.getWorld().getPlayers().size())
                        .withPlaceholder("map", mapOf(player.getWorld().getName()))))
                .orElseGet(() -> CompletableFuture.completedFuture(JoinMeResult.of(JoinMeResult.Status.DISABLED)));
    }

    private static String mapOf(String world) {
        String name = world.toLowerCase(Locale.ROOT).startsWith(ARENA + "-") ? world.substring(ARENA.length() + 1)
                : world;
        return name.isEmpty() ? world : name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    private final class Handler implements JoinMeHandler {

        @Override
        public String id() {
            return HANDLER;
        }

        @Override
        public int priority() {
            return 10;
        }

        @Override
        public boolean allows(JoinMeSpot spot) {
            return spot.state().joinable();
        }

        @Override
        public Map<String, String> placeholders(JoinMeSpot spot) {
            return Map.of("map", mapOf(spot.world()), "mode", "2x4");
        }

        @Override
        public JoinMeOutcome arrive(JoinMeArrival arrival) {
            Player player = plugin.getServer().getPlayer(arrival.player());
            Player creator = plugin.getServer().getPlayer(arrival.creator());
            if (player == null || creator == null || !creator.getWorld().getName().toLowerCase(Locale.ROOT)
                    .startsWith(ARENA)) {
                return JoinMeOutcome.FALLBACK;
            }
            if (creator.getWorld().getPlayers().size() >= ARENA_SIZE) {
                return JoinMeOutcome.REFUSED;
            }
            player.teleportAsync(creator.getLocation());
            texts.send(player, "joinme-arena", "name", prismarin.players().displayName(player.getUniqueId(),
                    creator.getUniqueId()), "map", mapOf(creator.getWorld().getName()));
            return JoinMeOutcome.HANDLED;
        }
    }
}
