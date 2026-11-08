package net.prismarin.core.rank;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.core.permission.CorePermissions;
import net.prismarin.core.permission.PermissionSnapshots;
import org.bukkit.entity.Player;

public final class DefaultRankFormatProvider implements RankFormatProvider {

    private static final int ADMIN_WEIGHT = 100;

    private final LocaleService locale;
    private final PermissionSnapshots snapshots;
    private final ProfileService profiles;

    public DefaultRankFormatProvider(LocaleService locale, PermissionSnapshots snapshots, ProfileService profiles) {
        this.locale = locale;
        this.snapshots = snapshots;
        this.profiles = profiles;
    }

    @Override
    public Component prefix(Player player) {
        String key = isAdmin(player) ? "rank.admin-prefix" : "rank.default-prefix";
        return locale.render(key, PlayerLocales.of(player, profiles));
    }

    @Override
    public int weight(Player player) {
        return isAdmin(player) ? ADMIN_WEIGHT : 0;
    }

    @Override
    public NamedTextColor nameColor(Player player) {
        return isAdmin(player) ? NamedTextColor.RED : NamedTextColor.GRAY;
    }

    private boolean isAdmin(Player player) {
        return snapshots.has(player, CorePermissions.RANK_ADMIN);
    }
}
