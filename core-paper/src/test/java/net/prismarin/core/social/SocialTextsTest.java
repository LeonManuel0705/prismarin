package net.prismarin.core.social;

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

class SocialTextsTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("%[a-z_-]+%");
    private static final List<String> BLOCKS = List.of("friends", "party", "lobby", "clan");

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
    void menusFindTheirTexts() throws IOException {
        Map<String, String> english = texts("messages_en.yml");
        for (String key : List.of("lobby.item.nick-tool.name", "lobby.item.silent.on", "lobby.item.silent.off",
                "lobby.silent.on", "lobby.silent.off", "lobby.nick-tool.title", "lobby.nick-tool.auto.on",
                "lobby.profile.friends.name", "lobby.profile.party.name", "friends.menu.title",
                "friends.menu.unavailable", "friends.value.on", "friends.value.friends-of-friends",
                "friends.time.months", "party.menu.title", "party.menu.disband.yes", "friends.menu.confirm.no",
                "lobby.profile.clan.name", "lobby.profile.clan.member", "lobby.item.profile.lore.clan",
                "friends.menu.friend.clan", "party.menu.member.clan", "friends.menu.setting.clan-invites",
                "clan.menu.title", "clan.menu.disband.yes", "clan.menu.transfer.yes", "clan.menu.access.open",
                "clan.menu.access.invite-only", "clan.role.co-leader", "clan.menu.member.co-leader")) {
            assertTrue(english.containsKey(key), key);
        }
    }

    @Test
    void partyMenusFindTheirRoleTexts() throws IOException {
        for (String resource : List.of("messages_de.yml", "messages_en.yml")) {
            Map<String, String> texts = texts(resource);
            for (String key : List.of("party.menu.member.moderator", "party.menu.member.role", "party.menu.info.role",
                    "party.menu.moderator.name", "party.menu.demote.name", "party.menu.transfer.name",
                    "party.menu.transfer.yes", "party.menu.settings.title", "party.menu.settings.locked",
                    "party.menu.setting.invite", "party.menu.setting.access", "party.menu.setting.follow",
                    "party.menu.setting.chat", "party.menu.role.leader", "party.menu.role.moderator",
                    "party.menu.role.member")) {
                assertTrue(texts.containsKey(key), resource + " " + key);
            }
            for (String value : List.of("leader", "moderators", "members", "invite-only", "friends", "open", "on",
                    "off")) {
                assertTrue(texts.containsKey("party.menu.value." + value), resource + " " + value);
            }
            assertTrue(texts.get("friends.menu.empty.lore").contains("/friend add \\<"), resource);
        }
    }

    private static Set<String> placeholders(String text) {
        return Set.copyOf(PLACEHOLDER.matcher(text).results().map(MatchResult::group).toList());
    }

    private static Map<String, String> texts(String resource) throws IOException {
        try (InputStream stream = SocialTextsTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            ConfigurationNode root = YamlConfigurationLoader.builder().source(() -> reader).build().load();
            Map<String, String> texts = new TreeMap<>();
            for (String block : BLOCKS) {
                collect(root.node(block), block, texts);
            }
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
