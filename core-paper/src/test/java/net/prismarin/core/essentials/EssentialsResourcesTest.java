package net.prismarin.core.essentials;

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

class EssentialsResourcesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("%[a-z_]+%");

    private static final Set<String> USED = Set.of(
            "teleport.usage", "teleport.all", "teleport.nobody", "teleport.here", "teleport.self", "teleport.pulled",
            "teleport.pulled-hidden", "heal.self", "heal.other", "heal.dead", "feed.self", "feed.other", "fly.on",
            "fly.off", "fly.on-other", "fly.off-other", "gamemode.usage", "gamemode.changed",
            "gamemode.changed-other", "gamemode.mode.survival", "gamemode.mode.creative", "gamemode.mode.adventure",
            "gamemode.mode.spectator", "speed.usage", "speed.walk", "speed.fly", "speed.walk-other",
            "speed.fly-other", "clear.self", "clear.other", "invsee.usage", "invsee.self", "invsee.opened",
            "invsee.view-only", "enderchest.opened", "enderchest.view-only", "world.usage", "world.unknown",
            "world.day", "world.night", "world.sun", "world.rain", "world.thunder", "ping.usage", "ping.self",
            "ping.other", "ping.good", "ping.fair", "ping.poor", "hat.empty", "hat.done", "skull.usage",
            "skull.invalid", "skull.loading", "skull.unknown", "skull.given");

    @Test
    void bothLanguagesShareTheEssentialsTexts() throws IOException {
        Map<String, String> german = texts(load("messages_de.yml").node("essentials"));
        Map<String, String> english = texts(load("messages_en.yml").node("essentials"));
        assertEquals(USED, german.keySet());
        assertEquals(german.keySet(), english.keySet());
        german.forEach((key, text) -> {
            assertEquals(placeholders(text), placeholders(english.get(key)), key);
            assertFalse(text.contains("—"), key);
            assertFalse(text.contains("–"), key);
        });
    }

    @Test
    void pluginYmlDeclaresEveryCommandAndPermission() throws IOException {
        ConfigurationNode plugin = load("plugin.yml");
        for (EssentialsFeature feature : EssentialsFeature.values()) {
            for (String command : feature.commands()) {
                ConfigurationNode node = plugin.node("commands", command);
                assertFalse(node.virtual(), command);
                assertEquals(feature.permission(), node.node("permission").getString(), command);
            }
            assertFalse(plugin.node("permissions", feature.permission()).virtual(), feature.permission());
        }
        assertEquals("true", plugin.node("permissions", EssentialsFeature.PING.permission(), "default").getString());
        assertEquals("op", plugin.node("permissions", EssentialsFeature.HEAL.permission("others"), "default")
                .getString());
        assertTrue(plugin.node("commands", "clear").virtual());
    }

    private static Set<String> placeholders(String text) {
        return Set.copyOf(PLACEHOLDER.matcher(text).results().map(MatchResult::group).toList());
    }

    private static ConfigurationNode load(String resource) throws IOException {
        try (InputStream stream = EssentialsResourcesTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return YamlConfigurationLoader.builder().source(() -> reader).build().load();
        }
    }

    private static Map<String, String> texts(ConfigurationNode root) {
        Map<String, String> texts = new TreeMap<>();
        collect(root, "", texts);
        return texts;
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
