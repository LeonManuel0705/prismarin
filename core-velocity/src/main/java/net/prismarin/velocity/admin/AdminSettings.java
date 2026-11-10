package net.prismarin.velocity.admin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.prismarin.shared.config.YamlComments;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public record AdminSettings(String discordWebhook, int reportCooldownSeconds, int alertIntervalSeconds,
                            double lowTps, boolean trustBackendOperators, List<ReportReason> reportReasons,
                            boolean customReportReasons, ChatEvidence chatEvidence, ReportBehaviour reportBehaviour,
                            List<MenuAction> menuActions) {

    public record MenuAction(String id, String icon, Map<String, String> names, String command, String permission,
                             boolean console, boolean close) {

        public MenuAction {
            id = id.toLowerCase(Locale.ROOT);
            names = Map.copyOf(names);
        }
    }

    public record ReportBehaviour(int mergeMinutes, int priorityReporters, boolean staffJoinReminder,
                                  boolean returnNotice, boolean closeOnPunishment, boolean thankReporters,
                                  int claimMinutes) {

        public long mergeWindowMillis() {
            return mergeMinutes * 60_000L;
        }

        public long claimMillis() {
            return claimMinutes * 60_000L;
        }
    }

    public record ChatEvidence(boolean enabled, int minutes, int maxLines, boolean onlyReportedPlayer,
                               List<String> reasons, boolean customReasons) {

        public ChatEvidence {
            reasons = reasons.stream().map(reason -> reason.toLowerCase(Locale.ROOT)).toList();
        }

        public boolean appliesTo(String reasonId) {
            if (!enabled) {
                return false;
            }
            if (reasonId == null) {
                return customReasons;
            }
            return reasons.isEmpty() || reasons.contains(reasonId.toLowerCase(Locale.ROOT));
        }

        public long windowMillis() {
            return minutes * 60_000L;
        }
    }

    public static final Map<String, String> DEFAULT_COLORS = Map.of(
            "hacking", "red",
            "chat", "gold",
            "spam", "yellow",
            "name", "aqua",
            "teaming", "light_purple",
            "bugusing", "orange",
            "other", "gray");

    public static final List<ReportReason> DEFAULT_REASONS = List.of(
            seed("hacking", "DIAMOND_SWORD", "Hacking oder Cheats", "Hacking or cheats"),
            seed("chat", "WRITABLE_BOOK", "Beleidigung im Chat", "Insults in chat"),
            seed("spam", "PAPER", "Spam oder Werbung", "Spam or advertising"),
            seed("name", "NAME_TAG", "Unangemessener Name oder Skin", "Inappropriate name or skin"),
            seed("teaming", "LEAD", "Teaming", "Teaming"),
            seed("bugusing", "TNT", "Bugusing oder Griefing", "Bug abuse or griefing"),
            seed("other", "BOOK", "Sonstiges", "Something else"));

    private static final List<String> HEADER = List.of(
            "PRISMARIN admin settings for the proxy.",
            "Changes take effect after a proxy restart.");

    private static final Map<String, List<String>> COMMENTS = Map.ofEntries(
            Map.entry("alerts", List.of("Outage and performance alerts for staff with prismarin.staff.alerts.")),
            Map.entry("alerts.discord-webhook", List.of("Optional Discord webhook URL that also receives alerts and reports.")),
            Map.entry("alerts.low-tps", List.of("Staff get a warning when a server reports fewer TPS than this.")),
            Map.entry("reports", List.of("/report <player> opens a menu with these reasons on servers running PRISMARIN",
                    "and shows them as clickable chat lines everywhere else.")),
            Map.entry("reports.cooldown-seconds", List.of("How long a player has to wait between two reports. Staff skip it.")),
            Map.entry("reports.allow-custom-reason", List.of("Allows /report <player> <any text> besides the listed reasons.")),
            Map.entry("reports.reasons", List.of("Reason id, item icon, colour and one name per language tag. Add a line like",
                    "fr: \"Triche\" to support another language. The first name is used when a language is missing.",
                    "Names accept MiniMessage tags such as <bold> and classic codes such as &l or &c.",
                    "color: a colour name such as red, gold, aqua, light_purple or orange, a hex value such as",
                    "#FF8C00 or a code such as &c. It colours the reason in the report menu, the chat and /reports.")),
            Map.entry("reports.merge-minutes", List.of("Reports against the same player within this time become one case",
                    "with several reporters instead of separate reports. 0 turns bundling off.")),
            Map.entry("reports.priority-reporters", List.of("From this many reporters a case is marked as urgent for staff.")),
            Map.entry("reports.claim-minutes", List.of("Opening a case in /reports marks it as in progress for that staff member.",
                    "After this many minutes without any action on it the case is free for others again.",
                    "0 keeps a case in progress until it is closed.")),
            Map.entry("reports.staff-join-reminder", List.of("Staff see the number of open reports when they join.")),
            Map.entry("reports.return-notice", List.of("Staff are told when a player with open reports comes back online.")),
            Map.entry("reports.close-on-punishment", List.of("A ban, mute, kick or warning closes all open reports against that player.")),
            Map.entry("reports.thank-reporters", List.of("Reporters get a thank you once their report was handled, on their next",
                    "join if they are offline.")),
            Map.entry("reports.menu-actions", List.of("Extra buttons in the case view of /reports and in the chat list.",
                    "Every key is one action id, for example:",
                    "  spectate:",
                    "    icon: ENDER_EYE",
                    "    name: {de: \"Zuschauen\", en: \"Spectate\"}",
                    "    command: \"spectate %player%\"",
                    "    permission: \"\"",
                    "    console: false",
                    "    close: true",
                    "command runs as the staff member, or as the proxy console with console: true. Placeholders:",
                    "%player% (real name), %uuid%, %id% (report), %server% and %staff%.",
                    "Proxy commands run directly, other commands go to the server of the staff member.",
                    "permission: optional permission needed to see and use the button. close: closes the menu.",
                    "Plugins add more actions through the PRISMARIN API.")),
            Map.entry("reports.chat-evidence", List.of("Attaches the recent chat of the reported player's server to a report,",
                    "so staff can read what happened. Kept in memory on the proxy only.")),
            Map.entry("reports.chat-evidence.minutes", List.of("How far back the attached chat goes.")),
            Map.entry("reports.chat-evidence.only-reported-player", List.of("true attaches only the reported player's own lines,",
                    "false the whole conversation on that server.")),
            Map.entry("reports.chat-evidence.reasons", List.of("Reason ids that attach chat. An empty list attaches it to every reason.")),
            Map.entry("reports.chat-evidence.custom-reasons", List.of("Also attach chat to reports with a free text reason.")),
            Map.entry("permissions.trust-backend-operators", List.of("Gives every player who is OP on a PRISMARIN backend all",
                    "prismarin.* permissions on the proxy. Keep it off when you use a permission plugin.")));

    public Optional<ReportReason> reason(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        return reportReasons.stream().filter(reason -> reason.id().equals(key)).findFirst();
    }

    public static AdminSettings load(Path file) {
        try {
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(file)
                    .nodeStyle(NodeStyle.BLOCK)
                    .indent(2)
                    .build();
            CommentedConfigurationNode root = Files.exists(file) ? loader.load() : loader.createNode();
            boolean changed = setIfAbsent(root.node("alerts", "discord-webhook"), "");
            changed |= setIfAbsent(root.node("alerts", "interval-seconds"), 15);
            changed |= setIfAbsent(root.node("alerts", "low-tps"), 15.0);
            changed |= setIfAbsent(root.node("reports", "cooldown-seconds"), 60);
            changed |= setIfAbsent(root.node("reports", "allow-custom-reason"), true);
            if (root.node("reports", "reasons").virtual()) {
                for (ReportReason reason : DEFAULT_REASONS) {
                    ConfigurationNode node = root.node("reports", "reasons", reason.id());
                    node.node("icon").set(reason.icon());
                    node.node("color").set(DEFAULT_COLORS.get(reason.id()));
                    node.node("name", "de").set(reason.names().get("de"));
                    node.node("name", "en").set(reason.names().get("en"));
                }
                changed = true;
            }
            for (Map.Entry<Object, ? extends ConfigurationNode> reason
                    : root.node("reports", "reasons").childrenMap().entrySet()) {
                String color = DEFAULT_COLORS.get(reason.getKey().toString().toLowerCase(Locale.ROOT));
                if (color != null) {
                    changed |= setIfAbsent(reason.getValue().node("color"), color);
                }
            }
            changed |= setIfAbsent(root.node("reports", "merge-minutes"), 30);
            changed |= setIfAbsent(root.node("reports", "priority-reporters"), 3);
            changed |= setIfAbsent(root.node("reports", "claim-minutes"), 10);
            changed |= setIfAbsent(root.node("reports", "staff-join-reminder"), true);
            changed |= setIfAbsent(root.node("reports", "return-notice"), true);
            changed |= setIfAbsent(root.node("reports", "close-on-punishment"), true);
            changed |= setIfAbsent(root.node("reports", "thank-reporters"), true);
            if (root.node("reports", "menu-actions").virtual()) {
                root.node("reports", "menu-actions").raw(new LinkedHashMap<>());
                changed = true;
            }
            ConfigurationNode evidence = root.node("reports", "chat-evidence");
            changed |= setIfAbsent(evidence.node("enabled"), true);
            changed |= setIfAbsent(evidence.node("minutes"), 15);
            changed |= setIfAbsent(evidence.node("max-lines"), 40);
            changed |= setIfAbsent(evidence.node("only-reported-player"), false);
            if (evidence.node("reasons").virtual()) {
                evidence.node("reasons").setList(String.class, List.of("chat", "spam"));
                changed = true;
            }
            changed |= setIfAbsent(evidence.node("custom-reasons"), true);
            changed |= setIfAbsent(root.node("permissions", "trust-backend-operators"), false);
            if (changed) {
                loader.save(root);
            }
            YamlComments.apply(file, HEADER, COMMENTS);
            return new AdminSettings(
                    root.node("alerts", "discord-webhook").getString(""),
                    root.node("reports", "cooldown-seconds").getInt(60),
                    Math.max(5, root.node("alerts", "interval-seconds").getInt(15)),
                    root.node("alerts", "low-tps").getDouble(15.0),
                    root.node("permissions", "trust-backend-operators").getBoolean(false),
                    reasons(root.node("reports", "reasons")),
                    root.node("reports", "allow-custom-reason").getBoolean(true),
                    new ChatEvidence(evidence.node("enabled").getBoolean(true),
                            Math.max(1, Math.min(120, evidence.node("minutes").getInt(15))),
                            Math.max(1, Math.min(200, evidence.node("max-lines").getInt(40))),
                            evidence.node("only-reported-player").getBoolean(false),
                            evidence.node("reasons").getList(String.class, List.of("chat", "spam")),
                            evidence.node("custom-reasons").getBoolean(true)),
                    new ReportBehaviour(Math.max(0, root.node("reports", "merge-minutes").getInt(30)),
                            Math.max(2, root.node("reports", "priority-reporters").getInt(3)),
                            root.node("reports", "staff-join-reminder").getBoolean(true),
                            root.node("reports", "return-notice").getBoolean(true),
                            root.node("reports", "close-on-punishment").getBoolean(true),
                            root.node("reports", "thank-reporters").getBoolean(true),
                            Math.max(0, root.node("reports", "claim-minutes").getInt(10))),
                    actions(root.node("reports", "menu-actions")));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<ReportReason> reasons(ConfigurationNode node) {
        List<ReportReason> reasons = new ArrayList<>();
        node.childrenMap().forEach((key, child) -> {
            Map<String, String> names = names(child.node("name"));
            reasons.add(new ReportReason(key.toString(), child.node("icon").getString("PAPER"), names,
                    TextColors.parse(child.node("color").getString("")).orElse(null)));
        });
        return List.copyOf(reasons);
    }

    private static List<MenuAction> actions(ConfigurationNode node) {
        List<MenuAction> actions = new ArrayList<>();
        node.childrenMap().forEach((key, child) -> {
            String command = child.node("command").getString("").trim();
            String id = key.toString().trim();
            if (command.isEmpty() || id.isEmpty() || id.contains(" ") || id.contains(":")) {
                return;
            }
            actions.add(new MenuAction(id, child.node("icon").getString("PAPER"), names(child.node("name")),
                    command.startsWith("/") ? command.substring(1) : command,
                    child.node("permission").getString("").trim(), child.node("console").getBoolean(false),
                    child.node("close").getBoolean(true)));
        });
        return List.copyOf(actions);
    }

    private static Map<String, String> names(ConfigurationNode name) {
        Map<String, String> names = new LinkedHashMap<>();
        if (name.isMap()) {
            name.childrenMap().forEach((language, text) -> {
                String value = text.getString();
                if (value != null && !value.isBlank()) {
                    names.put(language.toString().toLowerCase(Locale.ROOT), value);
                }
            });
        } else if (name.getString() != null && !name.getString().isBlank()) {
            names.put("en", name.getString());
        }
        return names;
    }

    private static ReportReason seed(String id, String icon, String de, String en) {
        return new ReportReason(id, icon, Map.of("de", de, "en", en),
                TextColors.parse(DEFAULT_COLORS.get(id)).orElse(null));
    }

    private static boolean setIfAbsent(ConfigurationNode node, Object value) throws SerializationException {
        if (!node.virtual()) {
            return false;
        }
        node.set(value);
        return true;
    }
}
