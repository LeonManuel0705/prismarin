package net.prismarin.velocity.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatCommandPatternsTest {

    private static ChatCommandPatterns defaults() {
        Map<String, List<String>> channels = new LinkedHashMap<>();
        channels.put("private", List.of("msg <player> <message>", "r <message>"));
        channels.put("party", List.of("pc <message>", "party chat <message>", "/p chat <message>"));
        List<String> problems = new ArrayList<>();
        ChatCommandPatterns patterns = ChatCommandPatterns.parse(channels, problems);
        assertEquals(List.of(), problems);
        return patterns;
    }

    @Test
    void privateMessagesSkipTheTarget() {
        ChatCommandPatterns.Match match = defaults().match("msg Alex  hey there").orElseThrow();
        assertEquals("private", match.channel());
        assertEquals("msg", match.label());
        assertEquals("hey there", match.text());
        assertEquals("msg Alex  ***", match.rebuild("***"));
    }

    @Test
    void commandsMatchWithoutCase() {
        ChatCommandPatterns.Match match = defaults().match("/MSG Alex hi").orElseThrow();
        assertEquals("hi", match.text());
        assertEquals("/MSG Alex ok", match.rebuild("ok"));
        assertEquals("wb", defaults().match("R wb").orElseThrow().text());
    }

    @Test
    void subcommandsNeedEveryWord() {
        ChatCommandPatterns patterns = defaults();
        assertEquals("party", patterns.match("party chat hello all").orElseThrow().channel());
        assertEquals("hello all", patterns.match("p chat hello all").orElseThrow().text());
        assertTrue(patterns.match("party invite Alex").isEmpty());
        assertTrue(patterns.match("party chat").isEmpty());
        assertTrue(patterns.match("msg Alex").isEmpty());
        assertTrue(patterns.match("hub").isEmpty());
        assertTrue(patterns.match("").isEmpty());
    }

    @Test
    void brokenEntriesAreReported() {
        List<String> problems = new ArrayList<>();
        ChatCommandPatterns patterns = ChatCommandPatterns.parse(Map.of("clan", List.of("<message>", "cc",
                "cc <message> extra", "cc <message>")), problems);
        assertEquals(3, problems.size());
        assertEquals("clan", patterns.match("cc gg").orElseThrow().channel());
        assertTrue(ChatCommandPatterns.parse(Map.of(), problems).empty());
    }
}
