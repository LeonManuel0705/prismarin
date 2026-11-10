package net.prismarin.core.essentials;

import com.google.inject.Singleton;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

@Singleton
public final class FlightGrants implements Listener {

    private final Set<UUID> granted = ConcurrentHashMap.newKeySet();

    public boolean granted(Player player) {
        return granted.contains(player.getUniqueId());
    }

    boolean toggle(Player player) {
        UUID id = player.getUniqueId();
        if (granted.remove(id)) {
            return false;
        }
        granted.add(id);
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        granted.remove(event.getPlayer().getUniqueId());
    }
}
