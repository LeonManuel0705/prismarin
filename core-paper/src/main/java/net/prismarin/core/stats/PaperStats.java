package net.prismarin.core.stats;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import net.prismarin.api.player.NetworkPlayer;
import net.prismarin.api.player.PlayerService;
import net.prismarin.api.profile.PlayerProfile;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.stats.PlayerStats;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.bridge.PlaceholderApiStatResolver;
import net.prismarin.shared.network.NetworkCalls;
import net.prismarin.shared.platform.CoreServices;
import net.prismarin.shared.stats.DocumentStatsService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

@Singleton
public final class PaperStats implements Listener {

    private static final long PROBLEM_PAUSE_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private static final long CATALOG_MILLIS = TimeUnit.MINUTES.toMillis(5);

    private final PrismarinCorePlugin plugin;
    private final CoreServices services;
    private final ExecutorService executor;
    private final StatsSettings settings;
    private final ProfileService profiles;
    private final PlayerService players;
    private final Map<String, Long> reported = new ConcurrentHashMap<>();
    private DocumentStatsService service;
    private PlaceholderStats placeholders;
    private PlaceholderStats.Resolver resolver;
    private StatSyncSchedule schedule;
    private BukkitTask refresh;
    private BukkitTask sync;
    private volatile long catalogLoaded;

    @Inject
    public PaperStats(PrismarinCorePlugin plugin, CoreServices services, ExecutorService executor,
                      StatsSettings settings, ProfileService profiles, PlayerService players) {
        this.plugin = plugin;
        this.services = services;
        this.executor = executor;
        this.settings = settings;
        this.profiles = profiles;
        this.players = players;
    }

