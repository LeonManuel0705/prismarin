package net.prismarin.velocity.admin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.prismarin.api.moderation.PunishmentType;
import net.prismarin.shared.config.YamlComments;
import net.prismarin.shared.moderation.Durations;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public record PunishmentSettings(String appealUrl, ZoneId zone, boolean showActor, boolean ipBans,
                                 boolean countLifted, PublicBroadcast broadcast, List<PunishmentTemplate> templates,
                                 List<String> problems) {

    public static final String FILE_NAME = "punishments.yml";

    public record PublicBroadcast(boolean enabled, Set<PunishmentKind> kinds) {

        public PublicBroadcast {
            kinds = kinds.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(kinds));
        }

        public boolean covers(PunishmentKind kind) {
            return enabled && kinds.contains(kind);
        }
    }

    record Seed(String id, String de, String en, String icon, PunishmentType type, List<String> steps,
                String permission, String forgetAfter) {
    }

    static final List<Seed> DEFAULT_TEMPLATES = List.of(
            new Seed("hacking", "Hacking oder Cheats", "Hacking or cheats", "DIAMOND_SWORD", PunishmentType.BAN,
                    List.of("30d", "90d", "permanent"), "", ""),
            new Seed("chat", "Beleidigung im Chat", "Insults in chat", "WRITABLE_BOOK", PunishmentType.MUTE,
                    List.of("1h", "6h", "1d", "7d"), "", "180d"),
            new Seed("spam", "Spam oder Werbung", "Spam or advertising", "PAPER", PunishmentType.MUTE,
                    List.of("warn", "30m", "2h", "1d", "7d"), "", "90d"),
            new Seed("name", "Unangemessener Name oder Skin", "Inappropriate name or skin", "NAME_TAG",
                    PunishmentType.BAN, List.of("kick", "1d", "7d", "permanent"), "", ""),
            new Seed("teaming", "Teaming", "Teaming", "LEAD", PunishmentType.BAN,
                    List.of("1d", "3d", "7d", "30d"), "", "365d"),
            new Seed("bugusing", "Bugusing oder Griefing", "Bug abuse or griefing", "TNT", PunishmentType.BAN,
                    List.of("3d", "14d", "30d", "permanent"), "", ""),
            new Seed("evasion", "Umgehen einer Sperre", "Ban evasion", "BARRIER", PunishmentType.BAN,
                    List.of("permanent"), "prismarin.punish.template.evasion", ""));

    private static final List<String> HEADER = List.of(
            "PRISMARIN punishments for the proxy: templates, escalation and screens.",
            "Changes take effect after a proxy restart. The texts live in messages_<language>.yml under punishment.");

    private static final Map<String, List<String>> COMMENTS = Map.ofEntries(
            Map.entry("appeal-url", List.of("Where punished players can appeal. Shown wherever a text uses %appeal%.")),
            Map.entry("time-zone", List.of("Time zone for %expires% and dates, for example Europe/Berlin.",
                    "Empty uses the time zone of the proxy machine.")),
            Map.entry("show-actor-to-player", List.of("true shows the staff name in %actor% on ban screens and notices.",
                    "false, or a nicked or vanished staff member, shows the team instead.")),
            Map.entry("ip-bans", List.of("/ipban bans the current address of an online player, so other accounts",
                    "on that connection cannot join either. Addresses are only stored as salted hashes.",
                    "Careful: schools, dorms and mobile networks share addresses.",
                    "prismarin.punish.ipban.bypass lets an account join anyway.")),
            Map.entry("escalation", List.of("How previous offences of a template are counted.")),
            Map.entry("escalation.count-lifted", List.of("false ignores punishments that staff lifted early with",
                    "/unban or /unmute, for example after an appeal.")),
            Map.entry("public-broadcast", List.of("Tells all players without prismarin.staff that someone was punished.",
                    "Nicked players appear with their nick, vanished players never.")),
            Map.entry("public-broadcast.kinds", List.of("ban, tempban, ipban, mute, tempmute, kick, warn")),
            Map.entry("templates", List.of("/punish <player> <template> and /ban <player> <template> pick the next step",
                    "from the previous offences of that template. Without a template /punish <player> opens a menu.",
                    "name: one name per language tag, the first one is used when a language is missing.",
                    "icon: item shown in the menu. type: ban, mute, kick or warn for steps without a type.",
                    "steps: one entry per offence, the last one repeats. A duration such as 30m, 12h, 7d, 1w or 1y,",
                    "permanent, or a type with a duration: \"mute 2h\", \"ban 7d\", \"ban permanent\", \"warn\", \"kick\".",
                    "permission: optional extra permission needed to use the template.",
                    "forget-after: offences older than this no longer count, empty keeps them forever.")));

    public Optional<PunishmentTemplate> template(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String key = id.toLowerCase(Locale.ROOT);
        return templates.stream().filter(template -> template.id().equals(key)).findFirst();
    }

    public static PunishmentSettings load(Path file) {
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
            CommentedConfigurationNode root = Files.exists(file) ? loader.load() : loader.createNode();
            boolean changed = setIfAbsent(root.node("appeal-url"), "");
            changed |= setIfAbsent(root.node("time-zone"), "");
            changed |= setIfAbsent(root.node("show-actor-to-player"), false);
            changed |= setIfAbsent(root.node("ip-bans", "enabled"), true);
            changed |= setIfAbsent(root.node("escalation", "count-lifted"), false);
            changed |= setIfAbsent(root.node("public-broadcast", "enabled"), false);
            if (root.node("public-broadcast", "kinds").virtual()) {
                root.node("public-broadcast", "kinds").setList(String.class, List.of("ban", "tempban", "ipban"));
                changed = true;
            }
            if (root.node("templates").virtual()) {
                for (Seed seed : DEFAULT_TEMPLATES) {
                    ConfigurationNode node = root.node("templates", seed.id());
                    node.node("name", "de").set(seed.de());
                    node.node("name", "en").set(seed.en());
                    node.node("icon").set(seed.icon());
                    node.node("type").set(seed.type().name().toLowerCase(Locale.ROOT));
                    node.node("steps").setList(String.class, seed.steps());
                    node.node("permission").set(seed.permission());
                    node.node("forget-after").set(seed.forgetAfter());
                }
                changed = true;
            }
            if (changed) {
                loader.save(root);
            }
            YamlComments.apply(file, HEADER, COMMENTS);
            return read(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static PunishmentSettings read(ConfigurationNode root) throws SerializationException {
        List<String> problems = new ArrayList<>();
        String zoneText = root.node("time-zone").getString("").trim();
        ZoneId zone = ZoneId.systemDefault();
        if (!zoneText.isEmpty()) {
            try {
                zone = ZoneId.of(zoneText);
            } catch (DateTimeException e) {
                problems.add("time-zone '" + zoneText + "' is unknown, using " + zone.getId());
            }
        }
        Set<PunishmentKind> kinds = EnumSet.noneOf(PunishmentKind.class);
        for (String value : root.node("public-broadcast", "kinds").getList(String.class, List.of())) {
            PunishmentKind.parse(value).ifPresentOrElse(kinds::add,
                    () -> problems.add("public-broadcast.kinds: '" + value + "' is no punishment kind"));
        }
        List<PunishmentTemplate> templates = new ArrayList<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : root.node("templates").childrenMap().entrySet()) {
            template(entry.getKey().toString(), entry.getValue(), problems).ifPresent(templates::add);
        }
        return new PunishmentSettings(
                root.node("appeal-url").getString("").trim(),
                zone,
                root.node("show-actor-to-player").getBoolean(false),
                root.node("ip-bans", "enabled").getBoolean(true),
                root.node("escalation", "count-lifted").getBoolean(false),
                new PublicBroadcast(root.node("public-broadcast", "enabled").getBoolean(false), kinds),
                List.copyOf(templates),
                List.copyOf(problems));
    }

    private static Optional<PunishmentTemplate> template(String id, ConfigurationNode node, List<String> problems)
            throws SerializationException {
        String typeText = node.node("type").getString("ban");
        Optional<PunishmentKind> kind = PunishmentKind.parse(typeText);
        if (kind.isEmpty() || kind.get() == PunishmentKind.IPBAN) {
            problems.add("templates." + id + ".type: '" + typeText + "' must be ban, mute, kick or warn");
            return Optional.empty();
        }
        PunishmentType fallback = kind.get().type();
        List<PunishmentTemplate.Step> steps = new ArrayList<>();
        for (String text : node.node("steps").getList(String.class, List.of())) {
            PunishmentTemplate.parseStep(text, fallback).ifPresentOrElse(steps::add,
                    () -> problems.add("templates." + id + ".steps: '" + text + "' is no valid step"));
        }
        if (steps.isEmpty()) {
            problems.add("templates." + id + " has no valid step and is skipped");
            return Optional.empty();
        }
        Map<String, String> names = new LinkedHashMap<>();
        ConfigurationNode name = node.node("name");
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
        String forgetText = node.node("forget-after").getString("").trim();
        Duration forgetAfter = null;
        if (!forgetText.isEmpty()) {
            forgetAfter = Durations.parse(forgetText).orElse(null);
            if (forgetAfter == null) {
                problems.add("templates." + id + ".forget-after: '" + forgetText + "' is no duration");
            }
        }
        return Optional.of(new PunishmentTemplate(id, names, node.node("icon").getString("PAPER"),
                node.node("permission").getString(""), steps, forgetAfter));
    }

    private static boolean setIfAbsent(ConfigurationNode node, Object value) throws SerializationException {
        if (!node.virtual()) {
            return false;
        }
        node.set(value);
        return true;
    }
}
