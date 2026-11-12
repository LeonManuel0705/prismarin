package net.prismarin.core.chatfilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import net.prismarin.shared.chatfilter.ChatFilterSettings;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

class ChatFilterMessagesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("%[a-z_]+%");

    @Test
    void bothLanguagesShareTheChatFilterTexts() throws IOException {
        Map<String, String> german = texts("messages_de.yml");
        Map<String, String> english = texts("messages_en.yml");
        assertEquals(german.keySet(), english.keySet());
        german.forEach((key, text) -> {
            assertEquals(placeholders(text), placeholders(english.get(key)), key);
            assertFalse(text.contains("\u2014") || text.contains("\u2013"), key);
        });
    }

    @Test
    void everyBuiltInRuleExplainsItself() throws IOException {
        Map<String, String> english = texts("messages_en.yml");
        for (String rule : ChatFilterSettings.BUILT_IN) {
            assertTrue(english.containsKey("reason." + rule), rule);
        }
        for (String key : Set.of("blocked", "changed", "warned", "reason.custom")) {
            assertTrue(english.containsKey(key), key);
        }
        assertTrue(english.get("blocked").contains("%reason%"));
    }

    private static Map<String, String> texts(String resource) throws IOException {
        try (InputStream stream = ChatFilterMessagesTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            ConfigurationNode root = YamlConfigurationLoader.builder().source(() -> reader).build().load();
            Map<String, String> texts = new TreeMap<>();
            collect(root.node("chat-filter"), "", texts);
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

    private static Set<String> placeholders(String text) {
        return Set.copyOf(PLACEHOLDER.matcher(text).results().map(MatchResult::group).toList());
    }
}
