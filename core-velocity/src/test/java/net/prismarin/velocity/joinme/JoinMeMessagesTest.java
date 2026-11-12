package net.prismarin.velocity.joinme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import net.prismarin.api.joinme.JoinMeExpiredEvent;
import net.prismarin.api.server.GameState;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

class JoinMeMessagesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("%[a-z_]+%");

    @Test
    void bothLanguagesShareTheJoinMeTexts() throws IOException {
        Map<String, String> german = texts("messages_de.yml");
        Map<String, String> english = texts("messages_en.yml");

        assertEquals(german.keySet(), english.keySet());
        german.forEach((key, text) -> {
            assertEquals(placeholders(text), placeholders(english.get(key)), key);
            assertFalse(text.contains("\u2014") || text.contains("\u2013"), key);
            assertFalse(english.get(key).contains("\u2014") || english.get(key).contains("\u2013"), key);
        });
    }

    @Test
    void everyEndingAndStateHasAText() throws IOException {
        Map<String, String> english = texts("messages_en.yml");
        for (JoinMeExpiredEvent.Reason reason : JoinMeExpiredEvent.Reason.values()) {
            if (reason != JoinMeExpiredEvent.Reason.REPLACED) {
                assertTrue(english.containsKey("ended." + reason.id()), reason.id());
            }
        }
        for (GameState state : GameState.values()) {
            assertTrue(english.containsKey("state." + state.name().toLowerCase(Locale.ROOT)), state.name());
        }
    }

    @Test
    void theBroadcastIsColourfulAndNamesTheGame() throws IOException {
        for (String file : new String[]{"messages_de.yml", "messages_en.yml"}) {
            String broadcast = texts(file).get("broadcast");
            assertTrue(broadcast.startsWith("<newline>%brand_prefix%"), file);
            for (String placeholder : new String[]{"%creator%", "%group%", "%server%", "%players%", "%max_players%",
                    "%state%", "%time%"}) {
                assertTrue(broadcast.contains(placeholder), file + " " + placeholder);
            }
            assertTrue(broadcast.contains("<gradient:"), file);
        }
        Map<String, String> english = texts("messages_en.yml");
        Map<String, String> german = texts("messages_de.yml");
        for (String key : JoinMeSettings.defaults().face().rows().values()) {
            String local = key.substring("joinme.".length());
            assertTrue(english.containsKey(local) && german.containsKey(local), key);
        }
        assertTrue(english.get("face.title").contains("%creator%"));
        assertTrue(english.get("face.button").contains("Join"));
        assertTrue(english.get("joined").startsWith("%success_prefix%"));
        assertTrue(english.get("preview-header").startsWith("%brand_prefix%"));
        assertTrue(german.get("preview-header").startsWith("%brand_prefix%"));
        assertTrue(english.containsKey("preview-hover") && german.containsKey("preview-hover"));
        assertTrue(english.get("connect-failed").startsWith("%error_prefix%"));
        assertTrue(english.get("ended.expired").startsWith("%brand_prefix%"));
    }

    private static Map<String, String> texts(String resource) throws IOException {
        try (InputStream stream = JoinMeMessagesTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            ConfigurationNode root = YamlConfigurationLoader.builder().source(() -> reader).build().load();
            Map<String, String> texts = new TreeMap<>();
            collect(root.node("joinme"), "", texts);
            return texts;
        }
    }

    private static void collect(ConfigurationNode node, String prefix, Map<String, String> texts) {
        if (node.childrenMap().isEmpty()) {
            texts.put(prefix, node.getString(""));
            return;
        }
        node.childrenMap().forEach((key, child) -> collect(child, prefix.isEmpty() ? key.toString()
                : prefix + "." + key, texts));
    }

    private static Set<String> placeholders(String text) {
        return Set.copyOf(PLACEHOLDER.matcher(text).results().map(MatchResult::group).toList());
    }
}
