package net.prismarin.lobby.sidebar;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.brand.Branding;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.PlayerProfile;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.rank.RankFormatProvider;
import net.prismarin.core.scoreboard.AnimatedTitle;
import net.prismarin.core.scoreboard.SidebarProvider;
import net.prismarin.lobby.LobbyPlugin;
import net.prismarin.lobby.player.PlayerLocales;
import net.prismarin.lobby.util.XpProgressBar;
import org.bukkit.entity.Player;

@Singleton
public final class LobbySidebarProvider implements SidebarProvider {

    private static final long TITLE_FRAME_MILLIS = 500L;
    private static final int BAR_SEGMENTS = 8;
    private static final long BASE_XP_REQUIREMENT = 500L;

    private final LobbyPlugin plugin;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final RankFormatProvider ranks;
    private final MiniMessage miniMessage;
    private final AnimatedTitle title;
    private final String barColor;

    @Inject
    public LobbySidebarProvider(LobbyPlugin plugin, LocaleService locale, ProfileService profiles,
                                RankFormatProvider ranks, MiniMessage miniMessage, Branding branding) {
        this.plugin = plugin;
        this.locale = locale;
        this.profiles = profiles;
        this.ranks = ranks;
        this.miniMessage = miniMessage;
        this.title = AnimatedTitle.brand(miniMessage, branding);
        this.barColor = branding.primaryColor();
    }

    @Override
    public Component title(Player player) {
        return title.frame((int) (System.currentTimeMillis() / TITLE_FRAME_MILLIS % Integer.MAX_VALUE));
    }

    @Override
    public List<Component> lines(Player player) {
        Locale playerLocale = PlayerLocales.of(player, profiles);
        Optional<PlayerProfile> profile = profiles.getCached(player.getUniqueId());
        long coins = profile.map(value -> value.balance(Currency.COINS)).orElse(0L);
        long gems = profile.map(value -> value.balance(Currency.GEMS)).orElse(0L);
        int level = profile.map(PlayerProfile::getLevel).orElse(0);
        long xp = profile.map(PlayerProfile::getXp).orElse(0L);
        long max = profile.map(PlayerProfile::xpForNextLevel).orElse(BASE_XP_REQUIREMENT);
        Component bar = miniMessage.deserialize(XpProgressBar.render(xp, max, BAR_SEGMENTS, barColor));
        return List.of(
                Component.empty(),
                locale.render("lobby.sidebar.rank-label", playerLocale),
                locale.render("lobby.sidebar.rank", playerLocale,
                        Placeholder.component("rank", ranks.prefix(player))),
                Component.empty(),
                locale.render("lobby.sidebar.coins", playerLocale,
                        Placeholder.unparsed("coins", String.valueOf(coins))),
                locale.render("lobby.sidebar.gems", playerLocale,
                        Placeholder.unparsed("gems", String.valueOf(gems))),
                Component.empty(),
                locale.render("lobby.sidebar.level", playerLocale,
                        Placeholder.unparsed("level", String.valueOf(level))),
                locale.render("lobby.sidebar.progress", playerLocale,
                        Placeholder.component("bar", bar),
                        Placeholder.unparsed("xp", String.valueOf(xp)),
                        Placeholder.unparsed("max", String.valueOf(max))),
                Component.empty(),
                locale.render("lobby.sidebar.online", playerLocale,
                        Placeholder.unparsed("online", String.valueOf(plugin.getServer().getOnlinePlayers().size()))),
                Component.empty(),
                locale.render("lobby.sidebar.footer", playerLocale));
    }
}
