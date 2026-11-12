package net.prismarin.core.stats;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.prismarin.api.admin.NoticeText;
import net.prismarin.api.stats.StatDefinition;
import net.prismarin.api.stats.StatFormat;
import net.prismarin.api.stats.StatKeys;
import net.prismarin.api.stats.StatOrder;
import net.prismarin.shared.config.YamlComments;
import net.prismarin.shared.reward.RewardConfig;
import net.prismarin.shared.stats.DocumentStatsService;
import net.prismarin.shared.stats.GameConfig;
import net.prismarin.shared.stats.StatNumbers;
import net.prismarin.shared.stats.StatPeriods;
import net.prismarin.shared.time.DayClock;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public record StatsSettings(StatPeriods periods, int refreshSeconds, int cacheSize, boolean sharedRanks,
                            boolean showUnlisted, boolean profileEntry, int profileLimit, BuiltIn builtIn,
                            List<GameConfig> games, List<StatDefinition> definitions, Map<String, String> placeholders,
                            PlaceholderSync placeholderSync, Command command, long sourceTimeoutMillis,
                            List<String> problems) {

    public static final String FILE_NAME = "stats.yml";

    static final int DEFAULT_REFRESH_SECONDS = 60;
    static final int DEFAULT_CACHE_SIZE = 100;
    static final int DEFAULT_PROFILE_LIMIT = 6;
    static final int DEFAULT_SYNC_MINUTES = 5;
    static final int DEFAULT_JOIN_DELAY_SECONDS = 10;
    static final int DEFAULT_PAGE_SIZE = 10;
    static final int DEFAULT_GAME_LIST_ABOVE = 3;

    public record PlaceholderSync(int minutes, int joinDelaySeconds, StatNumbers.Decimal decimal) {

        public static final PlaceholderSync DEFAULT = new PlaceholderSync(DEFAULT_SYNC_MINUTES,
                DEFAULT_JOIN_DELAY_SECONDS, StatNumbers.Decimal.AUTO);

        public PlaceholderSync {
            minutes = Math.clamp(minutes, 0, 1_440);
            joinDelaySeconds = Math.clamp(joinDelaySeconds, 0, 300);
            decimal = decimal == null ? StatNumbers.Decimal.AUTO : decimal;
        }
    }

    public record Command(int pageSize, int gameListAbove) {

        public static final Command DEFAULT = new Command(DEFAULT_PAGE_SIZE, DEFAULT_GAME_LIST_ABOVE);

        public Command {
            pageSize = Math.clamp(pageSize, 3, 30);
            gameListAbove = Math.clamp(gameListAbove, 1, 50);
        }
    }

    public record BuiltIn(String votes, String dailyClaims, String dailyStreak) {

        public static final BuiltIn DEFAULT = new BuiltIn("votes", "daily.claims", "daily.streak");

        public Optional<String> votesKey() {
            return Optional.ofNullable(votes).filter(key -> !key.isEmpty());
        }

        public Optional<String> claimsKey() {
            return Optional.ofNullable(dailyClaims).filter(key -> !key.isEmpty());
        }

        public Optional<String> streakKey() {
            return Optional.ofNullable(dailyStreak).filter(key -> !key.isEmpty());
        }
    }

    private record Seed(String key, String english, String german, StatFormat format, StatOrder order,
                        boolean profile) {
    }

    private record GameSeed(String id, String english, String german, String icon, String color, int order,
                            List<String> featured) {
    }

    private static final List<GameSeed> GAME_SEEDS = List.of(
            new GameSeed(StatKeys.GENERAL, "General", "Allgemein", "NETHER_STAR", "#22D3EE", 0, List.of("votes")),
            new GameSeed("daily", "Daily rewards", "Tagesbelohnungen", "CHEST", "#FBBF24", 10, List.of("streak")),
            new GameSeed("example", "Example", "Beispiel", "DIAMOND_SWORD", "#F472B6", 20, List.of("wins")));

    private static final List<Seed> SEEDS = List.of(
            new Seed("votes", "Votes", "Stimmen", StatFormat.NUMBER, StatOrder.HIGHEST, true),
            new Seed("daily.streak", "Best login streak", "Beste Login-Serie", StatFormat.NUMBER, StatOrder.HIGHEST,
                    true),
            new Seed("daily.claims", "Daily rewards", "Tagesbelohnungen", StatFormat.NUMBER, StatOrder.HIGHEST, false),
            new Seed("example.wins", "Wins", "Siege", StatFormat.NUMBER, StatOrder.HIGHEST, true),
            new Seed("example.games", "Games played", "Gespielte Runden", StatFormat.NUMBER, StatOrder.HIGHEST,
                    false));

    private static final List<String> HEADER = Stream.concat(Stream.of(
            "Statistics and leaderboards. Game plugins count stats through prismarin.stats(), for example",
            "bedwars.wins or bedwars.kills. A key has lower case letters, digits, dots and dashes, the part",
            "before the first dot is the game (/stats <player> bedwars). Every server reads its own copy.",
            "With MONGO or MYSQL storage every server sees the same numbers, with LOCAL or SQLITE each its own.",
            "",
            "Stats of other plugins show up without any setup: pushed keys get a name made from the key",
            "(bedwars.final-kills is Final kills under Bedwars). Plugins with their own storage register a",
            "stat source, plugins without an API are read through PlaceholderAPI (placeholder under stats).",
            ""), YamlComments.TEXT_HELP.stream()).toList();

    private static final Map<String, List<String>> COMMENTS = Map.ofEntries(
            Map.entry("periods", List.of(
                    "Besides the all time value every stat keeps a weekly and a monthly total.",
                    "They start at 0 again when the week or the month begins.")),
            Map.entry("periods.time-zone", List.of(
                    "Time zone of the resets, for example Europe/Berlin. server uses the time zone of the machine.")),
            Map.entry("periods.reset-time", List.of("Time of day the new week and month begin, for example 00:00.")),
            Map.entry("periods.week-starts", List.of("First day of the week: monday, sunday, ...")),
            Map.entry("periods.weekly", List.of("false turns the weekly totals off.")),
            Map.entry("periods.monthly", List.of("false turns the monthly totals off.")),
            Map.entry("leaderboards", List.of(
                    "Top lists are built from one read of all stats and kept in memory, never read on every call.")),
            Map.entry("leaderboards.refresh-seconds", List.of(
                    "How often a top list that is in use is rebuilt. Lists nobody asked for in the last ten",
                    "minutes are not rebuilt.")),
            Map.entry("leaderboards.cache-size", List.of(
                    "Entries kept per top list. Ranks below it are counted from the values.")),
            Map.entry("leaderboards.shared-ranks", List.of(
                    "true gives equal values the same rank (1, 1, 3). false ranks the one who reached the",
                    "value first higher (1, 2, 3).")),
            Map.entry("show-unlisted", List.of(
                    "Shows stats in /stats that are not listed under stats below, with a name made from the key.")),
            Map.entry("sources", List.of("Stat sources of plugins that keep their stats in their own storage.")),
            Map.entry("sources.timeout-millis", List.of(
                    "Longest wait for a source. A source that takes longer is skipped and the stored values show.")),
            Map.entry("placeholder-sync-minutes", List.of(
                    "Stats with a placeholder are read for the players on this server when they join, when they",
                    "leave and every this many minutes, then written to the shared stats. The same run reads the",
                    "stat sources of these players again. 0 reads only on join and on leave.")),
            Map.entry("placeholder-join-delay-seconds", List.of(
                    "Wait after a join before the placeholders are read, so the other plugin can load its data.")),
            Map.entry("placeholder-decimal-separator", List.of(
                    "How a placeholder value is read: auto guesses from the text (1,234 and 1.234 are thousands,",
                    "1.5 and 1,5 are decimals), . or , fixes the decimal separator.")),
            Map.entry("command", List.of("Layout of /stats.")),
            Map.entry("command.page-size", List.of("Stat lines per page, 3 to 30.")),
            Map.entry("command.game-list-above", List.of(
                    "With more games than this, /stats first shows a clickable list of the games.")),
            Map.entry("profile", List.of("Stats entry in the lobby profile menu.")),
            Map.entry("profile.limit", List.of("At most this many stats with in-profile: true appear there.")),
            Map.entry("built-in", List.of(
                    "Stats PRISMARIN counts itself. An empty key turns one off.",
                    "votes: one per vote reward, daily-claims: one per daily reward,",
                    "daily-streak: the longest login streak.")),
            Map.entry("games", List.of(
                    "The games in /stats. Plugins can register the same through the API, values here win.",
                    "name: a text, a message key or one text per language. icon: a material for menus.",
                    "color: #RRGGBB or a colour name such as gold. order: lower comes first.",
                    "featured: stats shown first, with or without the game in front (wins or bedwars.wins).")),
            Map.entry("stats", List.of(
                    "Shown name, format and order per stat key.",
                    "name: a text, a message key or one text per language (en, de, ...).",
                    "format: number (1,234), plain (1234), duration (seconds as 1:02:03), millis (1:02.345)",
                    "or decimal (the value divided by 100, for example a K/D of 1.25 stored as 125).",
                    "order: highest (most wins first) or lowest (fastest time first).",
                    "in-stats shows it in /stats, in-profile in the lobby profile menu.",
                    "placeholder: reads the value through PlaceholderAPI, for example \"%bedwars_stats_wins%\".",
                    "Only servers where the placeholder works write the value, so others never overwrite it.")));

    public StatsSettings {
        games = List.copyOf(games);
        definitions = List.copyOf(definitions);
        placeholders = Collections.unmodifiableMap(new LinkedHashMap<>(placeholders));
        placeholderSync = placeholderSync == null ? PlaceholderSync.DEFAULT : placeholderSync;
        command = command == null ? Command.DEFAULT : command;
        problems = List.copyOf(problems);
    }

    public DocumentStatsService.Settings service() {
        return new DocumentStatsService.Settings(periods, refreshSeconds * 1000L, cacheSize, sharedRanks,
                showUnlisted, definitions, games, sourceTimeoutMillis);
    }

    public Optional<GameConfig> game(String id) {
        return games.stream().filter(game -> game.id().equals(id)).findFirst();
    }

    public List<String> gameIds() {
        return games.stream().map(GameConfig::id).toList();
    }

    public static StatsSettings load(Path file) {
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

    public static StatsSettings defaults() {
        return read(filled(), List.of());
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

    static StatsSettings read(ConfigurationNode root, List<String> initial) {
        List<String> problems = new ArrayList<>(initial);
        ConfigurationNode periods = root.node("periods");
        String zoneText = periods.node("time-zone").getString(DayClock.SERVER_ZONE);
        ZoneId zone = DayClock.zone(zoneText).orElseGet(() -> {
            problems.add("periods.time-zone '" + zoneText + "' is unknown, using the server time zone");
            return ZoneId.systemDefault();
        });
        String timeText = periods.node("reset-time").getString("00:00");
        LocalTime reset = DayClock.time(timeText).orElseGet(() -> {
            problems.add("periods.reset-time '" + timeText + "' is no time such as 00:00, using 00:00");
            return LocalTime.MIDNIGHT;
        });
        String weekText = periods.node("week-starts").getString("monday");
        DayOfWeek weekStart = StatPeriods.weekday(weekText).orElseGet(() -> {
            problems.add("periods.week-starts '" + weekText + "' is no weekday, using monday");
            return DayOfWeek.MONDAY;
        });
        StatPeriods statPeriods = new StatPeriods(new DayClock(zone, reset), weekStart,
                periods.node("weekly").getBoolean(true), periods.node("monthly").getBoolean(true));
        ConfigurationNode boards = root.node("leaderboards");
        int refresh = Math.clamp(boards.node("refresh-seconds").getInt(DEFAULT_REFRESH_SECONDS), 5, 3_600);
        int cache = Math.clamp(boards.node("cache-size").getInt(DEFAULT_CACHE_SIZE), 10, 1_000);
        List<GameConfig> games = new ArrayList<>();
        root.node("games").childrenMap().forEach((id, node) -> game(id.toString(), node, problems)
                .ifPresent(games::add));
        List<StatDefinition> definitions = new ArrayList<>();
        Map<String, String> placeholders = new LinkedHashMap<>();
        root.node("stats").childrenMap().forEach((key, node) -> {
            String raw = key.toString();
            if (!StatKeys.valid(raw)) {
                problems.add("stats." + raw + " is no valid stat key, use " + StatKeys.KEY.pattern());
                return;
            }
            String format = node.node("format").getString(StatFormat.NUMBER.id());
            String order = node.node("order").getString(StatOrder.HIGHEST.id());
            definitions.add(new StatDefinition(raw,
                    RewardConfig.text(node.node("name"), "stats." + raw + ".name", problems).orElse(null),
                    StatFormat.byId(format).orElseGet(() -> {
                        problems.add("stats." + raw + ".format '" + format + "' is unknown, using number");
                        return StatFormat.NUMBER;
                    }),
                    StatOrder.byId(order).orElseGet(() -> {
                        problems.add("stats." + raw + ".order must be highest or lowest, using highest");
                        return StatOrder.HIGHEST;
                    }),
                    node.node("in-stats").getBoolean(true), node.node("in-profile").getBoolean(false)));
            String placeholder = node.node("placeholder").getString("");
            if (!placeholder.isBlank()) {
                if (PlaceholderStats.identifier(placeholder).isPresent()) {
                    placeholders.put(StatKeys.key(raw), placeholder.strip());
                } else {
                    problems.add("stats." + raw + ".placeholder '" + placeholder + "' holds no PlaceholderAPI "
                            + "placeholder such as %bedwars_stats_wins%");
                }
            }
        });
        String separatorText = root.node("placeholder-decimal-separator").getString(StatNumbers.Decimal.AUTO.id());
        StatNumbers.Decimal separator = StatNumbers.Decimal.byId(separatorText).orElseGet(() -> {
            problems.add("placeholder-decimal-separator '" + separatorText + "' must be auto, . or ,, using auto");
            return StatNumbers.Decimal.AUTO;
        });
        PlaceholderSync sync = new PlaceholderSync(
                root.node("placeholder-sync-minutes").getInt(DEFAULT_SYNC_MINUTES),
                root.node("placeholder-join-delay-seconds").getInt(DEFAULT_JOIN_DELAY_SECONDS), separator);
        Command command = new Command(root.node("command", "page-size").getInt(DEFAULT_PAGE_SIZE),
                root.node("command", "game-list-above").getInt(DEFAULT_GAME_LIST_ABOVE));
        long timeout = Math.clamp(root.node("sources", "timeout-millis")
                .getLong(DocumentStatsService.DEFAULT_SOURCE_TIMEOUT_MILLIS), 250L, 30_000L);
        ConfigurationNode builtIn = root.node("built-in");
        BuiltIn keys = new BuiltIn(key(builtIn.node("votes"), BuiltIn.DEFAULT.votes(), "built-in.votes", problems),
                key(builtIn.node("daily-claims"), BuiltIn.DEFAULT.dailyClaims(), "built-in.daily-claims", problems),
                key(builtIn.node("daily-streak"), BuiltIn.DEFAULT.dailyStreak(), "built-in.daily-streak", problems));
        return new StatsSettings(statPeriods, refresh, cache, boards.node("shared-ranks").getBoolean(false),
                root.node("show-unlisted").getBoolean(true), root.node("profile", "enabled").getBoolean(true),
                Math.clamp(root.node("profile", "limit").getInt(DEFAULT_PROFILE_LIMIT), 1, 20), keys, games,
                definitions, placeholders, sync, command, timeout, problems);
    }

    private static Optional<GameConfig> game(String raw, ConfigurationNode node, List<String> problems) {
        if (!StatKeys.validGame(raw)) {
            problems.add("games." + raw + " is no valid game id, use " + StatKeys.GAME.pattern());
            return Optional.empty();
        }
        String path = "games." + raw;
        String icon = node.node("icon").getString();
        String color = node.node("color").getString();
        Integer order = node.node("order").virtual() ? null : node.node("order").getInt(0);
        List<String> featured = null;
        if (!node.node("featured").virtual()) {
            try {
                featured = node.node("featured").getList(String.class, List.of());
            } catch (SerializationException e) {
                problems.add(path + ".featured must be a list of stat keys");
            }
        }
        GameConfig config = attempt(path, problems, raw, RewardConfig.text(node.node("name"), path + ".name",
                problems).orElse(null), icon, color, order, featured);
        return Optional.ofNullable(config);
    }

    private static GameConfig attempt(String path, List<String> problems, String id,
                                      NoticeText name, String icon, String color,
                                      Integer order, List<String> featured) {
        try {
            return new GameConfig(id, name, icon, color, order, featured);
        } catch (IllegalArgumentException e) {
            problems.add(path + ": " + e.getMessage() + ", ignoring icon, color and featured");
            return new GameConfig(id, name, null, null, order, null);
        }
    }

    static boolean fillDefaults(ConfigurationNode root) throws SerializationException {
        boolean changed = false;
        changed |= setIfAbsent(root.node("periods", "time-zone"), DayClock.SERVER_ZONE);
        changed |= setIfAbsent(root.node("periods", "reset-time"), "00:00");
        changed |= setIfAbsent(root.node("periods", "week-starts"), "monday");
        changed |= setIfAbsent(root.node("periods", "weekly"), true);
        changed |= setIfAbsent(root.node("periods", "monthly"), true);
        changed |= setIfAbsent(root.node("leaderboards", "refresh-seconds"), DEFAULT_REFRESH_SECONDS);
        changed |= setIfAbsent(root.node("leaderboards", "cache-size"), DEFAULT_CACHE_SIZE);
        changed |= setIfAbsent(root.node("leaderboards", "shared-ranks"), false);
        changed |= setIfAbsent(root.node("show-unlisted"), true);
        changed |= setIfAbsent(root.node("sources", "timeout-millis"),
                DocumentStatsService.DEFAULT_SOURCE_TIMEOUT_MILLIS);
        changed |= setIfAbsent(root.node("placeholder-sync-minutes"), DEFAULT_SYNC_MINUTES);
        changed |= setIfAbsent(root.node("placeholder-join-delay-seconds"), DEFAULT_JOIN_DELAY_SECONDS);
        changed |= setIfAbsent(root.node("placeholder-decimal-separator"), StatNumbers.Decimal.AUTO.id());
        changed |= setIfAbsent(root.node("command", "page-size"), DEFAULT_PAGE_SIZE);
        changed |= setIfAbsent(root.node("command", "game-list-above"), DEFAULT_GAME_LIST_ABOVE);
        changed |= setIfAbsent(root.node("profile", "enabled"), true);
        changed |= setIfAbsent(root.node("profile", "limit"), DEFAULT_PROFILE_LIMIT);
        changed |= setIfAbsent(root.node("built-in", "votes"), BuiltIn.DEFAULT.votes());
        changed |= setIfAbsent(root.node("built-in", "daily-claims"), BuiltIn.DEFAULT.dailyClaims());
        changed |= setIfAbsent(root.node("built-in", "daily-streak"), BuiltIn.DEFAULT.dailyStreak());
        ConfigurationNode games = root.node("games");
        if (games.virtual()) {
            for (GameSeed seed : GAME_SEEDS) {
                ConfigurationNode node = games.node(seed.id());
                node.node("name", "en").set(seed.english());
                node.node("name", "de").set(seed.german());
                node.node("icon").set(seed.icon());
                node.node("color").set(seed.color());
                node.node("order").set(seed.order());
                node.node("featured").setList(String.class, seed.featured());
            }
            changed = true;
        }
        ConfigurationNode stats = root.node("stats");
        if (stats.virtual()) {
            for (Seed seed : SEEDS) {
                ConfigurationNode node = stats.node(seed.key());
                node.node("name", "en").set(seed.english());
                node.node("name", "de").set(seed.german());
                node.node("format").set(seed.format().id());
                node.node("order").set(seed.order().id());
                node.node("in-stats").set(true);
                node.node("in-profile").set(seed.profile());
            }
            changed = true;
        }
        return changed;
    }

    private static String key(ConfigurationNode node, String fallback, String path, List<String> problems) {
        String value = node.getString(fallback);
        if (value == null || value.isBlank()) {
            return "";
        }
        if (!StatKeys.valid(value)) {
            problems.add(path + " '" + value + "' is no valid stat key, using " + fallback);
            return fallback;
        }
        return StatKeys.key(value);
    }

    private static boolean setIfAbsent(ConfigurationNode node, Object value) throws SerializationException {
        if (!node.virtual()) {
            return false;
        }
        node.set(value);
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
