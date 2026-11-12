package net.prismarin.shared.chatfilter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TextNormalizerTest {

    @Test
    void foldingDropsCaseAccentsAndLookalikes() {
        assertEquals("hello", TextNormalizer.fold("H\u00C9LL\u00D6"));
        assertEquals("strasse", TextNormalizer.fold("Stra\u00DFe"));
        assertEquals("fuck", TextNormalizer.fold("\uFF46\uFF55\uFF43\uFF4B"));
        assertEquals("kopp", TextNormalizer.fold("\u043A\u043E\u0440\u0440"));
    }

    @Test
    void canonicalWordsResolveLeetspeakAndDropPunctuation() {
        assertEquals("idiot", TextNormalizer.canonical("1d10t"));
        assertEquals("fuck", TextNormalizer.canonical("f.u.c.k"));
        assertEquals("shit", TextNormalizer.canonical("$h!t"));
        assertEquals("ass", TextNormalizer.canonical("@ss"));
        assertEquals("", TextNormalizer.canonical("...??"));
        assertEquals("fuck", TextNormalizer.canonical("fu\u200Bck"));
    }

    @Test
    void leetCharactersAreKnown() {
        assertTrue(TextNormalizer.leet('@'));
        assertTrue(TextNormalizer.leet('0'));
        assertFalse(TextNormalizer.leet(','));
        assertFalse(TextNormalizer.leet('a'));
    }

    @Test
    void trimmingSkipsPunctuationAtTheEdges() {
        String text = "(idiot!)";
        assertArrayEquals(new int[]{1, 6}, TextNormalizer.trimmed(text, 0, text.length()));
        assertArrayEquals(new int[]{0, 0}, TextNormalizer.trimmed("!!", 0, 0));
    }

    @Test
    void simplifiedTextIgnoresPunctuationCaseAndLongRuns() {
        assertEquals("hello there", TextNormalizer.simplify("  HELLO,   there!!! "));
        assertEquals("hii", TextNormalizer.simplify("hiiiiiii"));
        assertEquals(TextNormalizer.simplify("Buy now!"), TextNormalizer.simplify("buy NOW"));
    }
}
