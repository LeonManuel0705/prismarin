package net.prismarin.lobby.leaderboard;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.prismarin.api.stats.StatKeys;
import net.prismarin.api.stats.StatPeriod;
import net.prismarin.shared.config.YamlComments;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public final class LeaderboardsFile {

    public static final String FILE_NAME = "leaderboards.yml";

    static final int DEFAULT_REFRESH_SECONDS = 30;

    private static final List<String> HEADER = List.of(
            "Leaderboards in the lobby, shown as floating text without a hologram plugin.",
            "Place them with /leaderboard create <id> <stat> [period] [size] where you stand, then use",
            "/leaderboard move, remove and list. Each player sees the board in his language, nicked players",
            "appear with their nick. The texts are lobby.leaderboard.* in the PrismarinCore messages files,",
            "title and line of a board replace them for that board (a text or a message key with %stat%,",
            "%period%, %rank%, %player% and %value%).");

    private static final Map<String, List<String>> COMMENTS = Map.ofEntries(
            Map.entry("refresh-seconds", List.of("How often the boards show the newest numbers.")),
            Map.entry("display", List.of("Look of every board.")),
            Map.entry("display.billboard", List.of(
                    "center turns the board towards every player, fixed keeps the direction it was placed in,",
                    "vertical and horizontal turn it around one axis only.")),
            Map.entry("display.background", List.of(
                    "Background colour as #AARRGGBB (alpha first) or #RRGGBB, default for the grey Minecraft",
                    "background, none for no background.")),
            Map.entry("display.shadow", List.of("Shadow behind the letters.")),
            Map.entry("display.line-width", List.of("Width in pixels after which a line wraps.")),
            Map.entry("display.scale", List.of("Size of the board, 1.0 is normal.")),
            Map.entry("boards", List.of("Written by /leaderboard, period is alltime, weekly or monthly.")));

    public record Display(String billboard, String background, boolean shadow, int lineWidth, double scale) {

        public static final Display DEFAULT = new Display("center", "#80101826", true, 250, 1.0);

        public Display {
            billboard = billboard == null ? "center" : billboard.strip().toLowerCase(Locale.ROOT);
            background = background == null ? "default" : background.strip();
            lineWidth = Math.clamp(lineWidth, 50, 1_000);
            scale = Math.clamp(scale, 0.1d, 10.0d);
        }

        public Optional<Integer> argb() {
            String value = background.toLowerCase(Locale.ROOT);
            if (value.equals("none")) {
                return Optional.of(0);
            }
            if (!value.matches("#([0-9a-f]{6}|[0-9a-f]{8})")) {
                return Optional.empty();
            }
            long parsed = Long.parseLong(value.substring(1), 16);
            return Optional.of((int) (value.length() == 7 ? 0xFF000000L | parsed : parsed));
        }
    }

    private final Path file;
    private final int refreshSeconds;
    private final Display display;
    private final List<String> warnings;
    private volatile Map<String, LeaderboardBoard> boards;

    private LeaderboardsFile(Path file, int refreshSeconds, Display display, Map<String, LeaderboardBoard> boards,
                             List<String> warnings) {
        this.file = file;
        this.refreshSeconds = refreshSeconds;
        this.display = display;
        this.boards = Collections.unmodifiableMap(new LinkedHashMap<>(boards));
        this.warnings = List.copyOf(warnings);
    }

    public static LeaderboardsFile load(Path file) {
        List<String> warnings = new ArrayList<>();
        ConfigurationNode root;
        try {
            root = Files.exists(file) ? loader(file).load() : loader(file).createNode();
        } catch (ConfigurateException e) {
            warnings.add(FILE_NAME + " could not be read: " + e.getMessage());
            root = loader(file).createNode();
        }
        ConfigurationNode settings = root.node("display");
        Display display = new Display(settings.node("billboard").getString(Display.DEFAULT.billboard()),
                settings.node("background").getString(Display.DEFAULT.background()),
                settings.node("shadow").getBoolean(Display.DEFAULT.shadow()),
                settings.node("line-width").getInt(Display.DEFAULT.lineWidth()),
                settings.node("scale").getDouble(Display.DEFAULT.scale()));
        Map<String, LeaderboardBoard> boards = new LinkedHashMap<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : root.node("boards").childrenMap().entrySet()) {
            String id = entry.getKey().toString();
            ConfigurationNode node = entry.getValue();
            String stat = node.node("stat").getString("");
            String periodText = node.node("period").getString(StatPeriod.ALL_TIME.id());
            Optional<StatPeriod> period = StatPeriod.byId(periodText);
            String world = node.node("world").getString("");
            if (!LeaderboardBoard.validId(id) || !StatKeys.valid(stat) || period.isEmpty() || world.isBlank()) {
                warnings.add("boards." + id + " needs a valid id, stat, period and world and is skipped");
                continue;
            }
            LeaderboardBoard board = new LeaderboardBoard(id, stat, period.get(),
                    node.node("size").getInt(LeaderboardBoard.DEFAULT_SIZE), world, node.node("x").getDouble(0.0),
                    node.node("y").getDouble(64.0), node.node("z").getDouble(0.0),
                    (float) node.node("yaw").getDouble(0.0), node.node("title").getString(""),
                    node.node("line").getString(""));
            boards.put(board.id(), board);
        }
        int refresh = Math.clamp(root.node("refresh-seconds").getInt(DEFAULT_REFRESH_SECONDS), 5, 3_600);
        LeaderboardsFile loaded = new LeaderboardsFile(file, refresh, display, boards, warnings);
        loaded.save();
        return loaded;
    }

    public int refreshSeconds() {
        return refreshSeconds;
    }

    public Display display() {
        return display;
    }

    public List<String> warnings() {
        return warnings;
    }

    public List<LeaderboardBoard> boards() {
        return List.copyOf(boards.values());
    }

    public Optional<LeaderboardBoard> board(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(boards.get(id.toLowerCase(Locale.ROOT)));
    }

    public synchronized void put(LeaderboardBoard board) {
        Map<String, LeaderboardBoard> updated = new LinkedHashMap<>(boards);
        updated.put(board.id(), board);
        boards = Collections.unmodifiableMap(updated);
    }

    public synchronized boolean remove(String id) {
        if (id == null || !boards.containsKey(id.toLowerCase(Locale.ROOT))) {
            return false;
        }
        Map<String, LeaderboardBoard> updated = new LinkedHashMap<>(boards);
        updated.remove(id.toLowerCase(Locale.ROOT));
        boards = Collections.unmodifiableMap(updated);
        return true;
    }

    public synchronized void save() {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            YamlConfigurationLoader loader = loader(file);
            ConfigurationNode root = loader.createNode();
            root.node("refresh-seconds").set(refreshSeconds);
            root.node("display", "billboard").set(display.billboard());
            root.node("display", "background").set(display.background());
            root.node("display", "shadow").set(display.shadow());
            root.node("display", "line-width").set(display.lineWidth());
            root.node("display", "scale").set(display.scale());
            if (boards.isEmpty()) {
                root.node("boards").set(Map.of());
            }
            for (LeaderboardBoard board : boards.values()) {
                write(root.node("boards", board.id()), board);
            }
            loader.save(root);
            YamlComments.apply(file, HEADER, COMMENTS);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(ConfigurationNode node, LeaderboardBoard board) throws SerializationException {
        node.node("stat").set(board.stat());
        node.node("period").set(board.period().id());
        node.node("size").set(board.size());
        node.node("world").set(board.world());
        node.node("x").set(round(board.x()));
        node.node("y").set(round(board.y()));
        node.node("z").set(round(board.z()));
        node.node("yaw").set(round(board.yaw()));
        node.node("title").set(board.title());
        node.node("line").set(board.line());
    }

    private static double round(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    private static YamlConfigurationLoader loader(Path file) {
        return YamlConfigurationLoader.builder()
                .path(file)
                .nodeStyle(NodeStyle.BLOCK)
                .indent(2)
                .build();
    }
}
