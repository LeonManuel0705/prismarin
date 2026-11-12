package net.prismarin.velocity.vote;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.prismarin.api.reward.Reward;
import net.prismarin.api.reward.VoteSite;
import net.prismarin.shared.config.YamlComments;
import net.prismarin.shared.reward.RewardConfig;
import net.prismarin.shared.reward.VoteRules;
import net.prismarin.shared.time.DayClock;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public record VoteSettings(List<VoteSite> sites, boolean votifier, boolean broadcast, Reminder reminder,
                           boolean streaks, DayClock clock, int graceDays, long keepMillis, VoteRules rules,
                           List<String> problems) {

    public static final String FILE_NAME = "votes.yml";

    static final int DEFAULT_KEEP_DAYS = 30;

    public record Reminder(boolean onJoin, int afterHours, int delaySeconds, int intervalMinutes) {

        public long afterMillis() {
            return afterHours * 3_600_000L;
        }
    }

    private static final List<String> HEADER = Stream.concat(Stream.of(
            "Vote rewards. Votes arrive through NuVotifier on this proxy (install it next to PRISMARIN) or from",
            "other plugins through prismarin.votes(). Every vote is queued here and handed out by the next",
            "PRISMARIN server the player is on with modules.vote-rewards, also after a restart or days later.",
            "/vote shows the sites below, staff with prismarin.vote.test count a test vote with /vote test.",
            "",
            "A reward has currencies (any currency of economy.currencies), xp, levels, commands (run by the",
            "console of the server that hands it out, %player%, %uuid%, %streak% and %service% are replaced),",
            "message (a text, a message key or one text per language) and custom (plugin rewards).",
            ""), YamlComments.TEXT_HELP.stream()).toList();

    private static final Map<String, List<String>> COMMENTS = Map.ofEntries(
            Map.entry("sites", List.of(
                    "Vote sites shown by /vote. service is the name the site sends through Votifier, the",
                    "site's own reward replaces the reward below for its votes. enabled: false hides a site.")),
            Map.entry("votifier", List.of("Counts the votes NuVotifier receives. Needs the NuVotifier plugin.")),
            Map.entry("broadcast", List.of("Tells everyone on the network when somebody voted.")),
            Map.entry("reminder", List.of("Reminds players who have not voted for a while.")),
            Map.entry("reminder.on-join", List.of("Once per visit, shortly after the player joined.")),
            Map.entry("reminder.after-hours", List.of("Hours since the last vote after which a player is reminded.")),
            Map.entry("reminder.interval-minutes", List.of("Reminds again every this many minutes, 0 turns it off.")),
            Map.entry("streak", List.of("Days in a row with at least one vote.")),
            Map.entry("streak.time-zone", List.of("Time zone of the day change, server uses the machine's.")),
            Map.entry("streak.grace-days", List.of("Days without a vote that do not end the streak.")),
            Map.entry("streak.milestones", List.of(
                    "Extra reward on the first vote of the day the streak reaches exactly this many days.")),
            Map.entry("reward", List.of("Reward for every vote from a site without its own reward.")),
            Map.entry("queue", List.of("Votes wait here until the player is on a server that hands out rewards.")),
            Map.entry("queue.keep-days", List.of("Votes older than this are dropped without a reward, 0 keeps them.")));

    public VoteSettings {
        sites = List.copyOf(sites);
        problems = List.copyOf(problems);
    }

    public Optional<VoteSite> site(String service) {
        if (service == null || service.isBlank()) {
            return Optional.empty();
        }
        String wanted = service.strip().toLowerCase(Locale.ROOT);
        return sites.stream().filter(site -> site.service().toLowerCase(Locale.ROOT).equals(wanted)
                || site.id().equalsIgnoreCase(wanted)).findFirst();
    }

    public static VoteSettings load(Path file) {
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

    public static VoteSettings defaults() {
        return read(filled(), List.of());
    }

    static VoteSettings read(ConfigurationNode root, List<String> initial) {
        List<String> problems = new ArrayList<>(initial);
        List<VoteSite> sites = new ArrayList<>();
        Map<String, Reward> siteRewards = new LinkedHashMap<>();
        root.node("sites").childrenMap().forEach((key, node) -> {
            String id = key.toString();
            if (!node.node("enabled").getBoolean(true)) {
                return;
            }
            String url = node.node("url").getString("");
            if (!url.startsWith("https://") && !url.startsWith("http://")) {
                problems.add("sites." + id + ".url must start with https://");
                return;
            }
            VoteSite site = new VoteSite(id, node.node("name").getString(id), url,
                    node.node("service").getString(id));
            sites.add(site);
            Reward reward = RewardConfig.read(node.node("reward"), "sites." + id + ".reward", problems);
            if (!reward.isEmpty()) {
                siteRewards.put(site.service(), reward);
            }
        });
        ConfigurationNode streak = root.node("streak");
        String zoneText = streak.node("time-zone").getString(DayClock.SERVER_ZONE);
        ZoneId zone = DayClock.zone(zoneText).orElseGet(() -> {
            problems.add("streak.time-zone '" + zoneText + "' is unknown, using the server time zone");
            return ZoneId.systemDefault();
        });
        String timeText = streak.node("reset-time").getString("00:00");
        LocalTime reset = DayClock.time(timeText).orElseGet(() -> {
            problems.add("streak.reset-time '" + timeText + "' is no time such as 00:00, using 00:00");
            return LocalTime.MIDNIGHT;
        });
        Map<Integer, Reward> milestones = new LinkedHashMap<>();
        streak.node("milestones").childrenMap().forEach((key, node) -> {
            String text = key.toString().strip();
            if (!text.matches("\\d{1,6}") || Integer.parseInt(text) < 1) {
                problems.add("streak.milestones." + key + " is no day number of 1 or more");
                return;
            }
            milestones.put(Integer.parseInt(text), RewardConfig.read(node, "streak.milestones." + text, problems));
        });
        VoteRules rules = new VoteRules(RewardConfig.read(root.node("reward"), "reward", problems), siteRewards,
                milestones);
        ConfigurationNode reminder = root.node("reminder");
        Reminder reminders = new Reminder(reminder.node("on-join").getBoolean(true),
                Math.clamp(reminder.node("after-hours").getInt(24), 1, 24 * 30),
                Math.clamp(reminder.node("delay-seconds").getInt(10), 0, 600),
                Math.clamp(reminder.node("interval-minutes").getInt(0), 0, 24 * 60));
        int keepDays = Math.clamp(root.node("queue", "keep-days").getInt(DEFAULT_KEEP_DAYS), 0, 3_650);
        return new VoteSettings(sites, root.node("votifier").getBoolean(true), root.node("broadcast").getBoolean(true),
                reminders, streak.node("enabled").getBoolean(true), new DayClock(zone, reset),
                Math.clamp(streak.node("grace-days").getInt(0), 0, 30), keepDays * 86_400_000L, rules, problems);
    }

    static boolean fillDefaults(ConfigurationNode root) throws SerializationException {
        boolean changed = false;
        ConfigurationNode sites = root.node("sites");
        if (sites.virtual()) {
            ConfigurationNode example = sites.node("example");
            example.node("enabled").set(false);
            example.node("name").set("Example list");
            example.node("url").set("https://example.com/vote/your-server");
            example.node("service").set("example.com");
            changed = true;
        }
        changed |= setIfAbsent(root.node("votifier"), true);
        changed |= setIfAbsent(root.node("broadcast"), true);
        changed |= setIfAbsent(root.node("reminder", "on-join"), true);
        changed |= setIfAbsent(root.node("reminder", "after-hours"), 24);
        changed |= setIfAbsent(root.node("reminder", "delay-seconds"), 10);
        changed |= setIfAbsent(root.node("reminder", "interval-minutes"), 0);
        changed |= setIfAbsent(root.node("streak", "enabled"), true);
        changed |= setIfAbsent(root.node("streak", "time-zone"), DayClock.SERVER_ZONE);
        changed |= setIfAbsent(root.node("streak", "reset-time"), "00:00");
        changed |= setIfAbsent(root.node("streak", "grace-days"), 0);
        if (root.node("streak", "milestones").virtual()) {
            RewardConfig.write(root.node("streak", "milestones", "7"), Reward.currency("gems", 5L));
            RewardConfig.write(root.node("streak", "milestones", "30"), Reward.currency("gems", 25L));
            changed = true;
        }
        if (root.node("reward").virtual()) {
            RewardConfig.write(root.node("reward"), Reward.currency("coins", 100L).plus(Reward.experience(50L)));
            changed = true;
        }
        changed |= setIfAbsent(root.node("queue", "keep-days"), DEFAULT_KEEP_DAYS);
        return changed;
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
