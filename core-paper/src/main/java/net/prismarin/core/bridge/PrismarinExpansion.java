package net.prismarin.core.bridge;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Relational;
import net.prismarin.core.nick.NickManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public final class PrismarinExpansion extends PlaceholderExpansion implements Relational {

    private final PrismarinPlaceholders placeholders;
    private final String version;

    public PrismarinExpansion(PrismarinPlaceholders placeholders, String version) {
        this.placeholders = placeholders;
        this.version = version;
    }

    @Override
    public String getIdentifier() {
        return "prismarin";
    }

    @Override
    public String getAuthor() {
        return "PRISMARIN";
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        return placeholders.resolve(player == null ? null : player.getUniqueId(), params);
    }

    @Override
    public String onPlaceholderRequest(Player viewer, Player target, String params) {
        if (viewer == null || target == null) {
            return null;
        }
        return placeholders.resolveRelational(viewer.getUniqueId(),
                viewer.hasPermission(NickManager.PERMISSION_REALNAME), target.getUniqueId(), params);
    }
}
