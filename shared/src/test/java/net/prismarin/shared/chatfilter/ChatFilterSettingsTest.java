package net.prismarin.shared.chatfilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import net.prismarin.api.moderation.ChatFilterAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChatFilterSettingsTest {

    @TempDir
    Path directory;

    @Test
    void theFirstStartWritesCommentedDefaults() throws IOException {
        Path file = directory.resolve(ChatFilterSettings.FILE_NAME);
        ChatFilterSettings settings = ChatFilterSettings.load(file, false);
        String written = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(written.startsWith("# Chat filter of this server."));
        assertTrue(written.contains("# A plain entry matches whole words only"));
        assertTrue(written.contains("top-level-domains:"));
        assertFalse(written.contains("proxy-commands"));
        assertEquals(List.of(), settings.problems());
        assertEquals("***", settings.replacement());
        assertEquals(ChatFilterAction.BLOCK, settings.words().policy().action());
        assertEquals(ChatFilterAction.REPLACE, settings.caps().policy().action());
        assertEquals(70, settings.caps().percent());
        assertEquals(8, settings.caps().minLetters());
        assertEquals(4, settings.flood().messages());
        assertEquals(5_000L, settings.flood().windowMillis());
        assertEquals(30_000L, settings.repeat().windowMillis());
        assertEquals(Set.of("private"), settings.repeat().policy().skipChannels());
        assertEquals("chat", settings.words().policy().escalation().template());
        assertEquals(600_000L, settings.words().policy().escalation().windowMillis());
        assertTrue(settings.words().policy().escalation().report());
        assertEquals("spam", settings.links().policy().escalation().reportReason());
        assertFalse(settings.caps().policy().escalation().enabled());
        assertFalse(settings.commands().enabled());
        assertEquals(settings, ChatFilterSettings.load(file, false));
    }

    @Test
    void theProxyFileListsChatCommands() throws IOException {
        Path file = directory.resolve(ChatFilterSettings.FILE_NAME);
        ChatFilterSettings settings = ChatFilterSettings.load(file, true);
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("proxy-commands:"));
        assertTrue(settings.commands().enabled());
        assertTrue(settings.commands().channels().get("private").contains("msg <player> <message>"));
        assertTrue(settings.commands().channels().get("party").contains("party chat <message>"));
    }

    @Test
    void ownValuesStayAndMistakesAreReported() throws IOException {
        Path file = directory.resolve(ChatFilterSettings.FILE_NAME);
        Files.writeString(file, String.join("\n",
                "replacement: \"[removed]\"",
                "words:",
                "  action: warn",
                "  list: [noob]",
                "caps:",
                "  percent: 50",
                "repeat:",
                "  action: replace",
                "flood:",
                "  within: soon",
                "links:",
                "  allowed-domains: [example.net]",
                "  escalation:",
                "    template: \"Bad Id!\"",
                "custom:",
                "  my-rule:",
                "    action: log",
                "    notify-staff: false",
                "  words:",
                "    action: block",
                ""), StandardCharsets.UTF_8);
        ChatFilterSettings settings = ChatFilterSettings.load(file, false);
        assertEquals("[removed]", settings.replacement());
        assertEquals(ChatFilterAction.WARN, settings.words().policy().action());
        assertEquals(List.of("noob"), settings.words().list());
        assertEquals(50, settings.caps().percent());
        assertEquals(ChatFilterAction.BLOCK, settings.repeat().policy().action());
        assertEquals(5_000L, settings.flood().windowMillis());
        assertEquals(List.of("example.net"), settings.links().allowedDomains());
        assertEquals("", settings.links().policy().escalation().template());
        assertEquals(ChatFilterAction.LOG, settings.policy("my-rule").action());
        assertFalse(settings.policy("my-rule").notifyStaff());
        assertEquals(4, settings.problems().size());
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("min-letters: 8"));
    }

    @Test
    void unknownRulesUseTheCustomDefaults() {
        ChatFilterSettings settings = ChatFilterSettings.defaults(false);
        ChatFilterSettings.RulePolicy policy = settings.policy("anticheat-chat");
        assertNull(policy.action());
        assertTrue(policy.notifyStaff());
        assertFalse(policy.escalation().enabled());
        assertEquals(settings.words().policy(), settings.policy(ChatFilterSettings.WORDS));
    }

    @Test
    void brokenFilesFallBackToTheDefaults() throws IOException {
        Path file = directory.resolve(ChatFilterSettings.FILE_NAME);
        Files.writeString(file, "words: [unclosed\n  - : :", StandardCharsets.UTF_8);
        ChatFilterSettings settings = ChatFilterSettings.load(file, false);
        assertEquals(1, settings.problems().size());
        assertEquals(ChatFilterSettings.DEFAULT_WORDS, settings.words().list());
    }

    @Test
    void durationsAreWrittenReadably() {
        assertEquals("30s", ChatFilterSettings.text(30_000L));
        assertEquals("10m", ChatFilterSettings.text(600_000L));
        assertEquals("2h", ChatFilterSettings.text(7_200_000L));
        assertEquals("7d", ChatFilterSettings.text(604_800_000L));
        assertEquals("90s", ChatFilterSettings.text(90_000L));
    }

    @Test
    void escalationValuesAreClamped() {
        ChatFilterSettings.Escalation escalation = new ChatFilterSettings.Escalation(true, 0, 1L, " Chat ", true,
                "spam");
        assertEquals(1, escalation.violations());
        assertEquals(1_000L, escalation.windowMillis());
        assertEquals("chat", escalation.template());
        assertTrue(escalation.punishes());
        assertFalse(new ChatFilterSettings.Escalation(true, 3, 60_000L, "", true, "").punishes());
    }
}
