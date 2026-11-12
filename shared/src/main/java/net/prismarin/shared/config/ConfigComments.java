package net.prismarin.shared.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ConfigComments {

    public static final List<String> HEADER = header();

    public static final Map<String, List<String>> CORE = Map.ofEntries(
            Map.entry("server-id", List.of("Unique name of this server in the network. Must match the name in servers.yml",
                    "and in the proxy, for example lobby-1 or bedwars-2.")),
            Map.entry("storage", List.of("Where profiles, balances, punishments and reports are stored.")),
            Map.entry("storage.mode", List.of("LOCAL (JSON files), SQLITE (one file), MYSQL or MARIADB, MONGO.",
                    "LOCAL and SQLITE only work for a single server. A network shares MYSQL or MONGO.",
                    "Move existing data with /prismarin migrate <from> <to> in the console.",
                    "The SQLite and MongoDB drivers are downloaded on first start when that mode is used.")),
            Map.entry("storage.mysql.properties", List.of("Options passed to the database driver, for example",
                    "sslMode: trust for self signed certificates or sslMode: disable without TLS.")),
            Map.entry("storage.mysql.pool-size", List.of("Connections this server keeps open.")),
            Map.entry("storage.sqlite.file", List.of("Path of the SQLite file, relative to the plugin folder.")),
            Map.entry("redis", List.of("Redis connects all servers live: network overview metrics, staff chat,",
                    "/netexec and instant updates. Without it every server only knows itself.")),
            Map.entry("locale.default", List.of("Language for players whose client language has no messages_<tag>.yml.",
                    "Add a file such as messages_fr.yml to the plugin folder to support another language.")),
            Map.entry("metrics", List.of("Anonymous usage statistics through bStats (bstats.org): server software, versions,",
                    "player count and storage mode, never names, IPs or chat. plugins/bStats/config.yml turns it off",
                    "for every plugin at once.")),
            Map.entry("update-check", List.of("Asks GitHub for a newer PRISMARIN release on start and then regularly.")),
            Map.entry("update-check.interval-hours", List.of("Hours between two checks.")),
            Map.entry("update-check.notify-staff", List.of(
                    "Staff with prismarin.admin.update get a clickable note on join while an update is available.")),
            Map.entry("branding", List.of("Your network's name and colours. Used by %brand%, %brand_prefix%,",
                    "<brand_primary> and <brand_accent> in every text.")),
            Map.entry("branding.primary-color", List.of("Hex colour #RRGGBB.")),
            Map.entry("branding.accent-color", List.of("Hex colour #RRGGBB, the second colour of the brand gradient.")),
            Map.entry("branding.prefix", List.of("Prefix in front of chat messages. Example: \"&8[&bMyNetwork&8] &7\"")),
            Map.entry("branding.success-prefix", List.of("Prefix of messages that confirm a command worked (%success_prefix%).",
                    "The default puts the text in light green after the normal prefix.")),
            Map.entry("branding.error-prefix", List.of("Prefix of messages that report a problem (%error_prefix%).",
                    "The default puts the text in light red after the normal prefix.")),
            Map.entry("economy.currencies", List.of("Currency ids of your network. Players get a balance in each one.")),
            Map.entry("economy.primary", List.of("The currency Vault and /coins use.")),
            Map.entry("compatibility.auto-detect", List.of("Switches PRISMARIN's own tablist, sidebar or chat format off",
                    "when TAB, a scoreboard plugin or a chat plugin is installed.")),
            Map.entry("systems", List.of("Whole systems. false removes the commands, displays and data of a system,",
                    "for example economy: false when you keep your own coin plugin.")),
            Map.entry("modules", List.of("Single features. false turns one off without touching the rest.")),
            Map.entry("modules.joinme", List.of("JoinMe: /joinme on game servers sends a clickable invite through the",
                    "proxy, see joinme.yml. Needs the module on the proxy and on the game servers.")));

    private ConfigComments() {
        throw new AssertionError();
    }

    public static void apply(Path file, Map<String, List<String>> extra) {
        Map<String, List<String>> comments = new HashMap<>(CORE);
        comments.putAll(extra);
        YamlComments.apply(file, HEADER, comments);
    }

    private static List<String> header() {
        List<String> lines = new ArrayList<>();
        lines.add("PRISMARIN core configuration.");
        lines.add("Missing keys are added on every start, your values stay as they are.");
        lines.add("");
        lines.addAll(YamlComments.TEXT_HELP);
        return List.copyOf(lines);
    }
}
