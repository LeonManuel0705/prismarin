package net.prismarin.lobby.profile;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.event.Subscription;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.reward.DailyStatus;
import net.prismarin.api.stats.PlayerStats;
import net.prismarin.api.stats.StatDefinition;
import net.prismarin.core.CoreModules;
import net.prismarin.core.ModuleState;
import net.prismarin.core.gui.ClickContext;
import net.prismarin.core.gui.ItemBuilder;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.core.reward.PaperDailyRewards;
import net.prismarin.core.reward.RewardSummary;
import net.prismarin.core.stats.PaperStats;
import net.prismarin.core.stats.StatTexts;
import net.prismarin.core.stats.StatsSettings;
import net.prismarin.core.stats.StatsView;
import net.prismarin.lobby.api.LobbyMenuEntry;
import net.prismarin.lobby.extension.LobbyExtensions;
import net.prismarin.lobby.menu.LobbyMenus;
import net.prismarin.lobby.menu.MenuKit;
import net.prismarin.shared.stats.DocumentStatsService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@Singleton
public final class ProfileEntries {

    public static final String STATS_ID = "prismarin-stats";
    public static final String DAILY_ID = "prismarin-daily";
    private static final int STATS_ORDER = -20;
    private static final int DAILY_ORDER = -10;

    private final LobbyExtensions extensions;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final ModuleState modules;
    private final Injector injector;
    private final Provider<LobbyMenus> menus;
    private final MenuKit kit;
    private final List<Subscription> registered = new ArrayList<>();

    @Inject
    public ProfileEntries(LobbyExtensions extensions, LocaleService locale, ProfileService profiles,
                          ModuleState modules, Injector injector, Provider<LobbyMenus> menus, MenuKit kit) {
        this.extensions = extensions;
        this.locale = locale;
        this.profiles = profiles;
        this.modules = modules;
        this.injector = injector;
        this.menus = menus;
        this.kit = kit;
    }

    public void register() {
        if (modules.enabled(CoreModules.STATS) && injector.getInstance(StatsSettings.class).profileEntry()) {
            PaperStats stats = injector.getInstance(PaperStats.class);
            if (stats.service().isPresent()) {
                registered.add(extensions.profile().register(stats(stats.service().get())));
            }
        }
        if (modules.enabled(CoreModules.DAILY_REWARDS)) {
            PaperDailyRewards daily = injector.getInstance(PaperDailyRewards.class);
            if (daily.settings().profileEntry()) {
                registered.add(extensions.profile().register(daily(daily)));
            }
        }
    }

    public void unregister() {
        registered.forEach(Subscription::unsubscribe);
        registered.clear();
    }

    private LobbyMenuEntry stats(DocumentStatsService service) {
        StatsSettings settings = injector.getInstance(StatsSettings.class);
        StatTexts texts = injector.getInstance(StatTexts.class);
        return entry(STATS_ID, STATS_ORDER, viewer -> {
            Locale target = PlayerLocales.of(viewer, profiles);
            PlayerStats values = service.cached(viewer.getUniqueId())
                    .orElseGet(() -> PlayerStats.empty(viewer.getUniqueId(), viewer.getName()));
            List<Component> lore = new ArrayList<>();
            List<StatDefinition> shown = StatsView.profile(values, service.definitions(), settings.profileLimit());
            for (StatDefinition definition : shown) {
                lore.add(locale.render("stats.menu.line", target,
                        Placeholder.component("stat", texts.name(definition, target)),
                        Placeholder.unparsed("value", service.format(definition.key(), values.value(definition.key()),
                                target))));
            }
            if (shown.isEmpty()) {
                lore.add(locale.render("stats.menu.empty", target));
            }
            lore.add(Component.empty());
            lore.add(locale.render("stats.menu.click", target));
            return ItemBuilder.of(Material.BOOK).name(locale.render("stats.menu.name", target)).lore(lore).build();
        }, context -> {
            kit.closeLater(context.player());
            context.player().performCommand("stats");
        });
    }

    private LobbyMenuEntry daily(PaperDailyRewards daily) {
        RewardSummary summary = injector.getInstance(RewardSummary.class);
        return entry(DAILY_ID, DAILY_ORDER, viewer -> {
            Locale target = PlayerLocales.of(viewer, profiles);
            Optional<DailyStatus> status = daily.cached(viewer.getUniqueId());
            boolean ready = status.map(DailyStatus::claimable).orElse(false);
            List<Component> lore = new ArrayList<>();
            status.ifPresent(found -> {
                lore.add(found.claimable() ? locale.render("daily.menu.ready", target)
                        : locale.render("daily.menu.claimed", target,
                        Placeholder.component("time", daily.remaining(found, target))));
                lore.add(locale.render("daily.menu.streak", target,
                        Placeholder.unparsed("streak", Integer.toString(found.streak())),
                        Placeholder.unparsed("best", Integer.toString(found.bestStreak()))));
                lore.add(Component.empty());
                int day = daily.settings().schedule().day(found.nextStreak());
                lore.add(locale.render("daily.menu.next", target, Placeholder.unparsed("day", Integer.toString(day)),
                        Placeholder.unparsed("cycle", Integer.toString(daily.cycle()))));
                lore.addAll(summary.lines(daily.reward(found.nextStreak()), target));
            });
            if (ready) {
                lore.add(Component.empty());
                lore.add(locale.render("daily.menu.click", target));
            }
            return ItemBuilder.of(ready ? Material.CHEST : Material.ENDER_CHEST)
                    .name(locale.render("daily.menu.name", target))
                    .lore(lore)
                    .glow(ready)
                    .build();
        }, context -> {
            Player player = context.player();
            if (daily.cached(player.getUniqueId()).map(DailyStatus::claimable).orElse(true)) {
                daily.claimAndTell(player, () -> {
                    if (player.isOnline()) {
                        menus.get().openProfile(player);
                    }
                });
            } else {
                daily.claimAndTell(player, null);
            }
        });
    }

    private static LobbyMenuEntry entry(String id, int order, Function<Player, ItemStack> icon,
                                        Consumer<ClickContext> click) {
        return new LobbyMenuEntry() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public ItemStack icon(Player viewer) {
                return icon.apply(viewer);
            }

            @Override
            public void click(ClickContext context) {
                click.accept(context);
            }

            @Override
            public int order() {
                return order;
            }
        };
    }
}
