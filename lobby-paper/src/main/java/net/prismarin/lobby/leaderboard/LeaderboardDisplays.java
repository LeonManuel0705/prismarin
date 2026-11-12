package net.prismarin.lobby.leaderboard;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.stats.Leaderboard;
import net.prismarin.api.stats.LeaderboardEntry;
import net.prismarin.api.stats.StatDefinition;
import net.prismarin.api.stats.StatsApi;
import net.prismarin.api.stats.StatsService;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.core.stats.StatTexts;
import net.prismarin.lobby.LobbyPlugin;
import net.prismarin.lobby.util.ConfigText;
import net.prismarin.shared.config.CoreConfig;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

@Singleton
public final class LeaderboardDisplays implements Listener {

    private final LobbyPlugin plugin;
    private final LeaderboardsFile file;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final StatTexts texts;
    private final ConfigText configText;
    private final CoreConfig config;
    private final Map<String, Map<String, TextDisplay>> entities = new HashMap<>();
    private final Map<String, Leaderboard> data = new ConcurrentHashMap<>();
    private final Map<UUID, String> viewers = new HashMap<>();
    private BukkitTask task;

    @Inject
    public LeaderboardDisplays(LobbyPlugin plugin, LeaderboardsFile file, LocaleService locale,
                               ProfileService profiles, StatTexts texts, ConfigText configText, CoreConfig config) {
        this.plugin = plugin;
        this.file = file;
        this.locale = locale;
        this.profiles = profiles;
        this.texts = texts;
        this.configText = configText;
        this.config = config;
    }

    public void start() {
        file.warnings().forEach(warning -> plugin.getLogger().warning(warning));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getOnlinePlayers().forEach(this::assign);
        file.boards().forEach(this::spawn);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refresh, 20L,
                file.refreshSeconds() * 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        HandlerList.unregisterAll(this);
        entities.values().forEach(byLanguage -> byLanguage.values().forEach(TextDisplay::remove));
        entities.clear();
        viewers.clear();
        data.clear();
    }

    public LeaderboardsFile file() {
        return file;
    }

    public void place(LeaderboardBoard board) {
        despawn(board.id());
        file.put(board);
        data.remove(board.id());
        spawn(board);
        load(board);
    }

    public boolean remove(String id) {
        despawn(id);
        data.remove(id);
        return file.remove(id);
    }

    public void refresh() {
        plugin.getServer().getOnlinePlayers().forEach(this::assign);
        Set<String> languages = new HashSet<>(viewers.values());
        for (LeaderboardBoard board : file.boards()) {
            Map<String, TextDisplay> shown = entities.getOrDefault(board.id(), Map.of());
            shown.entrySet().stream()
                    .filter(entry -> !languages.contains(entry.getKey()) || !entry.getValue().isValid())
                    .map(Map.Entry::getKey)
                    .toList()
                    .forEach(language -> despawn(board.id(), language));
            spawn(board);
            load(board);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                assign(player);
                file.boards().forEach(this::spawn);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        viewers.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        List<LeaderboardBoard> inside = file.boards().stream()
                .filter(board -> board.world().equals(chunk.getWorld().getName()))
                .filter(board -> board.chunkX() == chunk.getX() && board.chunkZ() == chunk.getZ())
                .toList();
        if (!inside.isEmpty()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> inside.forEach(this::spawn));
        }
    }

