package net.prismarin.lobby.sidebar;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.PrismarinSystems;
import net.prismarin.api.brand.Branding;
import net.prismarin.api.economy.Currency;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.PlayerProfile;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.core.rank.RankFormatProvider;
import net.prismarin.core.scoreboard.AnimatedTitle;
import net.prismarin.core.scoreboard.SidebarProvider;
import net.prismarin.core.vanish.VanishService;
import net.prismarin.lobby.LobbyPlugin;
import net.prismarin.lobby.util.CurrencyDisplay;
import net.prismarin.lobby.util.XpProgressBar;
import net.prismarin.shared.config.CoreConfig;
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
    private final CurrencyDisplay currencies;
    private final boolean levels;
    private final VanishService vanish;

    @Inject
    public LobbySidebarProvider(LobbyPlugin plugin, LocaleService locale, ProfileService profiles,
                                RankFormatProvider ranks, MiniMessage miniMessage, Branding branding,
                                CurrencyDisplay currencies, CoreConfig config, VanishService vanish) {
        this.plugin = plugin;
        this.locale = locale;
        this.profiles = profiles;
        this.ranks = ranks;
        this.miniMessage = miniMessage;
        this.title = AnimatedTitle.brand(miniMessage, branding);
        this.barColor = branding.primaryColor();
        this.currencies = currencies;
        this.levels = config.systemEnabled(PrismarinSystems.LEVELS);
        this.vanish = vanish;
    }

    @Override
    public Component title(Player player) {
        return title.frame((int) (System.currentTimeMillis() / TITLE_FRAME_MILLIS % Integer.MAX_VALUE));
    }

    @Override
    public List<Component> lines(Player player) {
        Locale playerLocale = PlayerLocales.of(player, profiles);
        Optional<PlayerProfile> profile = profiles.getCached(player.getUniqueId());
        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());
        lines.add(locale.render("lobby.sidebar.rank-label", playerLocale, player.getUniqueId()));
        lines.add(locale.render("lobby.sidebar.rank", playerLocale, player.getUniqueId(),
                Placeholder.component("rank", ranks.prefix(player))));
        List<Currency> shown = currencies.shown();
        if (!shown.isEmpty()) {
            lines.add(Component.empty());
            for (Currency currency : shown) {
                long amount = profile.map(value -> value.balance(currency)).orElse(0L);
                lines.add(currencies.render("lobby.sidebar", currency, amount, playerLocale));
            }
        }
        if (levels) {
            int level = profile.map(PlayerProfile::getLevel).orElse(0);
            long xp = profile.map(PlayerProfile::getXp).orElse(0L);
            long max = profile.map(PlayerProfile::xpForNextLevel).orElse(BASE_XP_REQUIREMENT);
            Component bar = miniMessage.deserialize(XpProgressBar.render(xp, max, BAR_SEGMENTS, barColor));
            lines.add(Component.empty());
            lines.add(locale.render("lobby.sidebar.level", playerLocale, player.getUniqueId(),
                    Placeholder.unparsed("level", String.valueOf(level))));
            lines.add(locale.render("lobby.sidebar.progress", playerLocale, player.getUniqueId(),
                    Placeholder.component("bar", bar),
                    Placeholder.unparsed("xp", String.valueOf(xp)),
                    Placeholder.unparsed("max", String.valueOf(max))));
        }
        lines.add(Component.empty());
        lines.add(locale.render("lobby.sidebar.online", playerLocale, player.getUniqueId(),
                Placeholder.unparsed("online", String.valueOf(vanish.visibleCount(player)))));
        lines.add(Component.empty());
        lines.add(locale.render("lobby.sidebar.footer", playerLocale, player.getUniqueId()));
        return lines;
    }
}
