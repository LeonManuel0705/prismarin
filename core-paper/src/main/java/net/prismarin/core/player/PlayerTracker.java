package net.prismarin.core.player;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.prismarin.api.event.EventBus;
import net.prismarin.api.event.Subscription;
import net.prismarin.api.nick.NickChangedEvent;
import net.prismarin.api.player.LocaleChangedEvent;
import net.prismarin.api.player.ServerChangedEvent;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.core.moderation.ModerationChannel;
import net.prismarin.core.network.ArrivalTracker;
import net.prismarin.core.nick.NickRegistry;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.player.PlayerWire;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

@Singleton
public final class PlayerTracker implements Listener {

    private static final long STATE_DELAY_TICKS = 20L;

    private final PrismarinCorePlugin plugin;
    private final CoreConfig config;
    private final ProfileService profiles;
    private final EventBus events;
    private final ArrivalTracker arrivals;
    private final NickRegistry nicks;
    private final ProxyPlayerQueries queries;
    private final ModerationChannel channel;
    private final Map<UUID, Locale> locales = new ConcurrentHashMap<>();
    private volatile Subscription nickChanges;

    @Inject
    public PlayerTracker(PrismarinCorePlugin plugin, CoreConfig config, ProfileService profiles, EventBus events,
                         ArrivalTracker arrivals, NickRegistry nicks, ProxyPlayerQueries queries,
                         ModerationChannel channel) {
        this.plugin = plugin;
        this.config = config;
        this.profiles = profiles;
        this.events = events;
        this.arrivals = arrivals;
        this.nicks = nicks;
        this.queries = queries;
        this.channel = channel;
    }

    public void start() {
        channel.handlePlayerAnswers(queries::answer);
        channel.onLocaleReport(this::localeReported);
        nickChanges = events.subscribe(NickChangedEvent.class, event -> reportSoon(event.player()));
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            locales.put(player.getUniqueId(), PlayerLocales.of(player, profiles));
        }
    }

    public void stop() {
        Subscription subscription = nickChanges;
        if (subscription != null) {
            subscription.unsubscribe();
            nickChanges = null;
        }
        channel.handlePlayerAnswers(null);
        channel.onLocaleReport(null);
        locales.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        locales.put(uuid, PlayerLocales.of(player, profiles));
        arrivals.whenKnown(player, previous -> events.publish(
                new ServerChangedEvent(uuid, previous.orElse(""), config.serverId())));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                report(player);
            }
        }, STATE_DELAY_TICKS);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        report(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        locales.remove(uuid);
        queries.forget(uuid);
    }

    private void localeReported(Player player, Locale locale) {
        UUID uuid = player.getUniqueId();
        Locale previous = locales.put(uuid, locale);
        if (previous != null && !previous.equals(locale)) {
            events.publish(new LocaleChangedEvent(uuid, previous, locale));
        }
    }

    private void reportSoon(UUID uuid) {
        if (!plugin.isEnabled()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                report(player);
            }
        });
    }

    private void report(Player player) {
        String nick = nicks.shown(player.getUniqueId()).map(NickRegistry.Shown::nick).orElse("");
        channel.sendSocial(player, new PlayerWire.State(player.getWorld().getName(), nick).toJson(),
                PlayerWire.STATE);
    }
}
