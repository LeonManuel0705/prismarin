package net.prismarin.core.reward;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.prismarin.api.admin.NoticeText;
import net.prismarin.api.reward.Reward;
import net.prismarin.shared.config.YamlComments;
import net.prismarin.shared.reward.DailySchedule;
import net.prismarin.shared.reward.RewardConfig;
import net.prismarin.shared.time.DayClock;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public record DailyRewardSettings(DayClock clock, int graceDays, boolean remindOnJoin, int reminderDelaySeconds,
                                  boolean profileEntry, DailySchedule schedule, List<String> problems) {

    public static final String FILE_NAME = "daily-rewards.yml";

    static final int DEFAULT_CYCLE = 7;
    static final int DEFAULT_REMINDER_DELAY = 5;

    private static final List<String> HEADER = Stream.concat(Stream.of(
            "Daily rewards with a login streak. Players claim once per day with /daily or in the lobby profile.",
            "With MONGO or MYSQL storage the claim and the streak count for the whole network, a second server",
            "never pays the same day twice. With LOCAL or SQLITE every server counts on its own.",
            "Every server reads its own copy, keep it the same on every server that hands out rewards.",
            "",
            "A reward has currencies (any currency of economy.currencies), xp, levels, commands (run by the",
            "console, %player%, %uuid%, %streak% and %day% are replaced), message (a text, a message key or one",
            "text per language) and custom (rewards other plugins register through the API).",
            ""), YamlComments.TEXT_HELP.stream()).toList();

    private static final Map<String, List<String>> COMMENTS = Map.ofEntries(
            Map.entry("time-zone", List.of(
                    "Time zone of the daily reset, for example Europe/Berlin.",
                    "server uses the time zone of the machine.")),
            Map.entry("reset-time", List.of("Time of day a new reward becomes available, for example 00:00 or 06:00.")),
            Map.entry("grace-days", List.of(
                    "Days a player may miss without losing the streak. 0: missing one day starts again at day 1.")),
            Map.entry("reminder", List.of("Tells players who join the network that today's reward is still waiting,",
                    "not after a switch between servers.")),
            Map.entry("reminder.delay-seconds", List.of("Seconds after the join before the reminder appears.")),
            Map.entry("profile-entry", List.of("Shows the daily reward in the lobby profile menu, claim by click.")),
            Map.entry("cycle", List.of(
                    "Length of the reward cycle in days. Day 8 of a streak gives the reward of day 1 again.")),
            Map.entry("days", List.of("Reward per day of the cycle. Days that are missing give the default reward.")),
            Map.entry("default", List.of("Reward for every day of the cycle that has no entry under days.")),
            Map.entry("milestones", List.of(
                    "Extra rewards when the streak reaches exactly this many days, on top of the day's reward.")));

    public DailyRewardSettings {
        problems = List.copyOf(problems);
    }

    public static DailyRewardSettings load(Path file) {
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

    public static DailyRewardSettings defaults() {
        return read(filled(), List.of());
    }

    static DailyRewardSettings read(ConfigurationNode root, List<String> initial) {
        List<String> problems = new ArrayList<>(initial);
        String zoneText = root.node("time-zone").getString(DayClock.SERVER_ZONE);
        ZoneId zone = DayClock.zone(zoneText).orElseGet(() -> {
            problems.add("time-zone '" + zoneText + "' is unknown, using the server time zone");
            return ZoneId.systemDefault();
        });
        String timeText = root.node("reset-time").getString("00:00");
        LocalTime reset = DayClock.time(timeText).orElseGet(() -> {
            problems.add("reset-time '" + timeText + "' is no time such as 00:00, using 00:00");
            return LocalTime.MIDNIGHT;
        });
        int grace = Math.clamp(root.node("grace-days").getInt(0), 0, 30);
        int cycle = Math.clamp(root.node("cycle").getInt(DEFAULT_CYCLE), 1, 366);
        Map<Integer, Reward> days = new LinkedHashMap<>();
        root.node("days").childrenMap().forEach((key, node) -> number(key, "days", problems).ifPresent(day -> {
            if (day > cycle) {
                problems.add("days." + day + " is beyond the cycle of " + cycle + " days and is never given");
            }
            days.put(day, RewardConfig.read(node, "days." + day, problems));
        }));
        Map<Integer, Reward> milestones = new LinkedHashMap<>();
        root.node("milestones").childrenMap().forEach((key, node) -> number(key, "milestones", problems)
                .ifPresent(day -> milestones.put(day, RewardConfig.read(node, "milestones." + day, problems))));
        DailySchedule schedule = new DailySchedule(cycle, days,
                RewardConfig.read(root.node("default"), "default", problems), milestones);
        return new DailyRewardSettings(new DayClock(zone, reset), grace,
                root.node("reminder", "on-join").getBoolean(true),
                Math.clamp(root.node("reminder", "delay-seconds").getInt(DEFAULT_REMINDER_DELAY), 0, 300),
                root.node("profile-entry").getBoolean(true), schedule, problems);
    }

    static boolean fillDefaults(ConfigurationNode root) throws SerializationException {
        boolean changed = false;
        changed |= setIfAbsent(root.node("time-zone"), DayClock.SERVER_ZONE);
        changed |= setIfAbsent(root.node("reset-time"), "00:00");
        changed |= setIfAbsent(root.node("grace-days"), 0);
        changed |= setIfAbsent(root.node("reminder", "on-join"), true);
        changed |= setIfAbsent(root.node("reminder", "delay-seconds"), DEFAULT_REMINDER_DELAY);
        changed |= setIfAbsent(root.node("profile-entry"), true);
        changed |= setIfAbsent(root.node("cycle"), DEFAULT_CYCLE);
        if (root.node("days").virtual()) {
            long[] coins = {50L, 75L, 100L, 125L, 150L, 200L, 300L};
            for (int day = 1; day <= DEFAULT_CYCLE; day++) {
                Reward reward = Reward.currency("coins", coins[day - 1]).plus(Reward.experience(25L * day));
                if (day == DEFAULT_CYCLE) {
                    reward = reward.plus(Reward.currency("gems", 5L)).plus(Reward.message(texts(
                            "%success_prefix%A full week in a row, here are <color:#2DD4BF>5 gems</color> "
                                    + "on top.</gray>",
                            "%success_prefix%Eine ganze Woche am Stück, dafür gibt es <color:#2DD4BF>5 Gems</color> "
                                    + "dazu.</gray>")));
                }
                RewardConfig.write(root.node("days", Integer.toString(day)), reward);
            }
            changed = true;
        }
        if (root.node("default").virtual()) {
            RewardConfig.write(root.node("default"), Reward.currency("coins", 50L));
            changed = true;
        }
        if (root.node("milestones").virtual()) {
            RewardConfig.write(root.node("milestones", "30"), Reward.currency("gems", 25L)
                    .plus(Reward.message(texts(
                            "%success_prefix%30 days in a row. Thank you for playing with us.</gray>",
                            "%success_prefix%30 Tage am Stück. Danke, dass du bei uns spielst.</gray>")))
                    .plus(new Reward(Map.of(), 0L, 0, List.of(), List.of(), Map.of("cosmetic", "streak-flame"))));
            RewardConfig.write(root.node("milestones", "100"), Reward.currency("gems", 100L));
            changed = true;
        }
        return changed;
    }

    private static Optional<Integer> number(Object key, String path, List<String> problems) {
        String text = key.toString().strip();
        if (!text.matches("\\d{1,6}") || Integer.parseInt(text) < 1) {
            problems.add(path + "." + key + " is no day number of 1 or more");
            return Optional.empty();
        }
        return Optional.of(Integer.parseInt(text));
    }

    private static NoticeText texts(String english, String german) {
        Map<String, String> templates = new LinkedHashMap<>();
        templates.put("en", english);
        templates.put("de", german);
        return NoticeText.of(templates);
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

    private static YamlConfigurationLoader loader(Path file) {
        return YamlConfigurationLoader.builder()
                .path(file)
                .nodeStyle(NodeStyle.BLOCK)
                .indent(2)
                .build();
    }
}
