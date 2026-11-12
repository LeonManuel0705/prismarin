package net.prismarin.core.reward;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.event.EventBus;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.PlayerProfile;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.reward.DailyClaim;
import net.prismarin.api.reward.DailyRewardClaimedEvent;
import net.prismarin.api.reward.DailyRewardService;
import net.prismarin.api.reward.DailyStatus;
import net.prismarin.api.reward.DailyStreakBrokenEvent;
import net.prismarin.api.reward.Reward;
import net.prismarin.api.reward.RewardContext;
import net.prismarin.api.stats.StatsApi;
import net.prismarin.core.CoreModules;
import net.prismarin.core.ModuleState;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.core.network.ArrivalTracker;
import net.prismarin.core.stats.StatsSettings;
import net.prismarin.shared.platform.CoreServices;
import net.prismarin.shared.reward.DailyLedger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

@Singleton
public final class PaperDailyRewards implements DailyRewardService, Listener {

    public static final String COMMAND = "/daily";

    private final PrismarinCorePlugin plugin;
    private final CoreServices services;
    private final ExecutorService executor;
    private final DailyRewardSettings settings;
    private final RewardGranter granter;
    private final RewardSummary summary;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final EventBus events;
    private final ModuleState modules;
    private final Provider<StatsSettings> statsSettings;
    private final ArrivalTracker arrivals;
    private final Map<UUID, DailyStatus> cache = new ConcurrentHashMap<>();
    private volatile DailyLedger ledger;

    @Inject
    public PaperDailyRewards(PrismarinCorePlugin plugin, CoreServices services, ExecutorService executor,
                             DailyRewardSettings settings, RewardGranter granter, RewardSummary summary,
                             LocaleService locale, ProfileService profiles, EventBus events, ModuleState modules,
                             Provider<StatsSettings> statsSettings, ArrivalTracker arrivals) {
        this.plugin = plugin;
        this.services = services;
        this.executor = executor;
        this.settings = settings;
        this.granter = granter;
        this.summary = summary;
        this.locale = locale;
        this.profiles = profiles;
        this.events = events;
        this.modules = modules;
        this.statsSettings = statsSettings;
        this.arrivals = arrivals;
    }

    public void start() {
        settings.problems().forEach(problem -> plugin.getLogger().warning(DailyRewardSettings.FILE_NAME + ": "
                + problem));
        ledger = new DailyLedger(services.documents(), Clock.systemUTC(), settings.clock(), settings.graceDays());
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getOnlinePlayers().forEach(player -> status(player.getUniqueId()));
    }

    public void stop() {
        HandlerList.unregisterAll(this);
        cache.clear();
    }

    public DailyRewardSettings settings() {
        return settings;
    }

    @Override
    public CompletableFuture<DailyStatus> status(UUID player) {
        DailyLedger current = ledger();
        return CompletableFuture.supplyAsync(() -> {
            DailyStatus status = current.status(player, current.state(player));
            if (plugin.getServer().getPlayer(player) != null) {
                cache.put(player, status);
            }
            return status;
        }, executor);
    }

    @Override
    public Optional<DailyStatus> cached(UUID player) {
        DailyStatus status = player == null ? null : cache.get(player);
        if (status != null && status.nextReset() <= System.currentTimeMillis()) {
            status(player);
        }
        return Optional.ofNullable(status);
    }

