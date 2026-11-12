package net.prismarin.core.joinme;

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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import net.prismarin.api.joinme.JoinMeResult;
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
    void everyResultOfACreationOrAClickHasAText() throws IOException {
        Map<String, String> english = texts("messages_en.yml");
        for (JoinMeResult.Status status : JoinMeResult.Status.values()) {
            for (String key : List.of(JoinMeTexts.created(status), JoinMeTexts.joined(status),
                    JoinMeTexts.previewed(status))) {
                assertTrue(key.equals("general.no-permission") || english.containsKey(key), key);
            }
        }
        for (GameState state : GameState.values()) {
            assertTrue(english.containsKey("joinme.state." + state.name().toLowerCase(Locale.ROOT)), state.name());
        }
        for (String action : JoinMeSubCommands.ALL.visible(permission -> true)) {
            if (!JoinMeSubCommands.HELP.equals(action) && !JoinMeSubCommands.CANCEL.equals(action)) {
                assertTrue(english.containsKey("joinme.help-" + action), action);
            }
        }
    }

    @Test
    void prefixesAndUsageFollowTheHouseStyle() throws IOException {
        Map<String, String> english = texts("messages_en.yml");
        Map<String, String> german = texts("messages_de.yml");
        for (String key : List.of("joinme.created", "joinme.arrived", "joinme.cancel-own", "joinme.cancel-done",
                "joinme.preview-sent")) {
            assertTrue(english.get(key).startsWith("%success_prefix%"), key);
        }
        for (String key : List.of("joinme.cooldown", "joinme.failed", "joinme.join-expired", "joinme.vanished",
                "joinme.not-enough-money", "joinme.cancel-unknown")) {
            assertTrue(english.get(key).startsWith("%error_prefix%"), key);
        }
        for (String key : List.of("joinme.joining", "joinme.list-header", "joinme.list-empty")) {
            assertTrue(english.get(key).startsWith("%brand_prefix%"), key);
        }
        assertTrue(english.get("joinme.cancel-usage").contains("\\<id|player>"));
        assertTrue(german.get("joinme.cancel-usage").contains("\\<ID|Spieler>"));
        assertTrue(english.get("joinme.list-entry").contains("%creator%"));
    }

    private static Map<String, String> texts(String resource) throws IOException {
        try (InputStream stream = JoinMeMessagesTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            ConfigurationNode root = YamlConfigurationLoader.builder().source(() -> reader).build().load();
            Map<String, String> texts = new TreeMap<>();
            collect(root.node("joinme"), "joinme", texts);
            return texts;
        }
    }

    private static void collect(ConfigurationNode node, String prefix, Map<String, String> texts) {
        if (node.childrenMap().isEmpty()) {
            texts.put(prefix, node.getString(""));
            return;
        }
        node.childrenMap().forEach((key, child) -> collect(child, prefix + "." + key, texts));
    }

    private static Set<String> placeholders(String text) {
        return Set.copyOf(PLACEHOLDER.matcher(text).results().map(MatchResult::group).toList());
    }
}
