package net.prismarin.shared.chatfilter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.prismarin.api.moderation.ChatFilterAction;
import net.prismarin.api.moderation.ChatFilterMatch;
import net.prismarin.api.moderation.ChatFilterRule;
import net.prismarin.shared.config.YamlComments;
import net.prismarin.shared.moderation.Durations;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public record ChatFilterSettings(String replacement, Words words, Caps caps, Links links, Repeat repeat, Flood flood,
                                 Map<String, RulePolicy> custom, ProxyCommands commands, List<String> problems) {

    public static final String FILE_NAME = "chat-filter.yml";
    public static final String WORDS = "words";
    public static final String CAPS = "caps";
    public static final String LINKS = "links";
    public static final String REPEAT = "repeat";
    public static final String FLOOD = "flood";
    public static final List<String> BUILT_IN = List.of(FLOOD, REPEAT, WORDS, LINKS, CAPS);

    private static final Pattern REFERENCE = Pattern.compile("[a-z0-9_-]{0,32}");
    private static final Pattern CHANNEL = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final long MIN_WINDOW = 1_000L;
    private static final long MAX_WINDOW = Duration.ofDays(7).toMillis();
    private static final int MAX_VIOLATIONS = 1_000;

    public record Escalation(boolean enabled, int violations, long windowMillis, String template, boolean report,
                             String reportReason) {

        public static final Escalation OFF = new Escalation(false, 3, 600_000L, "", false, "");

        public Escalation {
            violations = Math.clamp(violations, 1, MAX_VIOLATIONS);
            windowMillis = Math.clamp(windowMillis, MIN_WINDOW, MAX_WINDOW);
            template = reference(template);
            reportReason = reference(reportReason);
        }

        public boolean punishes() {
            return enabled && !template.isEmpty();
        }

        private static String reference(String value) {
            String clean = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            return REFERENCE.matcher(clean).matches() ? clean : "";
        }
    }

    public record RulePolicy(boolean enabled, ChatFilterAction action, boolean notifyStaff, boolean audit,
                             Set<String> skipChannels, Escalation escalation) {

        public static final RulePolicy CUSTOM = new RulePolicy(true, null, true, true, Set.of(), Escalation.OFF);

        public RulePolicy {
            skipChannels = Set.copyOf(skipChannels);
            escalation = escalation == null ? Escalation.OFF : escalation;
        }

        public boolean covers(String channel) {
            return enabled && !skipChannels.contains(channel);
        }

        public boolean reported() {
            return notifyStaff || audit || escalation.enabled();
        }

        public ChatFilterMatch apply(ChatFilterMatch match) {
            return action == null ? match : match.as(action);
        }
    }

    public record Words(RulePolicy policy, List<String> list, List<String> patterns, List<String> allowed) {

        public Words {
            list = List.copyOf(list);
            patterns = List.copyOf(patterns);
            allowed = List.copyOf(allowed);
        }
    }

    public record Caps(RulePolicy policy, int percent, int minLetters) {
    }

    public record Links(RulePolicy policy, boolean blockIps, List<String> allowedDomains,
                        List<String> topLevelDomains) {

        public Links {
            allowedDomains = List.copyOf(allowedDomains);
            topLevelDomains = List.copyOf(topLevelDomains);
        }
    }

    public record Repeat(RulePolicy policy, int similarity, long windowMillis, int history, int minLength) {
    }

    public record Flood(RulePolicy policy, int messages, long windowMillis) {
    }

    public record ProxyCommands(boolean enabled, Map<String, List<String>> channels) {

        public static final ProxyCommands NONE = new ProxyCommands(false, Map.of());

        public ProxyCommands {
            Map<String, List<String>> copy = new LinkedHashMap<>();
            channels.forEach((channel, patterns) -> copy.put(channel, List.copyOf(patterns)));
            channels = Collections.unmodifiableMap(copy);
        }
    }

    public ChatFilterSettings {
        custom = Map.copyOf(custom);
        problems = List.copyOf(problems);
        commands = commands == null ? ProxyCommands.NONE : commands;
    }

    public RulePolicy policy(String rule) {
        return switch (rule) {
            case WORDS -> words.policy();
            case CAPS -> caps.policy();
            case LINKS -> links.policy();
            case REPEAT -> repeat.policy();
            case FLOOD -> flood.policy();
            default -> custom.getOrDefault(rule, RulePolicy.CUSTOM);
        };
    }

    static final List<String> DEFAULT_WORDS = List.of(
            "*hurensohn*", "hure", "huso", "*wichser", "*fotze", "*missgeburt", "spast", "spasti", "spasten",
            "*schwuchtel*", "kanake*", "neger*", "*arschloch", "fick dich", "nigger*", "nigga*", "faggot*", "fag",
            "retard", "retarded", "retards", "cunt*", "kys", "kill yourself", "kill urself", "motherfucker*",
            "whore*", "slut*", "tranny");

    static final List<String> DEFAULT_TLDS = List.of(
            "com", "net", "org", "de", "eu", "at", "ch", "gg", "io", "me", "tv", "co", "uk", "us", "info", "biz", "xyz",
            "online", "site", "fun", "club", "top", "pro", "app", "dev", "link", "live", "store", "shop", "world",
            "network", "games", "host", "space", "tech", "website", "cloud", "ru", "su", "fr", "nl", "pl", "es", "cz",
            "dk", "se", "fi", "be", "to", "ly", "sh", "cc", "ws", "tk", "ml", "ga", "cf", "gq", "ua", "br", "ca",
            "au", "tr", "ro", "hu", "sk", "pt");

    static final Map<String, List<String>> DEFAULT_COMMANDS = defaultCommands();

    private static final RulePolicy WORDS_POLICY = new RulePolicy(true, ChatFilterAction.BLOCK, true, true, Set.of(),
            new Escalation(true, 3, Duration.ofMinutes(10).toMillis(), "chat", true, "chat"));
    private static final RulePolicy CAPS_POLICY = new RulePolicy(true, ChatFilterAction.REPLACE, false, false,
            Set.of(), new Escalation(false, 10, Duration.ofMinutes(10).toMillis(), "spam", false, "spam"));
    private static final RulePolicy LINKS_POLICY = new RulePolicy(true, ChatFilterAction.BLOCK, true, true, Set.of(),
            new Escalation(true, 3, Duration.ofMinutes(30).toMillis(), "spam", true, "spam"));
    private static final RulePolicy REPEAT_POLICY = new RulePolicy(true, ChatFilterAction.BLOCK, false, false,
            Set.of("private"), new Escalation(true, 5, Duration.ofMinutes(5).toMillis(), "spam", false, "spam"));
    private static final RulePolicy FLOOD_POLICY = new RulePolicy(true, ChatFilterAction.BLOCK, false, false,
            Set.of(), new Escalation(true, 6, Duration.ofMinutes(2).toMillis(), "spam", false, "spam"));

    private static final List<String> HEADER = buildHeader();
    private static final Map<String, List<String>> COMMENTS = buildComments();

    public static ChatFilterSettings defaults(boolean proxy) {
        return read(filled(proxy), proxy, new ArrayList<>());
    }

    public static ChatFilterSettings load(Path file, boolean proxy) {
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
                List<String> problems = new ArrayList<>();
                problems.add(FILE_NAME + " could not be read, using the defaults: " + e.getMessage());
                return read(filled(proxy), proxy, problems);
            }
            if (fillDefaults(root, proxy)) {
                loader.save(root);
            }
            YamlComments.apply(file, HEADER, COMMENTS);
            return read(root, proxy, new ArrayList<>());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static ChatFilterSettings read(ConfigurationNode root, boolean proxy, List<String> problems) {
        try {
            String replacement = root.node("replacement").getString("***");
            ConfigurationNode wordsNode = root.node(WORDS);
            Words words = new Words(policy(wordsNode, WORDS, WORDS_POLICY, true, problems),
                    strings(wordsNode.node("list"), DEFAULT_WORDS), strings(wordsNode.node("patterns"), List.of()),
                    strings(wordsNode.node("allowed"), List.of()));
            ConfigurationNode capsNode = root.node(CAPS);
            Caps caps = new Caps(policy(capsNode, CAPS, CAPS_POLICY, true, problems),
                    Math.clamp(capsNode.node("percent").getInt(70), 1, 100),
                    Math.max(1, capsNode.node("min-letters").getInt(8)));
            ConfigurationNode linksNode = root.node(LINKS);
            Links links = new Links(policy(linksNode, LINKS, LINKS_POLICY, true, problems),
                    linksNode.node("block-ips").getBoolean(true), strings(linksNode.node("allowed-domains"), List.of()),
                    strings(linksNode.node("top-level-domains"), DEFAULT_TLDS));
            ConfigurationNode repeatNode = root.node(REPEAT);
            Repeat repeat = new Repeat(policy(repeatNode, REPEAT, REPEAT_POLICY, false, problems),
                    Math.clamp(repeatNode.node("similarity").getInt(85), 1, 100),
                    duration(repeatNode.node("within"), "30s", REPEAT + ".within", problems),
                    Math.clamp(repeatNode.node("history").getInt(3), 1, 20),
                    Math.max(1, repeatNode.node("min-length").getInt(4)));
            ConfigurationNode floodNode = root.node(FLOOD);
            Flood flood = new Flood(policy(floodNode, FLOOD, FLOOD_POLICY, false, problems),
                    Math.clamp(floodNode.node("messages").getInt(4), 1, 50),
                    duration(floodNode.node("within"), "5s", FLOOD + ".within", problems));
            Map<String, RulePolicy> custom = new LinkedHashMap<>();
            for (Map.Entry<Object, ? extends ConfigurationNode> entry : root.node("custom").childrenMap().entrySet()) {
                String id = entry.getKey().toString().toLowerCase(Locale.ROOT);
                if (!ChatFilterRule.ID.matcher(id).matches() || BUILT_IN.contains(id)) {
                    problems.add("custom." + id + " is no valid rule id and is skipped");
                    continue;
                }
                custom.put(id, customPolicy(entry.getValue(), "custom." + id, problems));
            }
            ProxyCommands commands = proxy ? commands(root.node("proxy-commands"), problems) : ProxyCommands.NONE;
            return new ChatFilterSettings(replacement, words, caps, links, repeat, flood, custom, commands, problems);
        } catch (SerializationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RulePolicy policy(ConfigurationNode node, String path, RulePolicy defaults, boolean replaceable,
                                     List<String> problems) throws SerializationException {
        ChatFilterAction action = action(node.node("action"), path, defaults.action(), problems);
        if (action == ChatFilterAction.REPLACE && !replaceable) {
            problems.add(path + ".action: replace does not work for this rule, using block");
            action = ChatFilterAction.BLOCK;
        }
        return new RulePolicy(node.node("enabled").getBoolean(defaults.enabled()), action,
                node.node("notify-staff").getBoolean(defaults.notifyStaff()),
                node.node("audit").getBoolean(defaults.audit()),
                channels(node.node("skip-channels"), defaults.skipChannels(), path, problems),
                escalation(node.node("escalation"), defaults.escalation(), path + ".escalation", problems));
    }

    private static RulePolicy customPolicy(ConfigurationNode node, String path, List<String> problems)
            throws SerializationException {
        ChatFilterAction action = node.node("action").virtual() || node.node("action").getString("").isBlank()
                ? null : action(node.node("action"), path, null, problems);
        return new RulePolicy(node.node("enabled").getBoolean(true), action,
                node.node("notify-staff").getBoolean(true), node.node("audit").getBoolean(true),
                channels(node.node("skip-channels"), Set.of(), path, problems),
                escalation(node.node("escalation"), Escalation.OFF, path + ".escalation", problems));
    }

    private static ChatFilterAction action(ConfigurationNode node, String path, ChatFilterAction fallback,
                                           List<String> problems) {
        String text = node.getString(fallback == null ? "" : fallback.id());
        return ChatFilterAction.byId(text).orElseGet(() -> {
            problems.add(path + ".action: '" + text + "' must be block, replace, warn or log");
            return fallback == null ? ChatFilterAction.BLOCK : fallback;
        });
    }

    private static Escalation escalation(ConfigurationNode node, Escalation defaults, String path,
                                         List<String> problems) {
        return new Escalation(node.node("enabled").getBoolean(defaults.enabled()),
                node.node("violations").getInt(defaults.violations()),
                duration(node.node("within"), text(defaults.windowMillis()),
                        path + ".within", problems),
                checkedReference(node.node("template").getString(defaults.template()), path + ".template", problems),
                node.node("report").getBoolean(defaults.report()),
                checkedReference(node.node("report-reason").getString(defaults.reportReason()),
                        path + ".report-reason", problems));
    }

    private static String checkedReference(String value, String path, List<String> problems) {
        String clean = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!REFERENCE.matcher(clean).matches()) {
            problems.add(path + ": '" + value + "' is no valid id");
            return "";
        }
        return clean;
    }

    private static Set<String> channels(ConfigurationNode node, Set<String> fallback, String path,
                                        List<String> problems) throws SerializationException {
        if (node.virtual()) {
            return fallback;
        }
        Set<String> channels = new LinkedHashSet<>();
        for (String value : node.getList(String.class, List.of())) {
            String channel = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            if (CHANNEL.matcher(channel).matches()) {
                channels.add(channel);
            } else {
                problems.add(path + ".skip-channels: '" + value + "' is no channel name");
            }
        }
        return channels;
    }

    private static long duration(ConfigurationNode node, String fallback, String path, List<String> problems) {
        String text = node.getString(fallback);
        return Durations.parse(text).map(Duration::toMillis).orElseGet(() -> {
            problems.add(path + ": '" + text + "' is no duration such as 30s, 10m or 2h, using " + fallback);
            return Durations.parse(fallback).orElseThrow().toMillis();
        });
    }

    static String text(long millis) {
        long seconds = Math.max(1L, millis / 1_000L);
        if (seconds % 86_400L == 0L) {
            return seconds / 86_400L + "d";
        }
        if (seconds % 3_600L == 0L) {
            return seconds / 3_600L + "h";
        }
        if (seconds % 60L == 0L) {
            return seconds / 60L + "m";
        }
        return seconds + "s";
    }

    private static ProxyCommands commands(ConfigurationNode node, List<String> problems)
            throws SerializationException {
        Map<String, List<String>> channels = new LinkedHashMap<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : node.node("channels").childrenMap().entrySet()) {
            String channel = entry.getKey().toString().toLowerCase(Locale.ROOT);
            if (!CHANNEL.matcher(channel).matches()) {
                problems.add("proxy-commands.channels." + channel + " is no channel name");
                continue;
            }
            channels.put(channel, strings(entry.getValue(), List.of()));
        }
        return new ProxyCommands(node.node("enabled").getBoolean(true), channels);
    }

    private static List<String> strings(ConfigurationNode node, List<String> fallback) throws SerializationException {
        if (node.virtual()) {
            return fallback;
        }
        List<String> values = new ArrayList<>();
        for (String value : node.getList(String.class, List.of())) {
            if (value != null && !value.isBlank()) {
                values.add(value.trim());
            }
        }
        return values;
    }

    static boolean fillDefaults(ConfigurationNode root, boolean proxy) throws SerializationException {
        boolean changed = setIfAbsent(root.node("replacement"), "***");
        changed |= fillPolicy(root.node(WORDS), WORDS_POLICY);
        changed |= setListIfAbsent(root.node(WORDS, "list"), DEFAULT_WORDS);
        changed |= setListIfAbsent(root.node(WORDS, "patterns"), List.of());
        changed |= setListIfAbsent(root.node(WORDS, "allowed"), List.of());
        changed |= fillEscalation(root.node(WORDS), WORDS_POLICY);
        changed |= fillPolicy(root.node(CAPS), CAPS_POLICY);
        changed |= setIfAbsent(root.node(CAPS, "percent"), 70);
        changed |= setIfAbsent(root.node(CAPS, "min-letters"), 8);
        changed |= fillEscalation(root.node(CAPS), CAPS_POLICY);
        changed |= fillPolicy(root.node(LINKS), LINKS_POLICY);
        changed |= setIfAbsent(root.node(LINKS, "block-ips"), true);
        changed |= setListIfAbsent(root.node(LINKS, "allowed-domains"), List.of());
        changed |= setListIfAbsent(root.node(LINKS, "top-level-domains"), DEFAULT_TLDS);
        changed |= fillEscalation(root.node(LINKS), LINKS_POLICY);
        changed |= fillPolicy(root.node(REPEAT), REPEAT_POLICY);
        changed |= setIfAbsent(root.node(REPEAT, "similarity"), 85);
        changed |= setIfAbsent(root.node(REPEAT, "within"), "30s");
        changed |= setIfAbsent(root.node(REPEAT, "history"), 3);
        changed |= setIfAbsent(root.node(REPEAT, "min-length"), 4);
        changed |= fillEscalation(root.node(REPEAT), REPEAT_POLICY);
        changed |= fillPolicy(root.node(FLOOD), FLOOD_POLICY);
        changed |= setIfAbsent(root.node(FLOOD, "messages"), 4);
        changed |= setIfAbsent(root.node(FLOOD, "within"), "5s");
        changed |= fillEscalation(root.node(FLOOD), FLOOD_POLICY);
        if (root.node("custom").virtual()) {
            root.node("custom").set(Map.of());
            changed = true;
        }
        if (proxy) {
            changed |= setIfAbsent(root.node("proxy-commands", "enabled"), true);
            if (root.node("proxy-commands", "channels").virtual()) {
                for (Map.Entry<String, List<String>> entry : DEFAULT_COMMANDS.entrySet()) {
                    root.node("proxy-commands", "channels", entry.getKey()).setList(String.class, entry.getValue());
                }
                changed = true;
            }
        }
        return changed;
    }

    private static boolean fillPolicy(ConfigurationNode node, RulePolicy policy) throws SerializationException {
        boolean changed = setIfAbsent(node.node("enabled"), policy.enabled());
        changed |= setIfAbsent(node.node("action"), policy.action().id());
        changed |= setIfAbsent(node.node("notify-staff"), policy.notifyStaff());
        changed |= setIfAbsent(node.node("audit"), policy.audit());
        changed |= setListIfAbsent(node.node("skip-channels"), List.copyOf(policy.skipChannels()));
        return changed;
    }

    private static boolean fillEscalation(ConfigurationNode node, RulePolicy policy) throws SerializationException {
        Escalation escalation = policy.escalation();
        ConfigurationNode section = node.node("escalation");
        boolean changed = setIfAbsent(section.node("enabled"), escalation.enabled());
        changed |= setIfAbsent(section.node("violations"), escalation.violations());
        changed |= setIfAbsent(section.node("within"), text(escalation.windowMillis()));
        changed |= setIfAbsent(section.node("template"), escalation.template());
        changed |= setIfAbsent(section.node("report"), escalation.report());
        changed |= setIfAbsent(section.node("report-reason"), escalation.reportReason());
        return changed;
    }

    private static ConfigurationNode filled(boolean proxy) {
        CommentedConfigurationNode root = CommentedConfigurationNode.root();
        try {
            fillDefaults(root, proxy);
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

    private static Map<String, List<String>> defaultCommands() {
        Map<String, List<String>> commands = new LinkedHashMap<>();
        commands.put("private", List.of("msg <player> <message>", "tell <player> <message>", "w <player> <message>",
                "whisper <player> <message>", "m <player> <message>", "r <message>", "reply <message>"));
        commands.put("party", List.of("pc <message>", "partychat <message>", "party chat <message>",
                "p chat <message>"));
        return Collections.unmodifiableMap(commands);
    }

    private static List<String> buildHeader() {
        return List.of(
                "Chat filter of this server. It checks every chat message before it is sent.",
                "Players with prismarin.chatfilter.bypass skip the whole filter,",
                "prismarin.chatfilter.bypass.<rule> skips a single rule, for example prismarin.chatfilter.bypass.caps.",
                "",
                "Every rule below has the same switches:",
                "  enabled: false turns the rule off.",
                "  action: block (the message is not sent, the writer is told why), replace (the bad part is",
                "    replaced, caps are written in small letters), warn (the message is sent, the writer gets a",
                "    warning) or log (sent unchanged, only staff and the audit log see it).",
                "  notify-staff: show it to staff with prismarin.staff.chatfilter on the whole network.",
                "  audit: write it into the audit log (/audit on the proxy).",
                "  skip-channels: channels the rule ignores: public, private, party or a channel a plugin uses.",
                "  escalation: after <violations> hits within <within> the proxy applies the punishment template",
                "    <template> from punishments.yml (empty: none) and files a report with the messages as",
                "    evidence when report is true. report-reason is a reason id from admin.yml.",
                "    Hits are counted on the proxy for the whole network.",
                "",
                "Texts for players live in messages_<language>.yml under chat-filter.");
    }

    private static Map<String, List<String>> buildComments() {
        Map<String, List<String>> comments = new LinkedHashMap<>();
        comments.put("replacement", List.of("Text that replaces a blocked word or link when the action is replace."));
        comments.put(WORDS, List.of("Blocked words and patterns. Words are matched without case and accents, with",
                "repeated letters (fuuuck), common leetspeak (f4ck, @, $), dots or spaces between letters (f.u.c.k,",
                "f u c k) and look-alike letters from other alphabets."));
        comments.put(WORDS + ".list", List.of("A plain entry matches whole words only, so \"cunt\" leaves Scunthorpe alone.",
                "word* also matches longer words that start with it, *word words that end with it,",
                "*word* any word that contains it. Entries with spaces match these words in a row."));
        comments.put(WORDS + ".patterns", List.of("Regular expressions checked against the original message, without case."));
        comments.put(WORDS + ".allowed", List.of("Words that never count, for example a town that contains a blocked word."));
        comments.put(CAPS, List.of("Messages written mostly in capital letters."));
        comments.put(CAPS + ".percent", List.of("Share of capital letters from which a message counts as shouting."));
        comments.put(CAPS + ".min-letters", List.of("Shorter messages are never checked, so OK, GG or LOL stay allowed."));
        comments.put(LINKS, List.of("Advertising: web addresses, server addresses and IP addresses, also written as",
                "\"example dot com\" or \"example (.) com\"."));
        comments.put(LINKS + ".block-ips", List.of("Also catch IPv4 addresses such as 127.0.0.1:25565."));
        comments.put(LINKS + ".allowed-domains", List.of("Domains that may be posted, together with all their subdomains,",
                "for example your own: play.example.net and example.net."));
        comments.put(LINKS + ".top-level-domains", List.of("Endings that count as an address without http:// in front.",
                "Addresses with http:// or https:// always count."));
        comments.put(REPEAT, List.of("The same or nearly the same message again within a short time."));
        comments.put(REPEAT + ".similarity", List.of("How similar two messages must be, in percent. 100 only catches exact copies."));
        comments.put(REPEAT + ".within", List.of("How long a message is remembered."));
        comments.put(REPEAT + ".history", List.of("How many earlier messages of a player are compared."));
        comments.put(REPEAT + ".min-length", List.of("Shorter messages such as gg or hi may be repeated."));
        comments.put(FLOOD, List.of("Too many messages in a short time. Blocked attempts count as well."));
        comments.put(FLOOD + ".messages", List.of("Messages allowed within the time below."));
        comments.put(FLOOD + ".within", List.of("The time window, for example 5s or 10s."));
        comments.put("custom", List.of("Settings for rules that other plugins add through the API, by rule id:",
                "enabled, action (empty keeps the plugin's choice), notify-staff, audit, skip-channels and escalation."));
        comments.put("proxy-commands", List.of("Proxy only: filters the text of private and party messages.",
                "Each entry is a command with its arguments, the last argument is the message.",
                "Add your own channel, for example clan: [\"cc <message>\"]. Only commands that run on the proxy",
                "or are sent unsigned are filtered."));
        return comments;
    }
}
