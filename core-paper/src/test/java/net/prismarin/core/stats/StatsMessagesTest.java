package net.prismarin.core.stats;

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

class StatsMessagesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("%[a-z_]+%");

    @Test
    void bothLanguagesShareTheStatsTexts() throws IOException {
        Map<String, String> german = texts("messages_de.yml");
        Map<String, String> english = texts("messages_en.yml");

        assertEquals(german.keySet(), english.keySet());
        german.forEach((key, text) -> {
            assertEquals(placeholders(text), placeholders(english.get(key)), key);
            assertFalse(text.contains("—") || text.contains("–"), key);
            assertFalse(english.get(key).contains("—") || english.get(key).contains("–"), key);
        });
    }

    @Test
    void everyPartOfTheOutputHasAText() throws IOException {
        Map<String, String> english = texts("messages_en.yml");
        for (String key : List.of("header", "header-game", "game", "line", "rank", "hover", "games-header",
                "game-entry", "game-first", "game-hover-line", "game-hover-more", "game-hover-click", "page",
                "page-previous", "page-next", "page-previous-none", "page-next-none", "page-hover", "back",
                "back-hover", "none", "none-game", "failed", "usage", "usage-self")) {
            assertTrue(english.containsKey("stats." + key), key);
        }
        assertTrue(english.get("stats.page").contains("%previous%") && english.get("stats.page").contains("%next%"));
        assertTrue(english.get("stats.game-entry").contains("%game%"));
    }

    @Test
    void prefixesAndUsageFollowTheHouseStyle() throws IOException {
        Map<String, String> english = texts("messages_en.yml");
        Map<String, String> german = texts("messages_de.yml");
        for (String key : List.of("stats.header", "stats.games-header", "stats.usage", "stats.none")) {
            assertTrue(english.get(key).startsWith("%brand_prefix%"), key);
            assertTrue(german.get(key).startsWith("%brand_prefix%"), key);
        }
        assertTrue(english.get("stats.failed").startsWith("%error_prefix%"));
        assertTrue(english.get("stats.usage").contains("[\\<player>] [\\<game>] [\\<page>]"));
        assertTrue(german.get("stats.usage").contains("[\\<Spieler>] [\\<Spiel>] [\\<Seite>]"));
    }

    private static Map<String, String> texts(String resource) throws IOException {
        try (InputStream stream = StatsMessagesTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            ConfigurationNode root = YamlConfigurationLoader.builder().source(() -> reader).build().load();
            Map<String, String> texts = new TreeMap<>();
            collect(root.node("stats"), "stats", texts);
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
