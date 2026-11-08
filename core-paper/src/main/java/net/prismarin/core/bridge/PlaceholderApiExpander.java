package net.prismarin.core.bridge;

import java.util.UUID;
import me.clip.placeholderapi.PlaceholderAPI;
import net.prismarin.shared.text.PlaceholderExpander;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public final class PlaceholderApiExpander implements PlaceholderExpander {

    @Override
    public String expand(UUID viewer, String text) {
        Player online = Bukkit.getPlayer(viewer);
        OfflinePlayer player = online != null ? online : Bukkit.getOfflinePlayer(viewer);
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
