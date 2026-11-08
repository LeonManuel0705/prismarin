package net.prismarin.core.bridge;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

public final class PrismarinExpansion extends PlaceholderExpansion {

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
}
