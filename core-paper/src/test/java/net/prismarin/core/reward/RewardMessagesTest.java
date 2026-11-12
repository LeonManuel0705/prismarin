package net.prismarin.core.reward;

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
import net.prismarin.api.stats.StatPeriod;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

class RewardMessagesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("%[a-z_]+%");
    private static final List<String> SECTIONS = List.of("stats", "daily", "reward", "vote", "lobby.leaderboard");

    @Test
    void bothLanguagesShareEveryText() throws IOException {
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
    void everyPeriodAndTheFirstRanksHaveAText() throws IOException {
        Map<String, String> english = texts("messages_en.yml");

        for (StatPeriod period : StatPeriod.values()) {
            assertTrue(english.containsKey("stats.period." + period.id()), period.id());
        }
        for (String rank : List.of("1", "2", "3", "other")) {
            assertTrue(english.containsKey("lobby.leaderboard.rank." + rank), rank);
        }
        assertTrue(english.get("daily.already").contains("%time%"));
        assertTrue(english.get("lobby.leaderboard.line").contains("%player%"));
        assertTrue(english.get("stats.usage").contains("\\<player>"));
    }

    @Test
    void confirmationsAndProblemsUseTheirPrefixes() throws IOException {
        Map<String, String> english = texts("messages_en.yml");

        for (String key : List.of("daily.claimed", "vote.rewarded", "lobby.leaderboard.created")) {
            assertTrue(english.get(key).startsWith("%success_prefix%"), key);
        }
        for (String key : List.of("daily.failed", "stats.failed", "lobby.leaderboard.unknown")) {
            assertTrue(english.get(key).startsWith("%error_prefix%"), key);
        }
    }

    private static Map<String, String> texts(String resource) throws IOException {
        try (InputStream stream = RewardMessagesTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            ConfigurationNode root = YamlConfigurationLoader.builder().source(() -> reader).build().load();
            Map<String, String> texts = new TreeMap<>();
            for (String section : SECTIONS) {
                collect(root.node((Object[]) section.split("\\.")), section, texts);
            }
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
