package net.prismarin.velocity.joinme;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.prismarin.api.joinme.JoinMeAudience;
import net.prismarin.api.skin.ChatFace;
import net.prismarin.shared.config.YamlComments;
import net.prismarin.shared.skin.FaceRows;
import net.prismarin.shared.skin.SkinFaceService;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public record JoinMeSettings(Create create, Tiers<Long> cooldowns, Tiers<Cost> costs, Tiers<Long> lifetimes,
                             Tiers<Integer> maxJoins, Join join, Audience audience, Broadcast broadcast, Face face,
                             List<String> problems) {

    public static final String FILE_NAME = "joinme.yml";
    public static final String COOLDOWN_PERMISSION = "prismarin.joinme.cooldown.";
    public static final String COST_PERMISSION = "prismarin.joinme.cost.";
    public static final String LIFETIME_PERMISSION = "prismarin.joinme.lifetime.";
    public static final String JOINS_PERMISSION = "prismarin.joinme.joins.";
    static final long MAX_SECONDS = 86_400L;
    private static final Pattern TIER = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final Pattern CURRENCY = Pattern.compile("[a-z0-9_-]{1,32}");

    public record Create(long lifetimeSeconds, int maxJoins, Set<String> allowedGroups, Set<String> blockedGroups,
                         boolean joinableStateOnly, boolean endWhenCreatorLeaves) {

        public Create {
            allowedGroups = Set.copyOf(allowedGroups);
            blockedGroups = Set.copyOf(blockedGroups);
        }

        public boolean groupAllowed(String group) {
            String key = group == null ? "" : group.toLowerCase(Locale.ROOT);
            if (blockedGroups.contains(key)) {
                return false;
            }
            return allowedGroups.isEmpty() || allowedGroups.contains(key);
        }
    }

    public record Cost(String currency, long amount) {

        public Cost {
            currency = currency == null ? "" : currency.toLowerCase(Locale.ROOT);
            amount = Math.max(0L, amount);
        }

        public boolean free() {
            return amount <= 0L || currency.isEmpty();
        }
    }

    public record Join(boolean sameServer, boolean joinableStateOnly, String permission, int arrivalTimeoutSeconds,
                       boolean notifyCreator) {

        public Join {
            permission = permission == null ? "" : permission.strip();
        }

        public long arrivalTimeoutMillis() {
            return arrivalTimeoutSeconds * 1000L;
        }
    }

    public record Audience(JoinMeAudience audience, String permission, boolean skipSameWorld) {

        public Audience {
            permission = permission == null ? "" : permission.strip();
        }
    }

    public record Broadcast(boolean title, String sound, float volume, float pitch) {

        public Broadcast {
            sound = sound == null ? "" : sound.strip();
        }
    }

    public record Face(boolean enabled, String symbol, boolean textWithoutSkin, boolean blankBefore,
                       boolean blankAfter, String gap, Map<Integer, String> rows, SkinFaceService.Settings cache) {

        public Face {
            symbol = symbol == null || symbol.isEmpty() ? ChatFace.SYMBOL : symbol;
            gap = gap == null ? " " : gap;
            rows = Collections.unmodifiableMap(new TreeMap<>(rows));
            cache = cache == null ? SkinFaceService.Settings.DEFAULT : cache;
        }
    }

    private static final List<String> HEADER = Stream.concat(Stream.of(
            "JoinMe. A player with prismarin.joinme types /joinme on a game server and everybody in the audience",
            "below gets a clickable message. A click brings the player to the creator: a game plugin that",
            "registered a JoinMe handler decides what joining means (for example the same arena or team),",
            "otherwise the player is sent to the creator's server and, see joinme.yml on the game server,",
            "into the creator's world.",
            "",
            "Tiers: every entry under cooldowns, costs, lifetime-tiers and max-joins-tiers is granted by the",
            "permission prismarin.joinme.cooldown.<name>, .cost.<name>, .lifetime.<name> or .joins.<name>.",
            "The entry called default applies to everybody. With several entries the best one wins: the",
            "shortest cooldown, the lowest cost, the longest lifetime and the most joins (0 is unlimited).",
            "",
            "The texts are in the proxy's messages files under joinme, the commands in PrismarinCore.",
            ""), YamlComments.TEXT_HELP.stream()).toList();

    private static final Map<String, List<String>> COMMENTS = Map.ofEntries(
            Map.entry("create", List.of("Rules for /joinme.")),
            Map.entry("create.lifetime-seconds", List.of("How long a JoinMe can be clicked, unless a tier says more.")),
            Map.entry("create.max-joins", List.of("How many players may join one JoinMe, 0 is unlimited.")),
            Map.entry("create.allowed-groups", List.of(
                    "Server groups from servers.yml where /joinme works. Empty means every group.")),
            Map.entry("create.blocked-groups", List.of("Server groups where /joinme never works, lobbies by default.")),
            Map.entry("create.joinable-state-only", List.of(
                    "Only while the game state of the server lets players join (unknown, waiting, starting).")),
            Map.entry("create.end-when-creator-leaves", List.of(
                    "Ends the JoinMe when the creator switches the server or goes offline.")),
            Map.entry("cooldowns", List.of(
                    "Seconds between two /joinme per player. Permission prismarin.joinme.cooldown.<name>.")),
            Map.entry("costs", List.of(
                    "What /joinme costs, in any currency of economy.currencies. Permission prismarin.joinme.cost.<name>.",
                    "The game server charges it and gives it back when the proxy refuses the JoinMe.")),
            Map.entry("lifetime-tiers", List.of("Longer lifetimes in seconds. Permission prismarin.joinme.lifetime.<name>.")),
            Map.entry("max-joins-tiers", List.of("More joins per JoinMe. Permission prismarin.joinme.joins.<name>.")),
            Map.entry("join", List.of("What happens on a click.")),
            Map.entry("join.same-server", List.of(
                    "Without a game plugin handler: send the player to the creator's server. Turned off, a JoinMe",
                    "only works for players who are already on that server (multi world servers).")),
            Map.entry("join.joinable-state-only", List.of(
                    "Refuses clicks while the game state of the target server does not let players join.")),
            Map.entry("join.permission", List.of("Permission needed to click a JoinMe. Empty means everybody.")),
            Map.entry("join.arrival-timeout-seconds", List.of(
                    "How long the proxy waits for a clicked player to arrive, for example while he waits in a queue.")),
            Map.entry("join.notify-creator", List.of("Tells the creator who joined through his JoinMe.")),
            Map.entry("audience", List.of("Who gets the message.")),
            Map.entry("audience.scope", List.of(
                    "groups: players on servers of the groups below. network: everybody on the network.")),
            Map.entry("audience.groups", List.of("Server groups for scope groups.")),
            Map.entry("audience.only", List.of(
                    "Limits the message to friends, party and or clan of the creator. Empty means everybody in scope.")),
            Map.entry("audience.permission", List.of("Permission needed to get the message. Empty means everybody.")),
            Map.entry("audience.skip-same-world", List.of("Players already in the creator's world get no message.")),
            Map.entry("broadcast", List.of("Extras for the message.")),
            Map.entry("broadcast.title", List.of("Also shows a title (joinme.title and joinme.subtitle).")),
            Map.entry("broadcast.sound", List.of("Sound key, for example entity.experience_orb.pickup. Empty plays none.")),
            Map.entry("face", List.of(
                    "Draws the creator's skin face as 8 x 8 coloured blocks in chat, the texts stand to the right.",
                    "A nicked creator shows the nick's skin, exactly what the other players see.")),
            Map.entry("face.enabled", List.of("false sends the one line text joinme.broadcast instead.")),
            Map.entry("face.symbol", List.of("Character for one pixel.")),
            Map.entry("face.without-skin", List.of(
                    "Without a skin (offline mode, failed download): default shows Steve or Alex, text sends",
                    "joinme.broadcast instead.")),
            Map.entry("face.gap", List.of("Space between the face and the text.")),
            Map.entry("face.rows", List.of(
                    "Which text stands next to which row of the face, 1 to 16. Rows above 8 stand below the face.",
                    "A value is a message key of the messages files or a text. Plugins can change rows too.")),
            Map.entry("face.cache-size", List.of("Faces kept in memory, by texture.")),
            Map.entry("face.disk-cache", List.of("Also keeps computed faces in the folder skin-faces.")),
            Map.entry("face.download-timeout-millis", List.of(
                    "Longest wait for a skin from textures.minecraft.net. Only the public texture URL is requested.")));

    public JoinMeSettings {
        problems = List.copyOf(problems);
    }

    public static JoinMeSettings load(Path file) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            YamlConfigurationLoader loader = loader(file);
            CommentedConfigurationNode root;
            try {
                root = Files.exists(file) ? loader.load() : loader.createNode();
            } catch (ConfigurateException e) {
                return read(filled(), List.of(FILE_NAME + " could not be read, using the defaults: "
                        + e.getMessage()));
            }
            if (fillDefaults(root)) {
                loader.save(root);
            }
            YamlComments.apply(file, HEADER, COMMENTS);
            return read(root, List.of());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static JoinMeSettings defaults() {
        return read(filled(), List.of());
    }

    static JoinMeSettings read(ConfigurationNode root, List<String> initial) {
        List<String> problems = new ArrayList<>(initial);
        ConfigurationNode create = root.node("create");
        Create rules = new Create(
                Math.clamp(create.node("lifetime-seconds").getLong(120L), 10L, MAX_SECONDS),
                Math.clamp(create.node("max-joins").getInt(0), 0, 1_000),
                groups(create.node("allowed-groups")), groups(create.node("blocked-groups")),
                create.node("joinable-state-only").getBoolean(true),
                create.node("end-when-creator-leaves").getBoolean(true));
        Tiers<Long> cooldowns = new Tiers<>(COOLDOWN_PERMISSION, seconds(root.node("cooldowns"), "cooldowns", 0L,
                problems));
        Tiers<Long> lifetimes = new Tiers<>(LIFETIME_PERMISSION, seconds(root.node("lifetime-tiers"),
                "lifetime-tiers", 10L, problems));
        Map<String, Integer> joins = new LinkedHashMap<>();
        root.node("max-joins-tiers").childrenMap().forEach((key, node) -> {
            String name = key.toString().toLowerCase(Locale.ROOT);
            if (!TIER.matcher(name).matches()) {
                problems.add("max-joins-tiers." + key + " is no tier name, use " + TIER.pattern());
                return;
            }
            joins.put(name, Math.clamp(node.getInt(0), 0, 1_000));
        });
        Map<String, Cost> costs = new LinkedHashMap<>();
        root.node("costs").childrenMap().forEach((key, node) -> {
            String name = key.toString().toLowerCase(Locale.ROOT);
            String currency = node.node("currency").getString("").strip().toLowerCase(Locale.ROOT);
            if (!TIER.matcher(name).matches()) {
                problems.add("costs." + key + " is no tier name, use " + TIER.pattern());
                return;
            }
            if (!currency.isEmpty() && !CURRENCY.matcher(currency).matches()) {
                problems.add("costs." + key + ".currency '" + currency + "' is no currency id");
                return;
            }
            costs.put(name, new Cost(currency, Math.max(0L, node.node("amount").getLong(0L))));
        });
        ConfigurationNode join = root.node("join");
        Join joining = new Join(join.node("same-server").getBoolean(true),
                join.node("joinable-state-only").getBoolean(true), join.node("permission").getString(""),
                Math.clamp(join.node("arrival-timeout-seconds").getInt(300), 10, 3_600),
                join.node("notify-creator").getBoolean(true));
        ConfigurationNode audienceNode = root.node("audience");
        String scopeText = audienceNode.node("scope").getString("groups");
        JoinMeAudience.Scope scope = JoinMeAudience.Scope.byId(scopeText).orElseGet(() -> {
            problems.add("audience.scope '" + scopeText + "' is neither groups nor network, using groups");
            return JoinMeAudience.Scope.GROUPS;
        });
        Set<JoinMeAudience.Circle> circles = new LinkedHashSet<>();
        for (String circle : strings(audienceNode.node("only"))) {
            JoinMeAudience.Circle.byId(circle).ifPresentOrElse(circles::add,
                    () -> problems.add("audience.only '" + circle + "' is none of friends, party, clan"));
        }
        Set<String> audienceGroups = groups(audienceNode.node("groups"));
        if (scope == JoinMeAudience.Scope.GROUPS && audienceGroups.isEmpty()) {
            problems.add("audience.groups is empty, nobody would get a JoinMe, using the whole network");
            scope = JoinMeAudience.Scope.NETWORK;
        }
        Audience audience = new Audience(new JoinMeAudience(scope, audienceGroups, circles),
                audienceNode.node("permission").getString(""), audienceNode.node("skip-same-world").getBoolean(true));
        ConfigurationNode broadcastNode = root.node("broadcast");
        String sound = broadcastNode.node("sound").getString("").strip().toLowerCase(Locale.ROOT);
        if (!sound.isEmpty() && !sound.matches("([a-z0-9_.-]+:)?[a-z0-9_./-]+")) {
            problems.add("broadcast.sound '" + sound + "' is no sound key, playing none");
            sound = "";
        }
        Broadcast broadcast = new Broadcast(broadcastNode.node("title").getBoolean(false), sound,
                (float) Math.clamp(broadcastNode.node("volume").getDouble(1.0D), 0.0D, 10.0D),
                (float) Math.clamp(broadcastNode.node("pitch").getDouble(1.2D), 0.5D, 2.0D));
        return new JoinMeSettings(rules, cooldowns, new Tiers<>(COST_PERMISSION, costs), lifetimes,
                new Tiers<>(JOINS_PERMISSION, joins), joining, audience, broadcast, face(root.node("face"), problems),
                problems);
    }

    private static Face face(ConfigurationNode node, List<String> problems) {
        String symbol = node.node("symbol").getString(ChatFace.SYMBOL);
        if (symbol.isEmpty() || symbol.codePointCount(0, symbol.length()) > 2) {
            problems.add("face.symbol must be one or two characters, using " + ChatFace.SYMBOL);
            symbol = ChatFace.SYMBOL;
        }
        String without = node.node("without-skin").getString("default").strip().toLowerCase(Locale.ROOT);
        if (!without.equals("default") && !without.equals("text")) {
            problems.add("face.without-skin '" + without + "' is neither default nor text, using default");
            without = "default";
        }
        Map<Integer, String> rows = new TreeMap<>();
        node.node("rows").childrenMap().forEach((key, value) -> {
            String text = key.toString().strip();
            String line = value.getString("").strip();
            int row = text.matches("\\d{1,2}") ? Integer.parseInt(text) : 0;
            if (row < 1 || row > FaceRows.MAX_ROWS) {
                problems.add("face.rows." + key + " is no row from 1 to " + FaceRows.MAX_ROWS);
                return;
            }
            if (!line.isEmpty()) {
                rows.put(row, line);
            }
        });
        return new Face(node.node("enabled").getBoolean(true), symbol, without.equals("text"),
                node.node("blank-line-before").getBoolean(true), node.node("blank-line-after").getBoolean(true),
                node.node("gap").getString(" "), rows,
                new SkinFaceService.Settings(node.node("cache-size").getInt(512),
                        node.node("disk-cache").getBoolean(true),
                        node.node("download-timeout-millis").getLong(3_000L)));
    }

    static boolean fillDefaults(ConfigurationNode root) throws SerializationException {
        boolean changed = false;
        changed |= setIfAbsent(root.node("create", "lifetime-seconds"), 120);
        changed |= setIfAbsent(root.node("create", "max-joins"), 0);
        changed |= setListIfAbsent(root.node("create", "allowed-groups"), List.of());
        changed |= setListIfAbsent(root.node("create", "blocked-groups"), List.of("lobby"));
        changed |= setIfAbsent(root.node("create", "joinable-state-only"), true);
        changed |= setIfAbsent(root.node("create", "end-when-creator-leaves"), true);
        if (root.node("cooldowns").virtual()) {
            root.node("cooldowns", Tiers.DEFAULT).set(300);
            root.node("cooldowns", "premium").set(180);
            root.node("cooldowns", "youtuber").set(60);
            changed = true;
        }
        if (root.node("costs").virtual()) {
            root.node("costs", Tiers.DEFAULT, "currency").set("coins");
            root.node("costs", Tiers.DEFAULT, "amount").set(0);
            changed = true;
        }
        if (root.node("lifetime-tiers").virtual()) {
            root.node("lifetime-tiers", "youtuber").set(300);
            changed = true;
        }
        if (root.node("max-joins-tiers").virtual()) {
            root.node("max-joins-tiers").raw(new LinkedHashMap<>());
            changed = true;
        }
        changed |= setIfAbsent(root.node("join", "same-server"), true);
        changed |= setIfAbsent(root.node("join", "joinable-state-only"), true);
        changed |= setIfAbsent(root.node("join", "permission"), "");
        changed |= setIfAbsent(root.node("join", "arrival-timeout-seconds"), 300);
        changed |= setIfAbsent(root.node("join", "notify-creator"), true);
        changed |= setIfAbsent(root.node("audience", "scope"), "groups");
        changed |= setListIfAbsent(root.node("audience", "groups"), List.of("lobby"));
        changed |= setListIfAbsent(root.node("audience", "only"), List.of());
        changed |= setIfAbsent(root.node("audience", "permission"), "");
        changed |= setIfAbsent(root.node("audience", "skip-same-world"), true);
        changed |= setIfAbsent(root.node("broadcast", "title"), false);
        changed |= setIfAbsent(root.node("broadcast", "sound"), "entity.experience_orb.pickup");
        changed |= setIfAbsent(root.node("broadcast", "volume"), 1.0D);
        changed |= setIfAbsent(root.node("broadcast", "pitch"), 1.2D);
        changed |= setIfAbsent(root.node("face", "enabled"), true);
        changed |= setIfAbsent(root.node("face", "symbol"), ChatFace.SYMBOL);
        changed |= setIfAbsent(root.node("face", "without-skin"), "default");
        changed |= setIfAbsent(root.node("face", "blank-line-before"), true);
        changed |= setIfAbsent(root.node("face", "blank-line-after"), true);
        changed |= setIfAbsent(root.node("face", "gap"), " ");
        if (root.node("face", "rows").virtual()) {
            root.node("face", "rows", "2").set("joinme.face.title");
            root.node("face", "rows", "4").set("joinme.face.details");
            root.node("face", "rows", "5").set("joinme.face.state");
            root.node("face", "rows", "7").set("joinme.face.button");
            changed = true;
        }
        changed |= setIfAbsent(root.node("face", "cache-size"), 512);
        changed |= setIfAbsent(root.node("face", "disk-cache"), true);
        changed |= setIfAbsent(root.node("face", "download-timeout-millis"), 3_000);
        return changed;
    }

    private static Map<String, Long> seconds(ConfigurationNode node, String path, long minimum,
                                             List<String> problems) {
        Map<String, Long> values = new LinkedHashMap<>();
        node.childrenMap().forEach((key, child) -> {
            String name = key.toString().toLowerCase(Locale.ROOT);
            if (!TIER.matcher(name).matches()) {
                problems.add(path + "." + key + " is no tier name, use " + TIER.pattern());
                return;
            }
            values.put(name, Math.clamp(child.getLong(minimum), minimum, MAX_SECONDS));
        });
        return values;
    }

    private static Set<String> groups(ConfigurationNode node) {
        Set<String> groups = new LinkedHashSet<>();
        for (String value : strings(node)) {
            if (!value.isBlank()) {
                groups.add(value.strip().toLowerCase(Locale.ROOT));
            }
        }
        return groups;
    }

    private static List<String> strings(ConfigurationNode node) {
        try {
            List<String> values = node.getList(String.class);
            return values == null ? List.of() : values;
        } catch (SerializationException e) {
            String single = node.getString("");
            return single.isBlank() ? List.of() : List.of(single);
        }
    }

    private static CommentedConfigurationNode filled() {
        CommentedConfigurationNode root = CommentedConfigurationNode.root();
        try {
            fillDefaults(root);
        } catch (SerializationException e) {
            throw new IllegalStateException(e);
        }
        return root;
    }

    private static boolean setIfAbsent(ConfigurationNode node, Object value) throws SerializationException {
        if (!node.virtual()) {
            return false;
        }
        node.set(value);
        return true;
    }

    private static boolean setListIfAbsent(ConfigurationNode node, List<String> value) throws SerializationException {
        if (!node.virtual()) {
            return false;
        }
        node.setList(String.class, value);
        return true;
    }

    private static YamlConfigurationLoader loader(Path file) {
        return YamlConfigurationLoader.builder()
                .path(file)
                .nodeStyle(NodeStyle.BLOCK)
                .indent(2)
                .build();
    }
}