    private void load(LeaderboardBoard board) {
        Optional<StatsService> stats = StatsApi.service();
        if (stats.isEmpty()) {
            return;
        }
        stats.get().top(board.stat(), board.period(), board.size()).whenComplete((top, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "Could not load the leaderboard " + board.id(), error);
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (file.board(board.id()).isEmpty()) {
                    return;
                }
                data.put(board.id(), top);
                entities.getOrDefault(board.id(), Map.of()).forEach((language, display) -> {
                    if (display.isValid()) {
                        display.text(text(board, language));
                    }
                });
            });
        });
    }

    private void assign(Player player) {
        String language = language(player);
        String previous = viewers.put(player.getUniqueId(), language);
        if (language.equals(previous)) {
            return;
        }
        for (Map<String, TextDisplay> byLanguage : entities.values()) {
            if (previous != null && byLanguage.containsKey(previous)) {
                player.hideEntity(plugin, byLanguage.get(previous));
            }
            TextDisplay display = byLanguage.get(language);
            if (display != null && display.isValid()) {
                player.showEntity(plugin, display);
            }
        }
    }

    private void spawn(LeaderboardBoard board) {
        World world = plugin.getServer().getWorld(board.world());
        if (world == null || !world.isChunkLoaded(board.chunkX(), board.chunkZ())) {
            return;
        }
        Map<String, TextDisplay> byLanguage = entities.computeIfAbsent(board.id(), id -> new HashMap<>());
        for (String language : new HashSet<>(viewers.values())) {
            TextDisplay existing = byLanguage.get(language);
            if (existing != null && existing.isValid()) {
                continue;
            }
            Location location = new Location(world, board.x(), board.y(), board.z(), board.yaw(), 0.0f);
            TextDisplay display = world.spawn(location, TextDisplay.class, entity -> {
                entity.setPersistent(false);
                entity.setVisibleByDefault(false);
                style(entity);
                entity.text(text(board, language));
            });
            byLanguage.put(language, display);
            viewers.forEach((uuid, chosen) -> {
                Player viewer = plugin.getServer().getPlayer(uuid);
                if (viewer != null && chosen.equals(language)) {
                    viewer.showEntity(plugin, display);
                }
            });
        }
    }

    private void despawn(String id) {
        Map<String, TextDisplay> byLanguage = entities.remove(id);
        if (byLanguage != null) {
            byLanguage.values().forEach(TextDisplay::remove);
        }
    }

    private void despawn(String id, String language) {
        Map<String, TextDisplay> byLanguage = entities.get(id);
        if (byLanguage == null) {
            return;
        }
        TextDisplay display = byLanguage.remove(language);
        if (display != null) {
            display.remove();
        }
    }

    private void style(TextDisplay entity) {
        LeaderboardsFile.Display display = file.display();
        entity.setBillboard(billboard(display.billboard()));
        entity.setAlignment(TextDisplay.TextAlignment.CENTER);
        entity.setShadowed(display.shadow());
        entity.setSeeThrough(false);
        entity.setLineWidth(display.lineWidth());
        Optional<Integer> argb = display.argb();
        if (argb.isPresent()) {
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(Color.fromARGB(argb.get()));
        } else {
            entity.setDefaultBackground(true);
        }
        float scale = (float) display.scale();
        Transformation current = entity.getTransformation();
        entity.setTransformation(new Transformation(current.getTranslation(), new AxisAngle4f(),
                new Vector3f(scale, scale, scale), new AxisAngle4f()));
    }

    private Component text(LeaderboardBoard board, String language) {
        Locale target = Locale.forLanguageTag(language);
        Optional<StatsService> stats = StatsApi.service();
        StatDefinition definition = stats.map(service -> service.definition(board.stat()))
                .orElseGet(() -> StatDefinition.of(board.stat()));
        Component stat = texts.name(definition, target);
        Component period = texts.period(board.period(), target);
        TagResolver[] heading = {Placeholder.component("stat", stat), Placeholder.component("period", period)};
        List<Component> lines = new ArrayList<>();
        lines.add(board.title().isBlank() ? locale.render("lobby.leaderboard.title", target, heading)
                : configText.render(board.title(), target, null, heading));
        lines.add(locale.render("lobby.leaderboard.period", target, heading));
        lines.add(Component.empty());
        Leaderboard top = data.get(board.id());
        for (int position = 1; position <= board.size(); position++) {
            Optional<LeaderboardEntry> entry = top == null ? Optional.empty() : top.entry(position);
            Component rank = rank(entry.map(LeaderboardEntry::rank).orElse(position), target);
            if (entry.isEmpty()) {
                lines.add(locale.render("lobby.leaderboard.empty", target, Placeholder.component("rank", rank)));
                continue;
            }
            String value = stats.map(service -> service.format(board.stat(), entry.get().value(), target))
                    .orElse(Long.toString(entry.get().value()));
            TagResolver[] row = {Placeholder.component("rank", rank),
                    Placeholder.unparsed("player", entry.get().name()), Placeholder.unparsed("value", value),
                    Placeholder.component("stat", stat), Placeholder.component("period", period)};
            lines.add(board.line().isBlank() ? locale.render("lobby.leaderboard.line", target, row)
                    : configText.render(board.line(), target, null, row));
        }
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    private Component rank(int rank, Locale target) {
        String key = "lobby.leaderboard.rank." + rank;
        if (rank <= 3 && !locale.raw(key, target).equals(key)) {
            return locale.render(key, target);
        }
        return locale.render("lobby.leaderboard.rank.other", target,
                Placeholder.unparsed("rank", Integer.toString(rank)));
    }

    private String language(Player player) {
        String language = PlayerLocales.of(player, profiles).getLanguage();
        return language.isEmpty() ? Locale.forLanguageTag(config.defaultLocale()).getLanguage() : language;
    }

    private static Display.Billboard billboard(String value) {
        return switch (value) {
            case "fixed" -> Display.Billboard.FIXED;
            case "vertical" -> Display.Billboard.VERTICAL;
            case "horizontal" -> Display.Billboard.HORIZONTAL;
            default -> Display.Billboard.CENTER;
        };
    }
}
