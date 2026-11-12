package net.prismarin.core.bridge;

import java.util.UUID;
import me.clip.placeholderapi.PlaceholderAPI;
import net.prismarin.core.stats.PlaceholderStats;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class PlaceholderApiStatResolver implements PlaceholderStats.Resolver {

    @Override
    public boolean available(String identifier) {
        return PlaceholderAPI.isRegistered(identifier);
    }

    @Override
    public String resolve(UUID player, String text) {
        Player online = Bukkit.getPlayer(player);
        return online == null ? null : PlaceholderAPI.setPlaceholders(online, text);
    }
}
