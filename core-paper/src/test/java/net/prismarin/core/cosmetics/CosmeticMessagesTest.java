package net.prismarin.core.cosmetics;

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
import net.prismarin.api.cosmetics.CosmeticRarity;
import net.prismarin.api.cosmetics.CosmeticResult;
import net.prismarin.api.cosmetics.CosmeticSlot;
import net.prismarin.core.cosmetics.CosmeticMessages.Action;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

class CosmeticMessagesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("%[a-z_]+%");

    private static final List<String> USED = List.of(
            "cosmetics.hat-lore", "cosmetics.unlocked", "cosmetics.unlocked-owned", "cosmetics.equipped-elsewhere",
            "cosmetics.preview", "cosmetics.hide-on", "cosmetics.hide-off", "cosmetics.locked-rank",
            "cosmetics.locked-reward", "cosmetics.usage-give", "cosmetics.usage-take", "cosmetics.usage-play",
            "cosmetics.played", "cosmetics.play-none", "cosmetics.play-offline", "cosmetics.play-slot",
            "cosmetics.help-header",
            "cosmetics.help-open", "cosmetics.help-category", "cosmetics.menu.title", "cosmetics.menu.category-title",
            "cosmetics.menu.confirm-title", "cosmetics.menu.header-name", "cosmetics.menu.header-owned",
            "cosmetics.menu.header-progress", "cosmetics.menu.category-owned", "cosmetics.menu.category-equipped",
            "cosmetics.menu.category-none", "cosmetics.menu.category-elsewhere", "cosmetics.menu.category-games",
            "cosmetics.menu.category-click", "cosmetics.menu.rarity", "cosmetics.menu.description",
            "cosmetics.menu.state-equipped", "cosmetics.menu.state-owned", "cosmetics.menu.state-rank",
            "cosmetics.menu.state-price", "cosmetics.menu.state-missing", "cosmetics.menu.state-locked-rank",
            "cosmetics.menu.state-reward", "cosmetics.menu.click-equip", "cosmetics.menu.click-unequip",
            "cosmetics.menu.click-buy", "cosmetics.menu.click-preview", "cosmetics.menu.empty",
            "cosmetics.menu.wallet-name", "cosmetics.menu.unequip-all-name", "cosmetics.menu.unequip-all-lore",
            "cosmetics.menu.unequip-slot-name", "cosmetics.menu.unequip-slot-lore", "cosmetics.menu.hide-name-on",
            "cosmetics.menu.hide-name-off", "cosmetics.menu.hide-lore-on", "cosmetics.menu.hide-lore-off",
            "cosmetics.menu.hide-click", "cosmetics.menu.confirm-name", "cosmetics.menu.confirm-lore",
            "cosmetics.menu.confirm-balance", "cosmetics.menu.cancel-name", "cosmetics.menu.profile-name",
            "cosmetics.menu.profile-lore", "cosmetics.menu.profile-click", "cosmetics.price.currency",
            "cosmetics.wallet.currency");

    @Test
    void bothLanguagesShareTheCosmeticTexts() throws IOException {
        Map<String, String> german = texts("messages_de.yml");
        Map<String, String> english = texts("messages_en.yml");

        assertEquals(german.keySet(), english.keySet());
        german.forEach((key, text) -> {
            assertEquals(placeholders(text), placeholders(english.get(key)), key);
            for (String forbidden : List.of("\u2014", "\u2013", "\u2713", "\u2717", "\u2714", "\u2716")) {
                assertFalse(text.contains(forbidden), key);
                assertFalse(english.get(key).contains(forbidden), key);
            }
        });
    }

    @Test
    void everyTextTheCodeUsesExists() throws IOException {
        Map<String, String> english = texts("messages_en.yml");
        for (String key : USED) {
            assertTrue(english.containsKey(key), key);
        }
        for (Action action : Action.values()) {
            for (CosmeticResult.Status status : CosmeticResult.Status.values()) {
                String key = CosmeticMessages.key(action, status);
                assertTrue(english.containsKey(key), action + " " + status + " " + key);
            }
        }
        for (String action : CosmeticSubCommands.ALL.visible(permission -> true)) {
            if (!CosmeticSubCommands.HELP.equals(action)) {
                assertTrue(english.containsKey("cosmetics.help-" + action), action);
            }
        }
        for (CosmeticSlot slot : CosmeticSlot.BUILT_IN) {
            assertTrue(english.containsKey("cosmetics.category." + slot.id()), slot.id());
        }
        for (CosmeticRarity rarity : CosmeticSettings.defaults().rarities()) {
            assertTrue(english.containsKey("cosmetics.rarity." + rarity.id()), rarity.id());
        }
        for (String currency : List.of("coins", "gems")) {
            assertTrue(english.containsKey("cosmetics.price." + currency), currency);
            assertTrue(english.containsKey("cosmetics.wallet." + currency), currency);
        }
    }

    @Test
    void theLobbyItemAndTheRewardLineExistInBothLanguages() throws IOException {
        for (String file : List.of("messages_en.yml", "messages_de.yml")) {
            Map<String, String> lobby = texts(file, "lobby");
            Map<String, String> reward = texts(file, "reward");
            assertTrue(lobby.get("lobby.item.cosmetics.name").contains("›"), file);
            assertTrue(lobby.containsKey("lobby.item.cosmetics.lore"), file);
            assertTrue(reward.get("reward.cosmetic").contains("%cosmetic%"), file);
        }
    }

    @Test
    void prefixesAndUsageFollowTheHouseStyle() throws IOException {
        Map<String, String> english = texts("messages_en.yml");
        Map<String, String> german = texts("messages_de.yml");
        for (String key : List.of("cosmetics.purchased", "cosmetics.equipped", "cosmetics.unequipped",
                "cosmetics.unequipped-all", "cosmetics.hide-on", "cosmetics.hide-off", "cosmetics.unlocked",
                "cosmetics.given", "cosmetics.taken", "cosmetics.played")) {
            assertTrue(english.get(key).startsWith("%success_prefix%"), key);
            assertTrue(german.get(key).startsWith("%success_prefix%"), key);
        }
        for (String key : List.of("cosmetics.not-enough-money", "cosmetics.not-for-sale", "cosmetics.not-owned",
                "cosmetics.category-off", "cosmetics.cancelled", "cosmetics.failed", "cosmetics.unknown",
                "cosmetics.locked-rank", "cosmetics.locked-reward", "cosmetics.given-already",
                "cosmetics.taken-none", "cosmetics.play-none", "cosmetics.play-offline", "cosmetics.play-slot")) {
            assertTrue(english.get(key).startsWith("%error_prefix%"), key);
            assertTrue(german.get(key).startsWith("%error_prefix%"), key);
        }
        for (String key : List.of("cosmetics.already-owned", "cosmetics.already-equipped",
                "cosmetics.nothing-equipped", "cosmetics.preview", "cosmetics.equipped-elsewhere",
                "cosmetics.usage-give", "cosmetics.usage-play", "cosmetics.help-header")) {
            assertTrue(english.get(key).startsWith("%brand_prefix%"), key);
            assertTrue(german.get(key).startsWith("%brand_prefix%"), key);
        }
        assertTrue(english.get("cosmetics.usage-give").contains("\\<player> \\<cosmetic>"));
        assertTrue(german.get("cosmetics.usage-take").contains("\\<Spieler> \\<Cosmetic>"));
        assertTrue(english.get("cosmetics.usage-play")
                .contains("\\<player> \\<category> [\\<x> \\<y> \\<z>|here]"));
        assertTrue(german.get("cosmetics.usage-play")
                .contains("\\<Spieler> \\<Kategorie> [\\<x> \\<y> \\<z>|here]"));
        assertTrue(english.get("cosmetics.not-enough-money").contains("%missing%"));
        assertTrue(english.get("cosmetics.purchased").contains("%price%"));
    }

    private static Map<String, String> texts(String resource) throws IOException {
        return texts(resource, "cosmetics");
    }

    private static Map<String, String> texts(String resource, String section) throws IOException {
        try (InputStream stream = CosmeticMessagesTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            ConfigurationNode root = YamlConfigurationLoader.builder().source(() -> reader).build().load();
            Map<String, String> texts = new TreeMap<>();
            collect(root.node(section), section, texts);
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
