package net.prismarin.lobby.player;

import com.google.inject.Singleton;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

@Singleton
public final class BuildModeService {

    public static final String PERMISSION = "prismarin.lobby.build";

    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();

    public boolean toggle(Player player) {
        UUID id = player.getUniqueId();
        if (enabled.remove(id)) {
            return false;
        }
        enabled.add(id);
        return true;
    }

    public boolean active(Player player) {
        return enabled.contains(player.getUniqueId()) && player.hasPermission(PERMISSION);
    }

    public void clear(UUID id) {
        enabled.remove(id);
    }
}
