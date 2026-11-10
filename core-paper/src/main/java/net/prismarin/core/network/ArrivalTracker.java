package net.prismarin.core.network;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.prismarin.core.PrismarinCorePlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

@Singleton
public final class ArrivalTracker implements Listener {

    private static final long WAIT_TICKS = 15L;

    private final PrismarinCorePlugin plugin;
    private final Map<UUID, String> origins = new ConcurrentHashMap<>();

    @Inject
    public ArrivalTracker(PrismarinCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void arrived(UUID player, String from) {
        origins.put(player, from == null ? "" : from);
    }

    public Optional<String> previousServer(Player player) {
        return Optional.ofNullable(origins.get(player.getUniqueId())).filter(from -> !from.isEmpty());
    }

    public void whenKnown(Player player, Consumer<Optional<String>> action) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                action.accept(previousServer(player));
            }
        }, WAIT_TICKS);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        origins.remove(event.getPlayer().getUniqueId());
    }
}
