package net.prismarin.velocity.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.prismarin.shared.text.TextFormatter;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

class QueueMessagesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("%[a-z_-]+%");
    private static final List<String> USED = List.of("not-leader", "reconnected", "already", "connect-failed",
            "sending", "send-failed", "paused", "resumed", "left", "left-switch", "left-other", "left-unit",
            "left-cleared", "left-failed", "left-party", "left-party-offline", "joined", "joined-party",
            "joined-paused", "party-queued", "action-bar", "action-bar-paused", "boss-bar", "boss-bar-paused",
            "status-none", "status", "status-paused", "status-party", "status-member", "usage-join", "usage-pause",
            "usage-resume", "usage-clear", "usage-send", "unknown-target", "already-there", "no-server", "failed",
            "not-queued", "list-empty", "list-header", "list-entry", "list-entry-paused", "list-target",
            "list-target-paused", "list-player", "list-player-away", "list-more", "paused-staff", "already-paused",
            "resumed-staff", "not-paused", "cleared", "sent-next", "nothing-to-send", "help-header", "help.status");

    @Test
    void bothLanguagesShareTheSameKeysAndPlaceholders() throws IOException {
        Map<String, String> german = texts("messages_de.yml");
        Map<String, String> english = texts("messages_en.yml");
        assertEquals(german.keySet(), english.keySet());
        german.forEach((key, text) -> {
            assertEquals(placeholders(text), placeholders(english.get(key)), key);
            for (String value : List.of(text, english.get(key))) {
                assertFalse(value.contains("—"), key);
                assertFalse(value.contains("–"), key);
                assertFalse(value.contains("✔") || value.contains("✖") || value.contains("✓"), key);
            }
        });
    }

    @Test
    void everyKeyTheQueueSendsExists() throws IOException {
        Map<String, String> english = texts("messages_en.yml");
        for (String key : USED) {
            assertTrue(english.containsKey("queue." + key), key);
        }
        for (String action : QueueSettings.SUB_COMMANDS.actions()) {
            assertTrue(english.containsKey("queue.help." + action), action);
        }
    }

    @Test
    void prefixesFollowTheMeaningOfTheMessage() throws IOException {
        Map<String, String> english = texts("messages_en.yml");
        for (String key : List.of("left", "sending", "paused-staff", "resumed-staff", "cleared", "sent-next")) {
            assertTrue(english.get("queue." + key).startsWith("%success_prefix%"), key);
        }
        for (String key : List.of("not-leader", "send-failed", "connect-failed", "left-failed", "unknown-target",
                "no-server", "failed", "not-queued", "nothing-to-send")) {
            assertTrue(english.get("queue." + key).startsWith("%error_prefix%"), key);
        }
        for (String key : List.of("joined", "already", "paused", "resumed", "status", "reconnected")) {
            assertTrue(english.get("queue." + key).startsWith("%brand_prefix%"), key);
        }
    }

    @Test
    void helpAndUsageShowArgumentsInAngleBrackets() throws IOException {
        TextFormatter formatter = TextFormatter.plain();
        Pattern escaped = Pattern.compile("\\\\<([^>]+)>");
        for (String resource : List.of("messages_de.yml", "messages_en.yml")) {
            int checked = 0;
            for (Map.Entry<String, String> entry : texts(resource).entrySet()) {
                if (!entry.getKey().contains(".help.") && !entry.getKey().contains("usage")) {
                    continue;
                }
                String rendered = plain(formatter, entry.getValue());
                assertFalse(rendered.contains("\\"), entry.getKey());
                Matcher matcher = escaped.matcher(entry.getValue());
                while (matcher.find()) {
                    assertTrue(rendered.contains("<" + matcher.group(1) + ">"), entry.getKey() + ": " + rendered);
                    checked++;
                }
            }
            assertTrue(checked >= 10, resource);
        }
        Map<String, String> english = texts("messages_en.yml");
        assertEquals(" /queue leave [<target>] » leave your queue",
                plain(formatter, english.get("queue.help.leave")));
        assertEquals("Queue for BedWars · 3 of 12", plain(formatter, english.get("queue.action-bar")));
    }

    private static String plain(TextFormatter formatter, String text) {
        return PlainTextComponentSerializer.plainText().serialize(formatter.format(text,
                Placeholder.unparsed("command", "/queue"), Placeholder.unparsed("target", "BedWars"),
                Placeholder.unparsed("position", "3"), Placeholder.unparsed("size", "12")));
    }

    private static Set<String> placeholders(String text) {
        return Set.copyOf(PLACEHOLDER.matcher(text).results().map(MatchResult::group).toList());
    }

    private static Map<String, String> texts(String resource) throws IOException {
        try (InputStream stream = QueueMessagesTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            ConfigurationNode root = YamlConfigurationLoader.builder().source(() -> reader).build().load();
            Map<String, String> texts = new TreeMap<>();
            collect(root.node("queue"), "queue", texts);
            assertFalse(texts.isEmpty(), resource);
            return texts;
        }
    }

    private static void collect(ConfigurationNode node, String path, Map<String, String> texts) {
        if (node.childrenMap().isEmpty()) {
            String value = node.getString();
            if (value != null) {
                texts.put(path, value);
            }
            return;
        }
        node.childrenMap().forEach((key, child) -> collect(child, path + "." + key, texts));
    }
}
