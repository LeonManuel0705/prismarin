package net.prismarin.lobby.player;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.prismarin.lobby.LobbyPlugin;
import org.bukkit.entity.Player;

@Singleton
public final class LobbyVisibility {

    private final LobbyPlugin plugin;
    private final Set<UUID> hiding = ConcurrentHashMap.newKeySet();
    private final Set<UUID> silent = ConcurrentHashMap.newKeySet();

    @Inject
    public LobbyVisibility(LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean hiding(UUID player) {
        return hiding.contains(player);
    }

    public boolean silent(UUID player) {
        return silent.contains(player);
    }

    public void hiding(Player player, boolean state) {
        if (state) {
            hiding.add(player.getUniqueId());
        } else {
            hiding.remove(player.getUniqueId());
        }
        refresh(player);
    }

    public void silent(Player player, boolean state) {
        if (state) {
            silent.add(player.getUniqueId());
        } else {
            silent.remove(player.getUniqueId());
        }
        refresh(player);
    }

    public void refresh(Player player) {
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (other.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            apply(player, other);
            apply(other, player);
        }
    }

    public void forget(UUID player) {
        hiding.remove(player);
        silent.remove(player);
    }

    public void forgetHiding(UUID player) {
        hiding.remove(player);
    }

    public boolean forgetSilent(UUID player) {
        return silent.remove(player);
    }

    private void apply(Player viewer, Player target) {
        if (VisibilityRule.visible(hiding(viewer.getUniqueId()), silent(viewer.getUniqueId()),
                silent(target.getUniqueId()))) {
            viewer.showPlayer(plugin, target);
        } else {
            viewer.hidePlayer(plugin, target);
        }
    }
}
