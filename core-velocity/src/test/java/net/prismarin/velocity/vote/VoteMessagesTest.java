package net.prismarin.velocity.vote;

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
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

class VoteMessagesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("%[a-z_]+%");

    @Test
    void bothLanguagesShareTheVoteTexts() throws IOException {
        Map<String, String> german = texts("messages_de.yml");
        Map<String, String> english = texts("messages_en.yml");

        assertEquals(german.keySet(), english.keySet());
        german.forEach((key, text) -> {
            assertEquals(placeholders(text), placeholders(english.get(key)), key);
            assertFalse(text.contains("\u2014") || text.contains("\u2013"), key);
        });
        assertTrue(english.get("site").contains("%site%"));
        assertTrue(english.get("test-usage").contains("\\<player>"));
        assertTrue(english.get("failed").startsWith("%error_prefix%"));
    }

    private static Map<String, String> texts(String resource) throws IOException {
        try (InputStream stream = VoteMessagesTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            ConfigurationNode root = YamlConfigurationLoader.builder().source(() -> reader).build().load();
            Map<String, String> texts = new TreeMap<>();
            root.node("vote").childrenMap().forEach((key, node) -> texts.put(key.toString(), node.getString("")));
            return texts;
        }
    }

    private static Set<String> placeholders(String text) {
        return Set.copyOf(PLACEHOLDER.matcher(text).results().map(MatchResult::group).toList());
    }
}
