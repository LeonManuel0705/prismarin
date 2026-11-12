package net.prismarin.shared.chatfilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class ChatStateTest {

    @Test
    void capsNeedEnoughLettersAndShare() {
        assertTrue(CapsCheck.percent("OK", 8).isEmpty());
        assertTrue(CapsCheck.percent("GG WP", 8).isEmpty());
        assertTrue(CapsCheck.percent("1234 !!", 1).isEmpty());
        assertEquals(OptionalInt.of(100), CapsCheck.percent("HELLO EVERYONE HOW ARE YOU", 8));
        assertEquals(OptionalInt.of(24), CapsCheck.percent("Hello Everyone, Nice To See You", 8));
        assertTrue(CapsCheck.percent("I am OK with that", 8).getAsInt() < 70);
    }

    @Test
    void softenedTextStartsWithACapital() {
        assertEquals("Hello everyone, how are you?", CapsCheck.soften("HELLO EVERYONE, HOW ARE YOU?"));
        assertEquals("... Wow", CapsCheck.soften("... WOW"));
        assertEquals("123", CapsCheck.soften("123"));
    }

    @Test
    void floodingCountsAttemptsInTheWindow() {
        ChatState state = new ChatState();
        for (int message = 0; message < 4; message++) {
            assertFalse(state.flooding(1_000L + message, 4, 5_000L));
        }
        assertTrue(state.flooding(1_010L, 4, 5_000L));
        assertTrue(state.flooding(1_020L, 4, 5_000L));
        assertFalse(state.flooding(10_000L, 4, 5_000L));
    }

    @Test
    void repeatsAreFoundWhileTheyAreRemembered() {
        ChatState state = new ChatState();
        String text = TextNormalizer.simplify("join my server now");
        assertTrue(state.repeated(text, 0L, 30_000L, 85, 4).isEmpty());
        state.remember(text, 0L, 3);
        assertEquals(OptionalInt.of(100), state.repeated(TextNormalizer.simplify("JOIN my server now!!"), 1_000L,
                30_000L, 85, 4));
        assertTrue(state.repeated(TextNormalizer.simplify("join my server now1"), 2_000L, 30_000L, 85, 4)
                .getAsInt() >= 85);
        assertTrue(state.repeated(TextNormalizer.simplify("what a nice build"), 3_000L, 30_000L, 85, 4).isEmpty());
        assertTrue(state.repeated(text, 40_000L, 30_000L, 85, 4).isEmpty());
    }

    @Test
    void shortMessagesMayRepeat() {
        ChatState state = new ChatState();
        state.remember("gg", 0L, 3);
        assertTrue(state.repeated("gg", 100L, 30_000L, 85, 4).isEmpty());
    }

    @Test
    void historyIsLimited() {
        ChatState state = new ChatState();
        state.remember("first message", 0L, 2);
        state.remember("second message", 1L, 2);
        state.remember("third message", 2L, 2);
        assertTrue(state.repeated("first message", 3L, 30_000L, 100, 4).isEmpty());
        assertEquals(OptionalInt.of(100), state.repeated("third message", 3L, 30_000L, 100, 4));
    }

    @Test
    void similarityUsesTheEditDistance() {
        assertEquals(100, ChatState.similarity("abc", "abc"));
        assertEquals(0, ChatState.similarity("abc", "xyz"));
        assertEquals(3, ChatState.distance("kitten", "sitting"));
        assertEquals(100, ChatState.similarity("", ""));
    }
}
