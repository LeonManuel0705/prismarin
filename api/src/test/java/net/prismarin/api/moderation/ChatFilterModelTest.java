package net.prismarin.api.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatFilterModelTest {

    @Test
    void messagesFillTheirDefaults() {
        ChatMessage message = new ChatMessage(null, null, " Party ", "hi");
        assertEquals(ChatMessage.NOBODY, message.sender());
        assertEquals("", message.senderName());
        assertEquals("party", message.channel());
        assertTrue(message.anonymous());
        assertEquals(ChatMessage.PUBLIC, ChatMessage.of(UUID.randomUUID(), "Steve", "hi").channel());
        assertEquals(ChatMessage.TEXT, ChatMessage.text("sign").channel());
        assertEquals("bye", message.withText("bye").text());
        assertThrows(NullPointerException.class, () -> ChatMessage.text(null));
    }

    @Test
    void matchesCheckTheirRuleAndText() {
        ChatFilterMatch match = ChatFilterMatch.replace("Words", "idiot", "you ***");
        assertEquals("words", match.rule());
        assertEquals("you ***", match.text());
        assertThrows(IllegalArgumentException.class, () -> ChatFilterMatch.block("bad id", ""));
        assertThrows(IllegalArgumentException.class, () -> ChatFilterMatch.replace("words", "", ""));
        assertEquals(ChatFilterAction.WARN, match.as(ChatFilterAction.WARN).action());
        assertEquals("", match.as(ChatFilterAction.WARN).text());
        assertEquals(ChatFilterAction.BLOCK, ChatFilterMatch.warn("caps", "").as(ChatFilterAction.REPLACE).action());
        assertEquals(match, match.as(ChatFilterAction.REPLACE));
        assertEquals(ChatFilterAction.LOG, ChatFilterMatch.log("flood", "").action());
    }

    @Test
    void resultsDescribeWhatHappened() {
        ChatFilterResult untouched = ChatFilterResult.untouched("hello");
        assertTrue(untouched.clean());
        assertFalse(untouched.changed());
        assertTrue(untouched.blocking().isEmpty());

        ChatFilterResult changed = new ChatFilterResult("HELLO", "Hello", false,
                List.of(ChatFilterMatch.replace("caps", "100%", "Hello"), ChatFilterMatch.warn("caps", "")));
        assertTrue(changed.changed());
        assertTrue(changed.warned());
        assertEquals(List.of("caps"), changed.rules());

        ChatFilterResult blocked = new ChatFilterResult("x", "x", true, List.of(ChatFilterMatch.block("words", "x")));
        assertFalse(blocked.changed());
        assertEquals("words", blocked.blocking().orElseThrow().rule());
    }

    @Test
    void actionsAreFoundByTheirId() {
        assertEquals(ChatFilterAction.REPLACE, ChatFilterAction.byId(" Replace ").orElseThrow());
        assertTrue(ChatFilterAction.byId("kick").isEmpty());
        assertTrue(ChatFilterAction.byId(null).isEmpty());
        assertEquals("log", ChatFilterAction.LOG.id());
    }

    @Test
    void filteredEventsCanBeCancelled() {
        ChatFilteredEvent event = new ChatFilteredEvent(ChatMessage.text("x"), ChatFilterResult.untouched("x"), null);
        assertFalse(event.cancelled());
        event.setCancelled(true);
        assertTrue(event.cancelled());
        assertEquals("", event.server());
    }

    @Test
    void altsShareAtLeastOneAddress() {
        AltAccount alt = new AltAccount(UUID.randomUUID(), null, 1L, 2L, 1, false, true);
        assertEquals("", alt.name());
        assertTrue(alt.punished());
        assertThrows(IllegalArgumentException.class, () -> new AltAccount(UUID.randomUUID(), "x", 1L, 2L, 0, false,
                false));
        BannedAltJoinEvent event = new BannedAltJoinEvent(UUID.randomUUID(), "Steve", null, false);
        assertEquals(List.of(), event.bannedAlts());
        assertTrue(ChatFilterRule.ID.matcher("anticheat-chat").matches());
        assertFalse(ChatFilterRule.ID.matcher("-starts-with-dash").matches());
    }
}