    @Override
    public CompletableFuture<DailyClaim> claim(UUID player) {
        Player online = player == null ? null : plugin.getServer().getPlayer(player);
        if (online == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("the player is not on this server"));
        }
        DailyLedger current = ledger();
        String name = profiles.getCached(player).map(PlayerProfile::getName).orElse(online.getName());
        return CompletableFuture.supplyAsync(() -> current.claim(player, name), executor).thenCompose(outcome -> {
            cache.put(player, outcome.status());
            if (!outcome.claimed()) {
                return CompletableFuture.completedFuture(new DailyClaim(DailyClaim.Result.ALREADY_CLAIMED,
                        outcome.status(), Reward.EMPTY, settings.schedule().day(outcome.status().nextStreak()), false,
                        0));
            }
            int streak = outcome.after().streak();
            DailyClaim claim = new DailyClaim(DailyClaim.Result.CLAIMED, outcome.status(),
                    settings.schedule().reward(streak), settings.schedule().day(streak),
                    settings.schedule().milestone(streak), outcome.step().lost());
            if (claim.streakBroken()) {
                events.publish(new DailyStreakBrokenEvent(player, claim.lostStreak(), outcome.step().missed()));
            }
            countStats(player, streak);
            RewardContext context = new RewardContext(RewardContext.DAILY, player, name, streak,
                    Integer.toString(claim.day()));
            return granter.give(player, claim.reward(), context, Map.of("day", Integer.toString(claim.day())))
                    .thenApply(done -> {
                        events.publish(new DailyRewardClaimedEvent(player, claim));
                        return claim;
                    });
        });
    }

    @Override
    public Reward reward(int streak) {
        return settings.schedule().reward(streak);
    }

    @Override
    public int cycle() {
        return settings.schedule().cycle();
    }

    public void claimAndTell(Player player, Runnable after) {
        claim(player.getUniqueId()).whenComplete((claim, error) -> plugin.getServer().getScheduler().runTask(plugin,
                () -> {
                    if (player.isOnline()) {
                        tell(player, claim, error);
                    }
                    if (after != null) {
                        after.run();
                    }
                }));
    }

    public Component remaining(DailyStatus status, Locale language) {
        RemainingTime.Parts parts = RemainingTime.of(status.nextReset() - System.currentTimeMillis());
        if (parts.hours() > 0L) {
            return locale.render("daily.time.hours", language,
                    Placeholder.unparsed("hours", Long.toString(parts.hours())),
                    Placeholder.unparsed("minutes", Long.toString(parts.minutes())));
        }
        return locale.render("daily.time.minutes", language,
                Placeholder.unparsed("minutes", Long.toString(parts.minutes())));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        status(player.getUniqueId()).thenAccept(status -> {
            if (status.claimable() && settings.remindOnJoin()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> arrivals.whenKnown(player, previous -> {
                    if (previous.isEmpty()) {
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> remind(player),
                                Math.max(1L, settings.reminderDelaySeconds() * 20L));
                    }
                }));
            }
        }).exceptionally(error -> {
            plugin.getLogger().log(Level.WARNING, "Could not load the daily reward of " + player.getName(), error);
            return null;
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
    }

    private void remind(Player player) {
        DailyStatus status = cache.get(player.getUniqueId());
        if (!player.isOnline() || status == null || !status.claimable()) {
            return;
        }
        Locale language = PlayerLocales.of(player, profiles);
        player.sendMessage(locale.render("daily.reminder", language,
                        Placeholder.unparsed("streak", Integer.toString(status.nextStreak())))
                .clickEvent(ClickEvent.runCommand(COMMAND))
                .hoverEvent(HoverEvent.showText(locale.render("daily.reminder-hover", language))));
    }

    private void tell(Player player, DailyClaim claim, Throwable error) {
        Locale language = PlayerLocales.of(player, profiles);
        if (error != null || claim == null) {
            plugin.getLogger().log(Level.WARNING, "The daily reward of " + player.getName() + " failed", error);
            player.sendMessage(locale.render("daily.failed", language));
            return;
        }
        if (!claim.claimed()) {
            player.sendMessage(locale.render("daily.already", language,
                    Placeholder.component("time", remaining(claim.status(), language)),
                    Placeholder.unparsed("streak", Integer.toString(claim.status().streak()))));
            return;
        }
        List<Component> lines = new ArrayList<>();
        if (claim.streakBroken()) {
            lines.add(locale.render("daily.streak-broken", language,
                    Placeholder.unparsed("streak", Integer.toString(claim.lostStreak()))));
        }
        lines.add(locale.render("daily.claimed", language,
                Placeholder.unparsed("streak", Integer.toString(claim.status().streak())),
                Placeholder.unparsed("day", Integer.toString(claim.day())),
                Placeholder.unparsed("cycle", Integer.toString(cycle()))));
        lines.addAll(summary.lines(claim.reward(), language));
        if (claim.milestone()) {
            lines.add(locale.render("daily.milestone", language,
                    Placeholder.unparsed("streak", Integer.toString(claim.status().streak()))));
        }
        player.sendMessage(Component.join(JoinConfiguration.newlines(), lines));
    }

    private void countStats(UUID player, int streak) {
        if (!modules.enabled(CoreModules.STATS)) {
            return;
        }
        StatsApi.service().ifPresent(stats -> {
            StatsSettings.BuiltIn keys = statsSettings.get().builtIn();
            keys.claimsKey().ifPresent(key -> stats.increment(player, key, 1L).exceptionally(error -> null));
            keys.streakKey().ifPresent(key -> stats.record(player, key, streak).exceptionally(error -> null));
        });
    }

    private DailyLedger ledger() {
        DailyLedger current = ledger;
        if (current == null) {
            throw new IllegalStateException("daily rewards are not started");
        }
        return current;
    }
}
