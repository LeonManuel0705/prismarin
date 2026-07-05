package net.prismarin.core.rank;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.prismarin.api.locale.LocaleService;
import org.bukkit.entity.Player;

public final class DefaultRankFormatProvider implements RankFormatProvider {

    private static final String ADMIN_PERMISSION = "prismarin.rank.admin";
    private static final int ADMIN_WEIGHT = 100;

    private final LocaleService locale;

    public DefaultRankFormatProvider(LocaleService locale) {
        this.locale = locale;
    }

    @Override
    public Component prefix(Player player) {
        String key = isAdmin(player) ? "rank.admin-prefix" : "rank.default-prefix";
        return locale.render(key, player.locale());
    }

    @Override
    public int weight(Player player) {
        return isAdmin(player) ? ADMIN_WEIGHT : 0;
    }

    @Override
    public NamedTextColor nameColor(Player player) {
        return isAdmin(player) ? NamedTextColor.RED : NamedTextColor.GRAY;
    }

    private static boolean isAdmin(Player player) {
        return player.hasPermission(ADMIN_PERMISSION);
    }
}
