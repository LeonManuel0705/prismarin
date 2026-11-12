package net.prismarin.core.platform;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.Prismarin;
import net.prismarin.api.admin.PlatformApi;
import net.prismarin.api.admin.PrismarinReloadedEvent;
import net.prismarin.api.admin.ReloadResult;
import net.prismarin.api.admin.UpdateAvailableEvent;
import net.prismarin.api.admin.UpdateInfo;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.config.RankDisplayHolder;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.shared.admin.ReloadRegistry;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.config.PlatformSettings;
import net.prismarin.shared.locale.YamlLocaleService;
import net.prismarin.shared.update.UpdateChecker;
import org.bstats.bukkit.Metrics;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

@Singleton
public final class PlatformFeatures implements Listener {

    public static final String RELOAD_PERMISSION = "prismarin.admin.reload";
    public static final String UPDATE_PERMISSION = "prismarin.admin.update";

    private final PrismarinCorePlugin plugin;
    private final PlatformSettings settings;
    private final CoreConfig config;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final RankDisplayHolder rankDisplay;
    private final Prismarin prismarin;
    private final ReloadRegistry reloads = new ReloadRegistry();
    private UpdateChecker updates;
    private ScheduledExecutorService scheduler;
    private Metrics metrics;

    @Inject
    public PlatformFeatures(PrismarinCorePlugin plugin, PlatformSettings settings, CoreConfig config,
                            LocaleService locale, ProfileService profiles, RankDisplayHolder rankDisplay,
                            Prismarin prismarin) {
        this.plugin = plugin;
        this.settings = settings;
        this.config = config;
        this.locale = locale;
        this.profiles = profiles;
        this.rankDisplay = rankDisplay;
        this.prismarin = prismarin;
    }

    public void start() {
        if (locale instanceof YamlLocaleService yaml) {
            reloads.register("languages", yaml::reload);
        }
        reloads.register("rank display", rankDisplay::reload);
        String version = plugin.getPluginMeta().getVersion();
        updates = UpdateChecker.github(version, this::announce);
        PlatformApi.register(reloads, updates);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        if (settings.updateCheck()) {
            scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
            scheduler.scheduleAtFixedRate(() -> updates.check(), 5, settings.updateIntervalHours() * 3600L,
                    TimeUnit.SECONDS);
        }
        if (settings.metrics()) {
            metrics = PaperMetrics.start(plugin, config);
        }
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (metrics != null) {
            metrics.shutdown();
            metrics = null;
        }
        PlatformApi.unregister();
    }

    public ReloadResult reload(String actor) {
        ReloadResult result = reloads.reloadAll();
        prismarin.events().publish(new PrismarinReloadedEvent(result, actor));
        return result;
    }

    public Optional<UpdateInfo> available() {
        return updates == null ? Optional.empty() : updates.available();
    }

    private void announce(UpdateInfo update) {
        plugin.getLogger().info("PRISMARIN " + update.latest() + " is available, this server runs "
                + update.current() + ": " + update.url());
        prismarin.events().publish(new UpdateAvailableEvent(update));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!settings.notifyStaff() || !player.hasPermission(UPDATE_PERMISSION)) {
            return;
        }
        available().ifPresent(update -> plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.sendMessage(notice(player, update));
            }
        }, 60L));
    }

    private Component notice(Player player, UpdateInfo update) {
        Locale target = PlayerLocales.of(player, profiles);
        return locale.render("admin.update-available", target,
                        Placeholder.unparsed("latest", update.latest()),
                        Placeholder.unparsed("current", update.current()))
                .clickEvent(ClickEvent.openUrl(update.url()));
    }
}
