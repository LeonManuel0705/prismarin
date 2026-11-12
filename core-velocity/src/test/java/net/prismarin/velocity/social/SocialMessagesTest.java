package net.prismarin.velocity.social;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.prismarin.api.social.ClanAccess;
import net.prismarin.api.social.ClanRole;
import net.prismarin.api.social.PartyRole;
import net.prismarin.shared.text.TextFormatter;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

class SocialMessagesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("%[a-z_-]+%");
    private static final List<String> BLOCKS = List.of("friends", "party", "msg", "clan");

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
    void everySettingAndValueHasALabel() throws IOException {
        Map<String, String> english = texts("messages_en.yml");
        for (SocialSetting setting : SocialSetting.values()) {
            assertTrue(english.containsKey("friends.setting." + setting.id()), setting.id());
            for (String option : setting.options()) {
                assertTrue(english.containsKey("friends.value." + option), option);
            }
        }
        for (String unit : List.of("unknown", "now", "minutes", "hours", "days", "months")) {
            assertTrue(english.containsKey("friends.time." + unit), unit);
        }
        for (String action : List.of("add", "accept", "deny", "cancel", "remove", "list", "requests", "jump",
                "settings", "help")) {
            assertTrue(english.containsKey("friends.help." + action), action);
        }
        for (String action : List.of("invite", "accept", "deny", "join", "leave", "kick", "promote", "demote",
                "transfer", "disband", "list", "warp", "chat", "settings", "help")) {
            assertTrue(english.containsKey("party.help." + action), action);
        }
        for (PartyOption option : PartyOption.values()) {
            assertTrue(english.containsKey("party.setting." + option.id()), option.id());
            for (String value : option.options()) {
                assertTrue(english.containsKey("party.value." + value), value);
            }
        }
        for (String action : SubCommands.CLANS.actions()) {
            assertTrue(english.containsKey("clan.help." + action), action);
        }
        for (ClanRole role : ClanRole.values()) {
            assertTrue(english.containsKey("clan.role." + role.id()), role.id());
            assertTrue(english.containsKey("clan.chat-line." + role.id()), role.id());
            assertTrue(english.containsKey("clan.list-" + role.id()), role.id());
            assertTrue(english.containsKey("clan.list-" + role.id() + "-offline"), role.id());
        }
        for (ClanAccess access : ClanAccess.values()) {
            assertTrue(english.containsKey("clan.access." + access.id()), access.id());
        }
        for (String kind : List.of("name", "tag")) {
            for (String check : List.of("too-short", "too-long", "characters", "blocked")) {
                assertTrue(english.containsKey("clan." + kind + "-" + check), kind + check);
            }
        }
        for (PartyRole role : PartyRole.values()) {
            assertTrue(english.containsKey("party.role." + role.id()), role.id());
            assertTrue(english.containsKey("party.list-" + role.id()), role.id());
            assertTrue(english.containsKey("party.list-" + role.id() + "-offline"), role.id());
        }
    }

    @Test
    void helpAndUsageShowPlaceholdersInAngleBrackets() throws IOException {
        TextFormatter formatter = TextFormatter.plain();
        Pattern escaped = Pattern.compile("\\\\<([^>]+)>");
        for (String resource : List.of("messages_de.yml", "messages_en.yml")) {
            int checked = 0;
            for (Map.Entry<String, String> entry : texts(resource).entrySet()) {
                String key = entry.getKey();
                if (!key.contains(".help.") && !key.contains("usage")) {
                    continue;
                }
                String rendered = plain(formatter, entry.getValue());
                assertFalse(rendered.contains("\\"), key);
                Matcher matcher = escaped.matcher(entry.getValue());
                while (matcher.find()) {
                    assertTrue(rendered.contains("<" + matcher.group(1) + ">"), key + ": " + rendered);
                    checked++;
                }
            }
            assertTrue(checked > 25, resource);
        }
        Map<String, String> english = texts("messages_en.yml");
        assertEquals(" /cmd add <name> » send a friend request", plain(formatter, english.get("friends.help.add")));
        assertTrue(plain(formatter, english.get("msg.usage")).contains("Usage: /cmd <player> <message>"));
        assertEquals(" /cmd promote <player> » make a member moderator (leader)",
                plain(formatter, english.get("party.help.promote")));
        assertEquals(" /cmd list [<page>] » show your friends", plain(formatter, english.get("friends.help.list")));
        assertEquals(" /cmd create <name> <tag> » found a clan", plain(formatter, english.get("clan.help.create")));
        assertFalse(plain(formatter, english.get("clan.usage-color")).contains("&"));
    }

    private static String plain(TextFormatter formatter, String text) {
        return PlainTextComponentSerializer.plainText().serialize(formatter.format(text,
                Placeholder.unparsed("command", "/cmd"), Placeholder.unparsed("player", "Leon"),
                Placeholder.unparsed("name", "Leon"), Placeholder.unparsed("message", "hi")));
    }

    private static Set<String> placeholders(String text) {
        return Set.copyOf(PLACEHOLDER.matcher(text).results().map(MatchResult::group).toList());
    }

    private static Map<String, String> texts(String resource) throws IOException {
        try (InputStream stream = SocialMessagesTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            ConfigurationNode root = YamlConfigurationLoader.builder().source(() -> reader).build().load();
            Map<String, String> texts = new TreeMap<>();
            for (String block : BLOCKS) {
                collect(root.node(block), block, texts);
            }
            assertFalse(texts.isEmpty(), resource);
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
