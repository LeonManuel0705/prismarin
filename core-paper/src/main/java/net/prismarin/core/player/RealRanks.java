package net.prismarin.core.player;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.player.RankDisplay;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.config.RankDisplayHolder;
import net.prismarin.core.permission.PermissionSnapshots;
import net.prismarin.core.rank.DefaultRankFormatProvider;
import net.prismarin.core.rank.RankDisplayProvider;
import net.prismarin.core.rank.RankFormatProvider;
import net.prismarin.shared.platform.CoreServices;
import org.bukkit.entity.Player;

@Singleton
public final class RealRanks {

    private final RankFormatProvider real;

    @Inject
    public RealRanks(PrismarinCorePlugin plugin, LocaleService locale, CoreServices services,
                     PermissionSnapshots snapshots, ProfileService profiles, RankDisplayHolder display) {
        DefaultRankFormatProvider fallback = new DefaultRankFormatProvider(locale, snapshots, profiles);
        RankFormatProvider group = plugin.getServer().getPluginManager().getPlugin("LuckPerms") != null
                ? new LuckPermsGroupRank(services.formatter(), fallback)
                : fallback;
        this.real = new RankDisplayProvider(group, display, services.formatter());
    }

    public RankDisplay display(Player player) {
        return display(real, player);
    }

    public static RankDisplay display(RankFormatProvider provider, Player player) {
        return RankDisplays.of(provider.label(player), provider.prefix(player), provider.suffix(player),
                provider.nameColor(player), provider.chatColor(player), provider.weight(player));
    }
}
