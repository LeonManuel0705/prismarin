package net.prismarin.velocity.queue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import net.kyori.adventure.bossbar.BossBar;
import net.prismarin.api.queue.QueueApi;
import net.prismarin.api.queue.QueueTarget;
import net.prismarin.shared.config.YamlComments;
import net.prismarin.velocity.social.SubCommands;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public record QueueSettings(QueueRules defaults, Map<String, QueueRules.Override> groups,
                            Map<String, QueueRules.Override> servers, boolean onePerPlayer,
                            boolean leaveOnServerSwitch, int graceSeconds, int tickMillis, int statusRefreshSeconds,
                            int maxSendAttempts, int retryDelaySeconds, Map<String, Integer> priorities, Party party,
                            Display display, List<String> commands, List<String> leaveCommands,
                            SubCommands subCommands, List<String> warnings) {

    public static final String FILE_NAME = "queue.yml";
    public static final String BYPASS = QueueApi.BYPASS_PERMISSION;
    public static final String PRIORITY_PREFIX = "prismarin.queue.priority.";
    public static final String STAFF_PREFIX = "prismarin.staff.queue.";
    public static final SubCommands SUB_COMMANDS = subCommandDefaults();

    private static final Pattern COMMAND = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final Pattern PRIORITY = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final List<String> QUEUE_COMMANDS = List.of("queue");
    private static final List<String> LEAVE_COMMANDS = List.of("leavequeue");

    public record Party(boolean together, boolean membersAlone) {
    }

    public record Display(boolean actionBar, boolean bossBar, BossBar.Color color, BossBar.Overlay overlay,
                          int intervalSeconds) {
    }

    public QueueSettings {
        groups = Map.copyOf(groups);
        servers = Map.copyOf(servers);
        priorities = Map.copyOf(priorities);
        commands = List.copyOf(commands);
        leaveCommands = List.copyOf(leaveCommands);
        subCommands = subCommands == null ? SUB_COMMANDS : subCommands;
        warnings = List.copyOf(warnings);
    }

    public QueueRules rules(QueueTarget target, String group) {
        if (target.isGroup()) {
            return groups.getOrDefault(target.name(), QueueRules.Override.NONE).apply(defaults);
        }
        return serverRules(target.name(), group);
    }

    public QueueRules serverRules(String server, String group) {
        QueueRules base = group == null ? defaults
                : groups.getOrDefault(group.toLowerCase(Locale.ROOT), QueueRules.Override.NONE).apply(defaults);
        return servers.getOrDefault(server.toLowerCase(Locale.ROOT), QueueRules.Override.NONE).apply(base);
    }

    public int priority(Predicate<String> permission) {
        int best = 0;
        for (Map.Entry<String, Integer> entry : priorities.entrySet()) {
            if (entry.getValue() > best && permission.test(PRIORITY_PREFIX + entry.getKey())) {
                best = entry.getValue();
            }
        }
        return best;
    }

    public static boolean bypasses(Predicate<String> permission, QueueTarget target, String group) {
        if (permission.test(BYPASS) || permission.test(BYPASS + "." + target.name())) {
            return true;
        }
        return group != null && !group.equalsIgnoreCase(target.name()) && permission.test(BYPASS + "." + group);
    }

    public long graceMillis() {
        return graceSeconds * 1000L;
    }

    public long retryMillis() {
        return retryDelaySeconds * 1000L;
    }

    public static String staffPermission(String action) {
        return STAFF_PREFIX + ("resume".equals(action) ? "pause" : action);
    }

    private static SubCommands subCommandDefaults() {
        Map<String, List<String>> switches = new LinkedHashMap<>();
        switches.put("join", List.of("join"));
        switches.put("leave", List.of("leave"));
        switches.put("list", List.of("list"));
        switches.put("pause", List.of("pause", "resume"));
        switches.put("clear", List.of("clear"));
        switches.put("send", List.of("send"));
        return new SubCommands(List.of("join", "leave", "list", "pause", "resume", "clear", "send", "help"),
                switches, Set.of());
    }

    private static final List<String> HEADER = List.of(
            "PRISMARIN server queue on the proxy. A player who wants onto a full server or group, or onto a server",
            "whose round is running, waits in a queue and is sent as soon as there is room.",
            "Switch the whole queue off in config.yml under modules: queue.",
            "Every connection counts: the lobby navigator, /play, /server, party warp and follow, friend jumps and",
            "plugins that send players through the API.",
            "Players with prismarin.queue.bypass (or prismarin.queue.bypass.<group or server>) skip every queue and",
            "may join full servers. Staff commands need prismarin.staff.queue.list, .pause, .clear and .send.",
            "The queue lives in the memory of this proxy. With several proxies every proxy keeps its own queues.",
            "/prismarinproxy reload reads this file again, commands and sub-commands need a restart.");

    private static final Map<String, List<String>> COMMENTS = Map.ofEntries(
            Map.entry("defaults", List.of("Rules for every server and group. groups and servers below override",
                    "single keys, servers win over their group.")),
            Map.entry("defaults.enabled", List.of("false lets connections through without a queue.")),
            Map.entry("defaults.max-players", List.of(
                    "Players a server takes before the queue holds the rest. 0 uses max-players of the server,",
                    "a lower number keeps servers below their own limit, for example 12 for a BedWars round.")),
            Map.entry("defaults.respect-game-state", List.of(
                    "true also holds players while the server reports a running, ending or restarting round",
                    "(see the game state API). Waiting and starting servers take players.")),
            Map.entry("defaults.queue-when-offline", List.of(
                    "true lets players queue even when no server of the target is online, for example during",
                    "a restart. false answers that no server is online.")),
            Map.entry("defaults.hold-for-parties", List.of(
                    "A party at the front waits until the whole party fits. true holds the players behind it,",
                    "so slots add up for the party. false lets smaller units behind it take free slots.")),
            Map.entry("groups", List.of("Overrides per server group from servers.yml, for example",
                    "bedwars: {max-players: 12}. The lobby group is not queued by default, the lobby balancer",
                    "already spreads players there.")),
            Map.entry("servers", List.of("Overrides per server, for example bw-1: {enabled: false}.")),
            Map.entry("one-queue-per-player", List.of(
                    "true: joining another queue leaves the old one. false allows several queues at once,",
                    "the first one that sends the player wins and the others are left.")),
            Map.entry("leave-on-server-switch", List.of(
                    "true removes players from their queue when they switch to another server themselves.")),
            Map.entry("reconnect-grace-seconds", List.of(
                    "A player who disconnects keeps the place this long. Coming back in time keeps the position,",
                    "players behind move on meanwhile. 0 removes the player at once.")),
            Map.entry("tick-millis", List.of("How often the queue looks for free slots, 250 to 10000.")),
            Map.entry("status-refresh-seconds", List.of(
                    "How often the proxy pings the servers for players, slots and game state while someone waits.")),
            Map.entry("max-send-attempts", List.of(
                    "Failed sends in a row before a player is taken out of the queue. A failed player goes back",
                    "to his place and waits retry-delay-seconds.")),
            Map.entry("retry-delay-seconds", List.of(
                    "Seconds a player waits after a failed send or after a plugin held the send back.")),
            Map.entry("priorities", List.of(
                    "Priority levels: name: weight. prismarin.queue.priority.<name> gives the weight, the highest",
                    "matching weight wins and goes first. Same weight keeps first come, first served.",
                    "Players without a level have weight 0. A party uses the weight of its leader.")),
            Map.entry("party", List.of("Parties need modules.party in config.yml.")),
            Map.entry("party.together", List.of(
                    "true: when the leader queues, the whole party queues as one unit and is only sent when",
                    "there is room for everyone. Only parties with follow on (party setting and social.yml",
                    "follow rules) queue together, otherwise the leader queues alone.")),
            Map.entry("party.members-queue-alone", List.of(
                    "false: only the leader may queue. /queue join and /play tell members to ask the leader,",
                    "their own connects to full servers fail as they would without a queue.")),
            Map.entry("display", List.of("How waiting players see their place.")),
            Map.entry("display.action-bar", List.of("Shows \"Queue for BedWars: 3 of 12\" above the hotbar.")),
            Map.entry("display.boss-bar", List.of("Shows the same text as a boss bar that fills up towards the front.")),
            Map.entry("display.boss-bar-color", List.of("pink, blue, red, green, yellow, purple or white.")),
            Map.entry("display.boss-bar-style", List.of("progress, notched_6, notched_10, notched_12 or notched_20.")),
            Map.entry("display.interval-seconds", List.of("Seconds between two updates of the display.")),
            Map.entry("commands", List.of("Command and aliases. The first name is the main command.")),
            Map.entry("leave-commands", List.of("Short commands for /queue leave.")),
            Map.entry("sub-commands", List.of(
                    "false switches a part of /queue off. It disappears from the help and the tab completion.",
                    "pause also takes resume with it, leave also the leave-commands.")));

    public static QueueSettings standard() {
        return read(filled(), new ArrayList<>());
    }

    public static QueueSettings load(Path file) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(file)
                    .nodeStyle(NodeStyle.BLOCK)
                    .indent(2)
                    .build();
            CommentedConfigurationNode root;
            try {
                root = Files.exists(file) ? loader.load() : loader.createNode();
            } catch (ConfigurateException e) {
                List<String> warnings = new ArrayList<>();
                warnings.add(FILE_NAME + " could not be read, using the defaults: " + e.getMessage());
                return read(filled(), warnings);
            }
            if (fillDefaults(root)) {
                loader.save(root);
            }
            YamlComments.apply(file, HEADER, COMMENTS);
            return read(root, new ArrayList<>());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static QueueSettings read(ConfigurationNode root, List<String> warnings) {
        QueueRules defaults = defaults(root.node("defaults"), warnings);
        Map<String, QueueRules.Override> groups = overrides(root.node("groups"), "groups", warnings);
        Map<String, QueueRules.Override> servers = overrides(root.node("servers"), "servers", warnings);
        Party party = new Party(root.node("party", "together").getBoolean(true),
                root.node("party", "members-queue-alone").getBoolean(true));
        ConfigurationNode display = root.node("display");
        Display shown = new Display(display.node("action-bar").getBoolean(true),
                display.node("boss-bar").getBoolean(false), color(display.node("boss-bar-color"), warnings),
                overlay(display.node("boss-bar-style"), warnings),
                range(display.node("interval-seconds"), "display.interval-seconds", 2, 1, 30, warnings));
        return new QueueSettings(defaults, groups, servers, root.node("one-queue-per-player").getBoolean(true),
                root.node("leave-on-server-switch").getBoolean(true),
                range(root.node("reconnect-grace-seconds"), "reconnect-grace-seconds", 60, 0, 3_600, warnings),
                range(root.node("tick-millis"), "tick-millis", 1_000, 250, 10_000, warnings),
                range(root.node("status-refresh-seconds"), "status-refresh-seconds", 3, 1, 60, warnings),
                range(root.node("max-send-attempts"), "max-send-attempts", 3, 1, 20, warnings),
                range(root.node("retry-delay-seconds"), "retry-delay-seconds", 5, 1, 300, warnings),
                priorities(root.node("priorities"), warnings), party, shown,
                commands(root.node("commands"), "commands", QUEUE_COMMANDS, warnings),
                commands(root.node("leave-commands"), "leave-commands", LEAVE_COMMANDS, warnings),
                switches(root.node("sub-commands"), warnings), warnings);
    }

    private static QueueRules defaults(ConfigurationNode node, List<String> warnings) {
        QueueRules base = QueueRules.DEFAULT;
        return new QueueRules(node.node("enabled").getBoolean(base.enabled()),
                range(node.node("max-players"), "defaults.max-players", base.maxPlayers(), 0, 10_000, warnings),
                node.node("respect-game-state").getBoolean(base.respectGameState()),
                node.node("queue-when-offline").getBoolean(base.queueWhenOffline()),
                node.node("hold-for-parties").getBoolean(base.holdForParties()));
    }

    private static Map<String, QueueRules.Override> overrides(ConfigurationNode node, String path,
                                                             List<String> warnings) {
        Map<String, QueueRules.Override> overrides = new LinkedHashMap<>();
        node.childrenMap().forEach((key, child) -> {
            String name = key.toString().strip().toLowerCase(Locale.ROOT);
            if (name.isEmpty() || !child.isMap()) {
                warnings.add(path + "." + key + ": expected keys such as enabled or max-players");
                return;
            }
            Integer maxPlayers = null;
            if (!child.node("max-players").virtual()) {
                maxPlayers = range(child.node("max-players"), path + "." + name + ".max-players", 0, 0, 10_000,
                        warnings);
            }
            child.childrenMap().keySet().stream().map(Object::toString)
                    .filter(option -> !List.of("enabled", "max-players", "respect-game-state", "queue-when-offline",
                            "hold-for-parties").contains(option))
                    .forEach(option -> warnings.add(path + "." + name + "." + option + ": unknown key"));
            overrides.put(name, new QueueRules.Override(bool(child.node("enabled")), maxPlayers,
                    bool(child.node("respect-game-state")), bool(child.node("queue-when-offline")),
                    bool(child.node("hold-for-parties"))));
        });
        return overrides;
    }

    private static Boolean bool(ConfigurationNode node) {
        return node.virtual() ? null : node.getBoolean();
    }

    private static Map<String, Integer> priorities(ConfigurationNode node, List<String> warnings) {
        Map<String, Integer> priorities = new LinkedHashMap<>();
        node.childrenMap().forEach((key, child) -> {
            String name = key.toString().strip().toLowerCase(Locale.ROOT);
            int weight = child.getInt(-1);
            if (!PRIORITY.matcher(name).matches()) {
                warnings.add("priorities." + key + ": use letters, digits, - and _ for the name");
                return;
            }
            if (weight < 1 || weight > 1_000_000) {
                warnings.add("priorities." + name + ": needs a weight between 1 and 1000000");
                return;
            }
            priorities.put(name, weight);
        });
        return priorities;
    }

    private static BossBar.Color color(ConfigurationNode node, List<String> warnings) {
        String raw = node.getString("blue").strip().toLowerCase(Locale.ROOT);
        BossBar.Color color = BossBar.Color.NAMES.value(raw);
        if (color == null) {
            warnings.add("display.boss-bar-color: '" + raw + "' is no boss bar colour, using blue");
            return BossBar.Color.BLUE;
        }
        return color;
    }

    private static BossBar.Overlay overlay(ConfigurationNode node, List<String> warnings) {
        String raw = node.getString("progress").strip().toLowerCase(Locale.ROOT);
        BossBar.Overlay overlay = BossBar.Overlay.NAMES.value(raw);
        if (overlay == null) {
            warnings.add("display.boss-bar-style: '" + raw + "' is no boss bar style, using progress");
            return BossBar.Overlay.PROGRESS;
        }
        return overlay;
    }

    private static SubCommands switches(ConfigurationNode node, List<String> warnings) {
        Set<String> off = new LinkedHashSet<>();
        node.childrenMap().forEach((key, child) -> {
            String name = key.toString().toLowerCase(Locale.ROOT);
            if (!SUB_COMMANDS.switches().containsKey(name)) {
                warnings.add("sub-commands." + name + ": unknown switch, use one of "
                        + SUB_COMMANDS.switches().keySet());
                return;
            }
            if (!child.getBoolean(true)) {
                off.add(name);
            }
        });
        return SUB_COMMANDS.withOff(off);
    }

    private static List<String> commands(ConfigurationNode node, String path, List<String> fallback,
                                         List<String> warnings) {
        Set<String> names = new LinkedHashSet<>();
        for (String raw : strings(node)) {
            String name = raw.strip().toLowerCase(Locale.ROOT);
            if (name.startsWith("/")) {
                name = name.substring(1);
            }
            if (COMMAND.matcher(name).matches()) {
                names.add(name);
            } else {
                warnings.add(path + ": '" + raw + "' is no valid command name");
            }
        }
        if (names.isEmpty()) {
            warnings.add(path + ": needs at least one command name, using " + fallback);
            return fallback;
        }
        return List.copyOf(names);
    }

    private static List<String> strings(ConfigurationNode node) {
        if (node.virtual()) {
            return List.of();
        }
        if (!node.isList()) {
            return Optional.ofNullable(node.getString()).map(List::of).orElse(List.of());
        }
        List<String> values = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            String value = child.getString();
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    private static int range(ConfigurationNode node, String path, int fallback, int min, int max,
                             List<String> warnings) {
        int value = node.getInt(fallback);
        if (value < min || value > max) {
            int clamped = Math.max(min, Math.min(max, value));
            warnings.add(path + ": " + value + " must be between " + min + " and " + max + ", using " + clamped);
            return clamped;
        }
        return value;
    }

    private static CommentedConfigurationNode filled() {
        CommentedConfigurationNode root = YamlConfigurationLoader.builder().build().createNode();
        try {
            fillDefaults(root);
        } catch (SerializationException e) {
            throw new IllegalStateException(e);
        }
        return root;
    }

    static boolean fillDefaults(ConfigurationNode root) throws SerializationException {
        QueueRules base = QueueRules.DEFAULT;
        boolean changed = fill(root.node("defaults", "enabled"), base.enabled());
        changed |= fill(root.node("defaults", "max-players"), base.maxPlayers());
        changed |= fill(root.node("defaults", "respect-game-state"), base.respectGameState());
        changed |= fill(root.node("defaults", "queue-when-offline"), base.queueWhenOffline());
        changed |= fill(root.node("defaults", "hold-for-parties"), base.holdForParties());
        if (root.node("groups").virtual()) {
            root.node("groups", "lobby", "enabled").set(false);
            changed = true;
        }
        if (root.node("servers").virtual()) {
            root.node("servers").set(Map.of());
            changed = true;
        }
        changed |= fill(root.node("one-queue-per-player"), true);
        changed |= fill(root.node("leave-on-server-switch"), true);
        changed |= fill(root.node("reconnect-grace-seconds"), 60);
        changed |= fill(root.node("tick-millis"), 1_000);
        changed |= fill(root.node("status-refresh-seconds"), 3);
        changed |= fill(root.node("max-send-attempts"), 3);
        changed |= fill(root.node("retry-delay-seconds"), 5);
        if (root.node("priorities").virtual()) {
            root.node("priorities", "vip").set(10);
            root.node("priorities", "mvp").set(20);
            changed = true;
        }
        changed |= fill(root.node("party", "together"), true);
        changed |= fill(root.node("party", "members-queue-alone"), true);
        changed |= fill(root.node("display", "action-bar"), true);
        changed |= fill(root.node("display", "boss-bar"), false);
        changed |= fill(root.node("display", "boss-bar-color"), "blue");
        changed |= fill(root.node("display", "boss-bar-style"), "progress");
        changed |= fill(root.node("display", "interval-seconds"), 2);
        changed |= fillList(root.node("commands"), QUEUE_COMMANDS);
        changed |= fillList(root.node("leave-commands"), LEAVE_COMMANDS);
        for (String key : SUB_COMMANDS.switches().keySet()) {
            changed |= fill(root.node("sub-commands", key), true);
        }
        return changed;
    }

    private static boolean fill(ConfigurationNode node, Object value) throws SerializationException {
        if (!node.virtual()) {
            return false;
        }
        node.set(value);
        return true;
    }

    private static boolean fillList(ConfigurationNode node, List<String> values) throws SerializationException {
        if (!node.virtual()) {
            return false;
        }
        node.setList(String.class, values);
        return true;
    }
}
