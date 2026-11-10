package net.prismarin.velocity.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

class PunishmentMessagesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("%[a-z_]+%");

    private static final Set<String> USED = Set.of(
            "permanent", "never", "not-lasting", "no-reason", "team", "date-format", "step", "appeal-line",
            "time.separator", "time.year", "time.years", "time.day", "time.days", "time.hour", "time.hours",
            "time.minute", "time.minutes", "time.second", "time.seconds",
            "usage.ban", "usage.tempban", "usage.ipban", "usage.mute", "usage.tempmute", "usage.kick", "usage.warn",
            "usage.punish", "usage.unban", "usage.unmute", "usage.history", "usage.check",
            "invalid-duration", "unknown-template", "template-locked", "no-templates", "self", "exempt",
            "already-longer", "no-address", "kick-offline", "nothing-active", "failed",
            "notice.warn", "notice.unmuted", "screen.kick",
            "staff.issued", "staff.issued-hover", "staff.step", "staff.replaced", "staff.nicked", "staff.revoked",
            "staff.revoked-note", "staff.address-blocked",
            "check.header", "check.online", "check.offline", "check.active", "check.no-ban", "check.no-mute",
            "check.counts", "check.offences", "check.footer",
            "history.header", "history.empty", "history.entry", "history.entry-active", "history.entry-expired",
            "history.entry-lifted", "history.entry-replaced", "history.previous", "history.next",
            "menu.title", "menu.target", "menu.active", "menu.record", "menu.template", "menu.next",
            "menu.previous", "menu.ladder", "menu.ladder-step", "menu.ladder-current", "menu.ladder-separator",
            "menu.click", "menu.chat-header", "menu.chat-entry");

    private static Set<String> expected() {
        Set<String> keys = new HashSet<>(USED);
        for (PunishmentKind kind : PunishmentKind.values()) {
            keys.add("type." + kind.key());
            keys.add("public." + kind.key());
            if (kind.type() == net.prismarin.api.moderation.PunishmentType.BAN) {
                keys.add("screen." + kind.key());
            }
            if (kind.type() == net.prismarin.api.moderation.PunishmentType.MUTE) {
                keys.add("notice." + kind.key());
            }
        }
        return keys;
    }

    @Test
    void bothLanguagesHaveEveryPunishmentText() throws IOException {
        Map<String, String> german = texts("messages_de.yml", "punishment");
        Map<String, String> english = texts("messages_en.yml", "punishment");
        assertEquals(german.keySet(), english.keySet());
        assertEquals(expected(), new HashSet<>(german.keySet()));
        assertTrue(texts("messages_de.yml", "moderation").containsKey("unknown-player"));
        assertTrue(texts("messages_en.yml", "moderation").containsKey("unknown-player"));
    }

    @Test
    void placeholdersMatchAndGermanAvoidsDashes() throws IOException {
        Map<String, String> german = texts("messages_de.yml", "punishment");
        Map<String, String> english = texts("messages_en.yml", "punishment");
        german.forEach((key, text) -> {
            assertEquals(placeholders(text), placeholders(english.get(key)), key);
            assertFalse(text.contains("—"), key);
            assertFalse(text.contains("–"), key);
        });
        assertTrue(german.get("screen.ban").contains("<newline>"));
        assertTrue(german.get("screen.tempban").contains("%remaining%"));
        assertTrue(german.get("screen.tempban").contains("%appeal_line%"));
    }

    private static Set<String> placeholders(String text) {
        return Set.copyOf(PLACEHOLDER.matcher(text).results().map(MatchResult::group).toList());
    }

    private static Map<String, String> texts(String resource, String section) throws IOException {
        try (InputStream stream = PunishmentMessagesTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            ConfigurationNode root = YamlConfigurationLoader.builder().source(() -> reader).build().load();
            Map<String, String> texts = new TreeMap<>();
            collect(root.node(section), "", texts);
            return texts;
        }
    }

    private static void collect(ConfigurationNode node, String prefix, Map<String, String> texts) {
        if (node.childrenMap().isEmpty()) {
            texts.put(prefix, node.getString(""));
            return;
        }
        node.childrenMap().forEach((key, child) ->
                collect(child, prefix.isEmpty() ? key.toString() : prefix + "." + key, texts));
    }
}
