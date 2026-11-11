package net.prismarin.core.nick;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.prismarin.api.server.GameState;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.shared.config.YamlComments;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public record NickSettings(NickPolicy policy, Set<GameState> roundStates, int actionBarSeconds, Names names,
                           Skins skins, FakeRank fakeRank, NickWorlds worlds, List<String> problems) {

    public static final String FILE_NAME = "nick.yml";
    public static final int DEFAULT_ACTION_BAR_SECONDS = 3;

    public boolean actionBar() {
        return actionBarSeconds != 0;
    }

    static final List<String> DEFAULT_SKIN_POOL = List.of(
            "Notch", "jeb_", "Dinnerbone", "Grumm", "Searge", "slicedlime", "kingbdogz", "C418");
    static final List<String> DEFAULT_BLOCKED = List.of("admin", "owner", "staff", "moderator", "support");

    private static final NickPolicy.Where DEFAULT_WHERE = NickPolicy.Where.EVERYWHERE;
    private static final NickPolicy.When DEFAULT_WHEN = NickPolicy.When.ALWAYS;
    private static final List<String> DEFAULT_LOBBY_GROUPS = List.of(NetworkServer.GROUP_LOBBY);
    private static final List<String> DEFAULT_ROUND_GROUPS = List.of("game");
    private static final List<String> DEFAULT_ROUND_STATES = List.of("running");
    private static final int DEFAULT_CLAIM_EXPIRY_DAYS = 30;
    private static final int DEFAULT_REFRESH_HOURS = 72;
    private static final String DEFAULT_FAKE_GROUP = "default";
    private static final int DEFAULT_LUCKPERMS_PRIORITY = 1_000_000;

    static final List<String> HEADER = Stream.concat(Stream.of(
            "Nick settings of this server. Every server reads its own copy, so lobbies and game servers",
            "can behave differently. Commands: /nick, /nick <name>, /nick auto, /unnick and /realname <nick>.",
            ""), YamlComments.TEXT_HELP.stream()).toList();

    static final Map<String, List<String>> COMMENTS = comments();

    public NickSettings {
        roundStates = Set.copyOf(roundStates);
        problems = List.copyOf(problems);
    }

    public static NickSettings defaults() {
        return read(filled(), List.of());
    }

    public static NickSettings load(Path file) {
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
                return read(filled(), List.of(FILE_NAME + " could not be read, using the defaults: " + e.getMessage()));
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

    static NickSettings read(ConfigurationNode root, List<String> initialProblems) {
        List<String> problems = new ArrayList<>(initialProblems);
        NickPolicy.Where where = NickPolicy.Where.parse(root.node("where").getString(DEFAULT_WHERE.key()))
                .orElseGet(() -> {
                    problems.add("where must be everywhere, non-lobby or round-servers, using " + DEFAULT_WHERE.key());
                    return DEFAULT_WHERE;
                });
        NickPolicy.When when = NickPolicy.When.parse(root.node("when").getString(DEFAULT_WHEN.key()))
                .orElseGet(() -> {
                    problems.add("when must be always or in-round, using " + DEFAULT_WHEN.key());
                    return DEFAULT_WHEN;
                });
        NickPolicy policy = new NickPolicy(where, when, root.node("auto-nick").getBoolean(true),
                Set.copyOf(strings(root.node("lobby-groups"), DEFAULT_LOBBY_GROUPS)),
                Set.copyOf(strings(root.node("round-groups"), DEFAULT_ROUND_GROUPS)));
        Set<GameState> roundStates = EnumSet.noneOf(GameState.class);
        for (String value : strings(root.node("round-states"), DEFAULT_ROUND_STATES)) {
            try {
                roundStates.add(GameState.valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                problems.add("round-states contains the unknown state '" + value + "'");
            }
        }
        ConfigurationNode namesNode = root.node("names");
        List<String> preferred = validNames(strings(namesNode.node("list"), List.of()), "names.list", problems);
        Names names = new Names(namesNode.node("generator").getBoolean(true), preferred,
                strings(namesNode.node("blocked"), DEFAULT_BLOCKED),
                Math.max(1, namesNode.node("claim-expiry-days").getInt(DEFAULT_CLAIM_EXPIRY_DAYS)));
        ConfigurationNode skinsNode = root.node("skins");
        SkinMode mode = SkinMode.parse(skinsNode.node("mode").getString(SkinMode.POOL.key()));
        if (mode == null) {
            problems.add("skins.mode must be pool, own or default, using pool");
            mode = SkinMode.POOL;
        }
        Skins skins = new Skins(mode, validNames(strings(skinsNode.node("pool"), DEFAULT_SKIN_POOL), "skins.pool",
                problems), Math.max(1, skinsNode.node("refresh-hours").getInt(DEFAULT_REFRESH_HOURS)));
        ConfigurationNode rank = root.node("fake-rank");
        FakeRank fakeRank = new FakeRank(
                rank.node("group").getString(DEFAULT_FAKE_GROUP).trim(),
                rank.node("prefix").getString(""),
                rank.node("name-color").getString("").trim(),
                rank.node("weight").getInt(-1),
                strings(rank.node("perks"), List.of()),
                rank.node("luckperms-disguise").getBoolean(true),
                rank.node("luckperms-priority").getInt(DEFAULT_LUCKPERMS_PRIORITY));
        if (!fakeRank.nameColor().isEmpty() && FakeRankResolver.color(fakeRank.nameColor()).isEmpty()) {
            problems.add("fake-rank.name-color '" + fakeRank.nameColor() + "' is not a colour name such as gray");
        }
        int actionBarSeconds = root.node("action-bar").virtual() || root.node("action-bar").getBoolean(true)
                ? root.node("action-bar-seconds").getInt(DEFAULT_ACTION_BAR_SECONDS)
                : 0;
        ConfigurationNode worldsNode = root.node("worlds");
        NickWorlds worlds = new NickWorlds(worldsNode.node("enabled").getBoolean(false),
                NickWorlds.Mode.parse(worldsNode.node("mode").getString("whitelist")),
                Set.copyOf(strings(worldsNode.node("list"), List.of())),
                worldsNode.node("link-dimensions").getBoolean(true));
        return new NickSettings(policy, roundStates, actionBarSeconds, names, skins, fakeRank, worlds, problems);
    }

    static boolean fillDefaults(ConfigurationNode root) throws SerializationException {
        boolean changed = false;
        changed |= setIfAbsent(root.node("where"), DEFAULT_WHERE.key());
        changed |= setIfAbsent(root.node("when"), DEFAULT_WHEN.key());
        changed |= setIfAbsent(root.node("auto-nick"), true);
        changed |= setListIfAbsent(root.node("lobby-groups"), DEFAULT_LOBBY_GROUPS);
        changed |= setListIfAbsent(root.node("round-groups"), DEFAULT_ROUND_GROUPS);
        changed |= setListIfAbsent(root.node("round-states"), DEFAULT_ROUND_STATES);
        changed |= setIfAbsent(root.node("action-bar-seconds"), DEFAULT_ACTION_BAR_SECONDS);
        changed |= setIfAbsent(root.node("worlds", "enabled"), false);
        changed |= setIfAbsent(root.node("worlds", "mode"), NickWorlds.Mode.WHITELIST.key());
        changed |= setListIfAbsent(root.node("worlds", "list"), List.of("world"));
        changed |= setIfAbsent(root.node("worlds", "link-dimensions"), true);
        changed |= setIfAbsent(root.node("names", "generator"), true);
        changed |= setListIfAbsent(root.node("names", "list"), List.of());
        changed |= setListIfAbsent(root.node("names", "blocked"), DEFAULT_BLOCKED);
        changed |= setIfAbsent(root.node("names", "claim-expiry-days"), DEFAULT_CLAIM_EXPIRY_DAYS);
        changed |= setIfAbsent(root.node("skins", "mode"), SkinMode.POOL.key());
        changed |= setListIfAbsent(root.node("skins", "pool"), DEFAULT_SKIN_POOL);
        changed |= setIfAbsent(root.node("skins", "refresh-hours"), DEFAULT_REFRESH_HOURS);
        changed |= setIfAbsent(root.node("fake-rank", "group"), DEFAULT_FAKE_GROUP);
        changed |= setIfAbsent(root.node("fake-rank", "prefix"), "");
        changed |= setIfAbsent(root.node("fake-rank", "name-color"), "");
        changed |= setIfAbsent(root.node("fake-rank", "weight"), -1);
        changed |= setListIfAbsent(root.node("fake-rank", "perks"), List.of());
        changed |= setIfAbsent(root.node("fake-rank", "luckperms-disguise"), true);
        changed |= setIfAbsent(root.node("fake-rank", "luckperms-priority"), DEFAULT_LUCKPERMS_PRIORITY);
        return changed;
    }

    private static ConfigurationNode filled() {
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

    private static List<String> strings(ConfigurationNode node, List<String> fallback) {
        if (node.virtual()) {
            return fallback;
        }
        if (!node.isList()) {
            String single = node.getString();
            return single == null || single.isBlank() ? List.of() : List.of(single.trim());
        }
        List<String> values = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            String value = child.getString();
            if (value != null && !value.isBlank()) {
                values.add(value.trim());
            }
        }
        return List.copyOf(values);
    }

    private static List<String> validNames(List<String> names, String key, List<String> problems) {
        Set<String> valid = new LinkedHashSet<>();
        for (String name : names) {
            if (NickNames.validFormat(name)) {
                valid.add(name);
            } else {
                problems.add(key + " contains '" + name + "', which is not a valid Minecraft name");
            }
        }
        return List.copyOf(valid);
    }

    private static Map<String, List<String>> comments() {
        Map<String, List<String>> comments = new LinkedHashMap<>();
        comments.put("where", List.of(
                "Where nicks are shown: everywhere, non-lobby (every server outside lobby-groups) or",
                "round-servers (servers in round-groups and servers that report a game state)."));
        comments.put("when", List.of(
                "When a nick is shown: always, or in-round (only while the player is in a round, the real",
                "name comes back when the round is over)."));
        comments.put("auto-nick", List.of(
                "Players with prismarin.nick.auto who turned on /nick auto get a random nick when they join",
                "a server (when: always) or start a round (when: in-round) where nicks are shown."));
        comments.put("lobby-groups", List.of("Server groups from servers.yml that count as lobbies."));
        comments.put("round-groups", List.of("Server groups from servers.yml that count as round based game servers."));
        comments.put("round-states", List.of(
                "Game states that mean a round runs on this server: waiting, starting, running or ending.",
                "Add starting to nick players during the countdown, before most game plugins build their teams.",
                "Multi arena plugins mark single players through NickService#markInRound instead."));
        comments.put("action-bar-seconds", List.of(
                "Shows the nick in the action bar for this many seconds once it becomes visible on this server,",
                "not during a round and not while vanished. 0 turns it off, -1 keeps it on all the time."));
        comments.put("worlds", List.of(
                "For servers with several worlds instead of several servers: decide per world whether nicks",
                "are shown. Players are nicked or revealed within half a second after changing worlds."));
        comments.put("worlds.mode", List.of(
                "whitelist shows nicks only in the listed worlds, blacklist everywhere except the listed worlds."));
        comments.put("worlds.link-dimensions", List.of(
                "true treats world, world_nether and world_the_end as one world, so listing one of them",
                "covers all three. Set false to decide for every dimension on its own."));
        comments.put("names.list", List.of("Preferred nicks, tried in random order before the generator."));
        comments.put("names.generator", List.of("Builds names such as SilentFox42 when no listed name is free."));
        comments.put("names.blocked", List.of("A nick must not contain any of these words, case is ignored."));
        comments.put("names.claim-expiry-days", List.of(
                "The nick of a player who has not been on any server for this many days is free again."));
        comments.put("skins.mode", List.of(
                "pool: a random skin from the pool, own: the player keeps his skin, default: Steve or Alex."));
        comments.put("skins.pool", List.of(
                "Mojang user names whose current skin is used. Every skin is fetched once and shared",
                "with the other servers through the database. Pick accounts with plain skins.",
                "A pool skin that is not fetched yet shows as the default skin, never as the real one."));
        comments.put("skins.refresh-hours", List.of("Pool skins older than this are fetched again."));
        comments.put("fake-rank", List.of(
                "The rank nicked players appear to have in chat, tab list, name tag and sidebar.",
                "Their real permissions stay untouched."));
        comments.put("fake-rank.group", List.of(
                "LuckPerms group whose prefix, weight and name-color meta the fake rank copies."));
        comments.put("fake-rank.prefix", List.of(
                "Overrides the prefix. Empty: the prefix of the group, without LuckPerms rank.default-prefix.",
                "If a LuckPerms group shows the same text, nicked players sort and colour like that group."));
        comments.put("fake-rank.name-color", List.of(
                "Overrides the name colour, for example gray. Empty: the name-color meta of the group, else gray."));
        comments.put("fake-rank.weight", List.of(
                "Tab list sort weight. -1: the weight of the group above, or of the group whose prefix matches",
                "fake-rank.prefix, so nicked players stand between the real members of the rank they show."));
        comments.put("fake-rank.perks", List.of(
                "Cosmetic PRISMARIN perks nicked players keep, for example prismarin.chat.format.",
                "With LuckPerms the perks of the group above count as well."));
        comments.put("fake-rank.luckperms-disguise", List.of(
                "Adds a temporary LuckPerms prefix and name-color meta that is never saved, so TAB and chat",
                "plugins that read LuckPerms show the fake rank as well.",
                "TAB sorts by the real LuckPerms group. Set its sorting-types to",
                "PLACEHOLDER_HIGH_TO_LOW:%prismarin_rank_weight% so nicked players sort by the fake rank."));
        comments.put("fake-rank.luckperms-priority", List.of(
                "Priority of that prefix. It has to be higher than the priority of every real prefix."));
        return Map.copyOf(comments);
    }

    public record Names(boolean generator, List<String> list, List<String> blocked, int claimExpiryDays) {

        public Names {
            list = List.copyOf(list);
            blocked = List.copyOf(blocked);
        }

        public long claimExpiryMillis() {
            return claimExpiryDays * 24L * 60L * 60L * 1000L;
        }
    }

    public record Skins(SkinMode mode, List<String> pool, int refreshHours) {

        public Skins {
            pool = List.copyOf(pool);
        }

        public long refreshMillis() {
            return refreshHours * 60L * 60L * 1000L;
        }
    }

    public record FakeRank(String group, String prefix, String nameColor, int weight, List<String> perks,
                           boolean luckPermsDisguise, int luckPermsPriority) {

        public FakeRank {
            perks = List.copyOf(perks);
        }
    }

    public enum SkinMode {
        POOL("pool"),
        OWN("own"),
        DEFAULT("default");

        private final String key;

        SkinMode(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        static SkinMode parse(String value) {
            return NickPolicy.parseKey(values(), value, SkinMode::key).orElse(null);
        }
    }
}
