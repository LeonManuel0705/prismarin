package net.prismarin.velocity.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import net.prismarin.api.moderation.ChatFilterAction;
import net.prismarin.shared.chatfilter.ChatFilterSettings;
import org.junit.jupiter.api.Test;

class ChatFilterMessagesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("%[a-z_]+%");

    @Test
    void bothLanguagesShareTheChatFilterAndAltTexts() throws IOException {
        for (String section : Set.of("chat-filter", "alts")) {
            Map<String, String> german = PunishmentMessagesTest.texts("messages_de.yml", section);
            Map<String, String> english = PunishmentMessagesTest.texts("messages_en.yml", section);
            assertFalse(english.isEmpty(), section);
            assertEquals(german.keySet(), english.keySet(), section);
            german.forEach((key, text) -> {
                assertEquals(placeholders(text), placeholders(english.get(key)), section + "." + key);
                assertFalse(text.contains("\u2014") || text.contains("\u2013"), section + "." + key);
                assertFalse(english.get(key).contains("\u2014") || english.get(key).contains("\u2013"), key);
            });
        }
    }

    @Test
    void everyRuleAndActionHasAText() throws IOException {
        Map<String, String> english = PunishmentMessagesTest.texts("messages_en.yml", "chat-filter");
        for (String rule : ChatFilterSettings.BUILT_IN) {
            assertTrue(english.containsKey("reason." + rule), rule);
        }
        for (ChatFilterAction action : ChatFilterAction.values()) {
            assertTrue(english.containsKey("action." + action.id()), action.id());
        }
        for (String key : Set.of("name", "blocked", "changed", "warned", "reason.custom", "staff", "staff-hover",
                "escalated", "escalation-note", "report-details")) {
            assertTrue(english.containsKey(key), key);
        }
        Map<String, String> alts = PunishmentMessagesTest.texts("messages_en.yml", "alts");
        for (String key : Set.of("usage", "header", "none", "entry", "online", "offline", "banned", "muted", "hover",
                "inline", "inline-banned", "inline-muted", "inline-separator", "inline-more", "inline-none",
                "join-alert", "join-denied", "join-hover", "removed")) {
            assertTrue(alts.containsKey(key), key);
        }
        assertTrue(PunishmentMessagesTest.texts("messages_en.yml", "info").containsKey("alts"));
        assertTrue(PunishmentMessagesTest.texts("messages_de.yml", "info").containsKey("alts"));
    }

    private static Set<String> placeholders(String text) {
        return Set.copyOf(PLACEHOLDER.matcher(text).results().map(MatchResult::group).toList());
    }
}