    public synchronized DocumentStatsService start() {
        if (service != null) {
            return service;
        }
        settings.problems().forEach(problem -> plugin.getLogger().warning(StatsSettings.FILE_NAME + ": " + problem));
        service = new DocumentStatsService(services.documents(), executor, services.events(), Clock.systemUTC(),
                settings.service(), new DocumentStatsService.Names() {
                    @Override
                    public Optional<String> known(UUID player) {
                        return profiles.getCached(player).map(PlayerProfile::getName);
                    }

                    @Override
                    public CompletableFuture<Map<UUID, String>> shown(List<UUID> uuids) {
                        return names(uuids);
                    }
                });
        service.reportProblems(this::problem);
        DocumentStatsService current = service;
        loadCatalog(current);
        placeholders = new PlaceholderStats(settings.placeholders(), key -> current.definition(key).format(),
                settings.placeholderSync().decimal());
        resolver = plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")
                ? new PlaceholderApiStatResolver() : null;
        if (!placeholders.empty() && resolver == null) {
            plugin.getLogger().info(StatsSettings.FILE_NAME + " reads " + placeholders.placeholders().size()
                    + " stats through PlaceholderAPI, which is not installed here, so this server leaves them alone");
        }
        schedule = StatSyncSchedule.of(settings.placeholderSync());
        long now = System.currentTimeMillis();
        plugin.getServer().getOnlinePlayers().forEach(player -> {
            track(player.getUniqueId());
            schedule.joined(player.getUniqueId(), now);
        });
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        long period = settings.refreshSeconds() * 20L;
        refresh = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::refresh, 60L, period);
        sync = plugin.getServer().getScheduler().runTaskTimer(plugin, this::syncDue, 20L, 20L);
        return service;
    }

    public synchronized void stop() {
        if (refresh != null) {
            refresh.cancel();
            refresh = null;
        }
        if (sync != null) {
            sync.cancel();
            sync = null;
        }
        DocumentStatsService current = service;
        if (current != null && readsPlaceholders()) {
            plugin.getServer().getOnlinePlayers().forEach(player -> mirrorPlaceholders(current, player));
        }
        HandlerList.unregisterAll(this);
        service = null;
        resolver = null;
    }

    public Optional<DocumentStatsService> service() {
        return Optional.ofNullable(service);
    }

    public CompletableFuture<PlayerStats> fresh(UUID player) {
        DocumentStatsService current = service;
        if (current == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("stats are off"));
        }
        if (!readsPlaceholders()) {
            return current.stats(player);
        }
        CompletableFuture<PlayerStats> result = new CompletableFuture<>();
        Runnable read = () -> {
            Player online = plugin.getServer().getPlayer(player);
            CompletableFuture<PlayerStats> synced = online == null ? current.stats(player) : sync(current, online);
            synced.whenComplete((stats, error) -> {
                if (error != null) {
                    result.completeExceptionally(error);
                } else {
                    result.complete(stats);
                }
            });
        };
        if (plugin.getServer().isPrimaryThread()) {
            read.run();
        } else {
            plugin.getServer().getScheduler().runTask(plugin, read);
        }
        return result;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        track(uuid);
        StatSyncSchedule current = schedule;
        if (current != null) {
            current.joined(uuid, System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        StatSyncSchedule current = schedule;
        if (current != null) {
            current.left(uuid);
        }
        DocumentStatsService stats = service;
        if (stats != null) {
            if (readsPlaceholders()) {
                mirrorPlaceholders(stats, event.getPlayer());
            }
            stats.forget(uuid);
        }
    }

    private void syncDue() {
        DocumentStatsService current = service;
        StatSyncSchedule plan = schedule;
        if (current == null || plan == null || !readsPlaceholders() && !current.hasSources()) {
            return;
        }
        for (UUID uuid : plan.due(System.currentTimeMillis())) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null) {
                plan.left(uuid);
                continue;
            }
            sync(current, player).exceptionally(error -> {
                plugin.getLogger().log(Level.WARNING, "Could not sync the stats of " + player.getName(), error);
                return null;
            });
        }
    }

    private CompletableFuture<PlayerStats> sync(DocumentStatsService current, Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, Long> values = readPlaceholders(player);
        CompletableFuture<PlayerStats> mirrored = values.isEmpty() ? CompletableFuture.completedFuture(null)
                : current.mirror(uuid, values);
        return mirrored.thenCompose(done -> current.stats(uuid));
    }

    private void mirrorPlaceholders(DocumentStatsService current, Player player) {
        Map<String, Long> values = readPlaceholders(player);
        if (!values.isEmpty()) {
            current.mirror(player.getUniqueId(), values).exceptionally(error -> {
                plugin.getLogger().log(Level.WARNING, "Could not save the placeholder stats of " + player.getName(),
                        error);
                return null;
            });
        }
    }

    private Map<String, Long> readPlaceholders(Player player) {
        PlaceholderStats reader = placeholders;
        PlaceholderStats.Resolver current = resolver;
        if (reader == null || current == null) {
            return Map.of();
        }
        try {
            return reader.read(player.getUniqueId(), current);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read the placeholder stats of " + player.getName(), e);
            return Map.of();
        }
    }

    private boolean readsPlaceholders() {
        PlaceholderStats reader = placeholders;
        return resolver != null && reader != null && !reader.empty();
    }

    private void track(UUID player) {
        DocumentStatsService current = service;
        if (current != null) {
            current.track(player).exceptionally(error -> {
                plugin.getLogger().log(Level.WARNING, "Could not load the stats of " + player, error);
                return null;
            });
        }
    }

    private void loadCatalog(DocumentStatsService current) {
        catalogLoaded = System.currentTimeMillis();
        current.loadCatalog().exceptionally(error -> {
            plugin.getLogger().log(Level.WARNING, "Could not read the shared stat names", error);
            return null;
        });
    }

    private void refresh() {
        DocumentStatsService current = service;
        if (current != null && System.currentTimeMillis() - catalogLoaded >= CATALOG_MILLIS) {
            loadCatalog(current);
        }
        if (current != null && current.watching()) {
            current.refresh().exceptionally(error -> {
                plugin.getLogger().log(Level.WARNING, "Could not rebuild the leaderboards", error);
                return null;
            });
        }
    }

    private void problem(String game, Throwable error) {
        long now = System.currentTimeMillis();
        Long last = reported.get(game);
        if (last != null && now - last < PROBLEM_PAUSE_MILLIS) {
            return;
        }
        reported.put(game, now);
        String message = game.isEmpty() ? "Could not write mirrored stats or shared stat names"
                : "The stat source of " + game + " failed, its stored values show instead";
        plugin.getLogger().log(Level.WARNING, message, error);
    }

    private CompletableFuture<Map<UUID, String>> names(List<UUID> uuids) {
        if (uuids.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        return players.findAll(uuids).thenApply(found -> {
            Map<UUID, String> names = new HashMap<>();
            for (NetworkPlayer player : found) {
                String shown = players.canSee(NetworkCalls.NOBODY, player)
                        ? players.displayName(NetworkCalls.NOBODY, player)
                        : player.realName();
                if (!shown.isBlank()) {
                    names.put(player.uuid(), shown);
                }
            }
            return names;
        });
    }
}
