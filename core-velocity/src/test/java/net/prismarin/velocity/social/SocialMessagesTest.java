package net.prismarin.velocity.social;

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
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

class SocialMessagesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("%[a-z_-]+%");
    private static final List<String> BLOCKS = List.of("friends", "party", "msg");

    @Test
    void bothLanguagesShareTheSameKeys() throws IOException {
        assertEquals(texts("messages_de.yml").keySet(), texts("messages_en.yml").keySet());
    }

    @Test
    void placeholdersMatchAndGermanAvoidsDashes() throws IOException {
        Map<String, String> german = texts("messages_de.yml");
        Map<String, String> english = texts("messages_en.yml");
        german.forEach((key, text) -> {
            assertEquals(placeholders(text), placeholders(english.get(key)), key);
            assertFalse(text.contains("—"), key);
            assertFalse(text.contains("–"), key);
        });
    }

    @Test
    void everySettingAndValueHasALabel() throws IOException {
        Map<String, String> english = texts("messages_en.yml");
        for (SocialSetting setting : SocialSetting.values()) {
            assertTrue(english.containsKey("friends.setting." + setting.id()), setting.id());
            for (String option : setting.options()) {
                assertTrue(english.containsKey("friends.value." + option), option);
            }
        }
        for (String unit : List.of("unknown", "now", "minutes", "hours", "days", "months")) {
            assertTrue(english.containsKey("friends.time." + unit), unit);
        }
        for (String action : List.of("add", "accept", "deny", "cancel", "remove", "list", "requests", "jump",
                "settings", "help")) {
            assertTrue(english.containsKey("friends.help." + action), action);
        }
        for (String action : List.of("invite", "accept", "deny", "leave", "kick", "promote", "disband", "list",
                "warp", "chat", "settings", "help")) {
            assertTrue(english.containsKey("party.help." + action), action);
        }
    }

    private static Set<String> placeholders(String text) {
        return Set.copyOf(PLACEHOLDER.matcher(text).results().map(MatchResult::group).toList());
    }

    private static Map<String, String> texts(String resource) throws IOException {
        try (InputStream stream = SocialMessagesTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            ConfigurationNode root = YamlConfigurationLoader.builder().source(() -> reader).build().load();
            Map<String, String> texts = new TreeMap<>();
            for (String block : BLOCKS) {
                collect(root.node(block), block, texts);
            }
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
