package net.prismarin.velocity.platform;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import java.time.Duration;
import java.util.Optional;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.admin.PlatformApi;
import net.prismarin.api.admin.PrismarinReloadedEvent;
import net.prismarin.api.admin.ReloadResult;
import net.prismarin.api.admin.UpdateAvailableEvent;
import net.prismarin.api.admin.UpdateInfo;
import net.prismarin.api.event.EventBus;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.shared.admin.ReloadRegistry;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.config.PlatformSettings;
import net.prismarin.shared.locale.YamlLocaleService;
import net.prismarin.shared.update.UpdateChecker;
import net.prismarin.velocity.locale.ProxyMessages;
import org.bstats.charts.SimplePie;
import org.bstats.velocity.Metrics;
import org.slf4j.Logger;

public final class ProxyPlatform {

    public static final String RELOAD_PERMISSION = "prismarin.admin.reload";
    public static final String UPDATE_PERMISSION = "prismarin.admin.update";
    static final int PLUGIN_ID = 35278;

    private final Object plugin;
    private final ProxyServer proxy;
    private final Logger logger;
    private final PlatformSettings settings;
    private final CoreConfig config;
    private final LocaleService locale;
    private final ProxyMessages messages;
    private final EventBus events;
    private final Metrics.Factory metricsFactory;
    private final ReloadRegistry reloads = new ReloadRegistry();
    private UpdateChecker updates;
    private ScheduledTask checkTask;

    public ProxyPlatform(Object plugin, ProxyServer proxy, Logger logger, PlatformSettings settings, CoreConfig config,
                         LocaleService locale, ProxyMessages messages, EventBus events,
                         Metrics.Factory metricsFactory) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.logger = logger;
        this.settings = settings;
        this.config = config;
        this.locale = locale;
        this.messages = messages;
        this.events = events;
        this.metricsFactory = metricsFactory;
    }

    public void start(String version) {
        if (locale instanceof YamlLocaleService yaml) {
            reloads.register("languages", yaml::reload);
        }
        updates = UpdateChecker.github(version, this::announce);
        PlatformApi.register(reloads, updates);
        proxy.getEventManager().register(plugin, this);
        if (settings.updateCheck()) {
            checkTask = proxy.getScheduler().buildTask(plugin, () -> updates.check())
                    .delay(Duration.ofSeconds(5))
                    .repeat(Duration.ofHours(settings.updateIntervalHours()))
                    .schedule();
        }
        if (settings.metrics() && PLUGIN_ID > 0 && metricsFactory != null) {
            Metrics metrics = metricsFactory.make(plugin, PLUGIN_ID);
            metrics.addCustomChart(new SimplePie("storage_mode", () -> config.storageMode().name()));
            metrics.addCustomChart(new SimplePie("redis", () -> config.redisEnabled() ? "on" : "off"));
            metrics.addCustomChart(new SimplePie("default_language", config::defaultLocale));
        }
    }

    public void stop() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
        PlatformApi.unregister();
    }

    public ReloadResult reload(String actor) {
        ReloadResult result = reloads.reloadAll();
        events.publish(new PrismarinReloadedEvent(result, actor));
        return result;
    }

    public ReloadRegistry reloads() {
        return reloads;
    }

    public Optional<UpdateInfo> available() {
        return updates == null ? Optional.empty() : updates.available();
    }

    private void announce(UpdateInfo update) {
        logger.info("PRISMARIN {} is available, this proxy runs {}: {}", update.latest(), update.current(),
                update.url());
        events.publish(new UpdateAvailableEvent(update));
    }

    @Subscribe
    public void onLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        if (!settings.notifyStaff() || !player.hasPermission(UPDATE_PERMISSION)) {
            return;
        }
        available().ifPresent(update -> proxy.getScheduler().buildTask(plugin, () -> {
            if (player.isActive()) {
                player.sendMessage(messages.render(player, "admin.update-available",
                                Placeholder.unparsed("latest", update.latest()),
                                Placeholder.unparsed("current", update.current()))
                        .clickEvent(ClickEvent.openUrl(update.url())));
            }
        }).delay(Duration.ofSeconds(3)).schedule());
    }
}
