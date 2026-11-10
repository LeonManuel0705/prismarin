package net.prismarin.core.nick;

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

class NickMessagesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("%[a-z_]+%");

    private static final Set<String> USED_KEYS = Set.of(
            "usage", "applied", "stored", "removed", "auto-still-on", "removed-by-staff", "not-nicked", "invalid-name",
            "name-taken", "no-free-name", "failed", "disabled", "busy", "auto-on", "auto-off", "auto-not-here", "actionbar",
            "revealed", "renamed", "login-retry", "others.removed", "others.not-nicked", "realname.usage", "realname.result",
            "realname.result-remote", "realname.unknown", "realname.list-header", "realname.list-entry",
            "realname.list-empty");

    @Test
    void bothLanguagesShareTheSameNickKeys() throws IOException {
        Map<String, String> german = nickTexts("messages_de.yml");
        Map<String, String> english = nickTexts("messages_en.yml");
        assertEquals(german.keySet(), english.keySet());
        assertEquals(USED_KEYS, german.keySet());
    }

    @Test
    void germanTextsAvoidDashes() throws IOException {
        nickTexts("messages_de.yml").forEach((key, text) -> {
            assertFalse(text.contains("—"), key);
            assertFalse(text.contains("–"), key);
        });
    }

    @Test
    void placeholdersMatchBetweenLanguages() throws IOException {
        Map<String, String> german = nickTexts("messages_de.yml");
        Map<String, String> english = nickTexts("messages_en.yml");
        german.forEach((key, text) -> assertEquals(placeholders(text), placeholders(english.get(key)), key));
        assertTrue(placeholders(german.get("renamed")).contains("%old%"));
    }

    private static Set<String> placeholders(String text) {
        return Set.copyOf(PLACEHOLDER.matcher(text).results().map(MatchResult::group).toList());
    }

    private static Map<String, String> nickTexts(String resource) throws IOException {
        try (InputStream stream = NickMessagesTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            ConfigurationNode root = YamlConfigurationLoader.builder().source(() -> reader).build().load();
            Map<String, String> texts = new TreeMap<>();
            collect(root.node("nick"), "", texts);
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
