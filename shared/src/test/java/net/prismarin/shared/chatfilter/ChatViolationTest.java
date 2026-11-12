package net.prismarin.shared.chatfilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.UUID;
import net.prismarin.api.moderation.ChatFilterAction;
import net.prismarin.api.moderation.ChatFilterMatch;
import net.prismarin.api.moderation.ChatFilterResult;
import net.prismarin.api.moderation.ChatMessage;
import org.junit.jupiter.api.Test;

class ChatViolationTest {

    private final UUID player = UUID.randomUUID();
    private final ChatFilterSettings settings = ChatFilterSettings.defaults(false);

    private ChatViolation sample() {
        ChatMessage message = ChatMessage.of(player, "Steve", "DU HURENSOHN");
        ChatFilterResult result = new ChatFilterResult(message.text(), message.text(), true, List.of(
                ChatFilterMatch.block(ChatFilterSettings.WORDS, "*hurensohn*"),
                ChatFilterMatch.replace(ChatFilterSettings.CAPS, "100%", "Du hurensohn")));
        return ChatViolation.of(message, result, settings);
    }

    @Test
    void onlyRulesThatNeedTheProxyAreSent() {
        ChatViolation violation = sample();
        assertEquals(1, violation.entries().size());
        ChatViolation.Entry entry = violation.entries().getFirst();
        assertEquals(ChatFilterSettings.WORDS, entry.rule());
        assertTrue(entry.alert());
        assertTrue(entry.audit());
        assertEquals("chat", entry.escalation().template());
        assertTrue(violation.blocked());
        assertEquals("", violation.shown());
        assertTrue(violation.alerts());
    }

    @Test
    void violationsSurviveTheWire() {
        ChatViolation violation = sample();
        JsonObject wire = JsonParser.parseString(violation.toJson().toString()).getAsJsonObject();
        assertEquals(ChatViolation.TYPE, wire.get("type").getAsString());
        assertEquals(player.toString(), wire.get("uuid").getAsString());
        assertEquals(violation, ChatViolation.parse(wire).orElseThrow());
    }

    @Test
    void strangeMessagesAreRejectedOrCleaned() {
        assertTrue(ChatViolation.parse(null).isEmpty());
        JsonObject wrongType = sample().toJson();
        wrongType.addProperty("type", "report");
        assertTrue(ChatViolation.parse(wrongType).isEmpty());
        JsonObject badPlayer = sample().toJson();
        badPlayer.addProperty("uuid", "nobody");
        assertTrue(ChatViolation.parse(badPlayer).isEmpty());

        JsonObject odd = sample().toJson();
        odd.addProperty("message", "x".repeat(2_000));
        odd.addProperty("channel", "Not A Channel");
        JsonArray entries = odd.getAsJsonArray("entries");
        JsonObject broken = new JsonObject();
        broken.addProperty("rule", "Bad Rule");
        broken.addProperty("action", "block");
        entries.add(broken);
        JsonObject loud = entries.get(0).getAsJsonObject();
        loud.getAsJsonObject("escalation").addProperty("violations", -5);
        loud.getAsJsonObject("escalation").addProperty("window", Long.MAX_VALUE);
        loud.getAsJsonObject("escalation").addProperty("template", "../../etc");
        ChatViolation parsed = ChatViolation.parse(odd).orElseThrow();
        assertEquals(ChatViolation.MAX_TEXT, parsed.message().length());
        assertEquals(ChatMessage.PUBLIC, parsed.channel());
        assertEquals(1, parsed.entries().size());
        assertEquals(1, parsed.entries().getFirst().escalation().violations());
        assertEquals(604_800_000L, parsed.entries().getFirst().escalation().windowMillis());
        assertEquals("", parsed.entries().getFirst().escalation().template());
    }

    @Test
    void cleanResultsSendNothing() {
        ChatMessage message = ChatMessage.of(player, "Steve", "hello");
        ChatViolation violation = ChatViolation.of(message, ChatFilterResult.untouched("hello"), settings);
        assertTrue(violation.empty());
        assertFalse(violation.alerts());
        assertEquals(ChatFilterAction.BLOCK, ChatFilterAction.byId("BLOCK").orElseThrow());
    }
}
