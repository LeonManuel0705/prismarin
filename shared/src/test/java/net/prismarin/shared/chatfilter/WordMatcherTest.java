package net.prismarin.shared.chatfilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WordMatcherTest {

    private static WordMatcher matcher(List<String> words) {
        return matcher(words, List.of(), List.of());
    }

    private static WordMatcher matcher(List<String> words, List<String> patterns, List<String> allowed) {
        List<String> problems = new ArrayList<>();
        WordMatcher matcher = WordMatcher.compile(words, patterns, allowed, problems);
        assertEquals(List.of(), problems);
        return matcher;
    }

    private static boolean hit(WordMatcher matcher, String text) {
        return !matcher.find(text).isEmpty();
    }

    private static String masked(WordMatcher matcher, String text) {
        return Span.replace(text, matcher.find(text), "***");
    }

    @Test
    void plainWordsMatchWithoutCase() {
        WordMatcher matcher = matcher(List.of("idiot"));
        assertTrue(hit(matcher, "you IDIOT"));
        assertTrue(hit(matcher, "Idiot."));
        assertFalse(hit(matcher, "you are nice"));
    }

    @Test
    void repeatedLettersLeetspeakAndSeparatorsStillMatch() {
        WordMatcher matcher = matcher(List.of("idiot", "fuck"));
        assertTrue(hit(matcher, "iiiidiooot"));
        assertTrue(hit(matcher, "1d10t"));
        assertTrue(hit(matcher, "f.u.c.k"));
        assertTrue(hit(matcher, "f u c k this"));
        assertTrue(hit(matcher, "f-u-c-k"));
        assertTrue(hit(matcher, "FUUUUUCK"));
        assertTrue(hit(matcher, "you,idiot"));
        assertTrue(hit(matcher, "\u0456diot"));
    }

    @Test
    void wholeWordsLeaveScunthorpeAlone() {
        WordMatcher matcher = matcher(List.of("cunt", "ass", "hell", "tit"));
        assertFalse(hit(matcher, "I live in Scunthorpe"));
        assertFalse(hit(matcher, "the assassin class is fun"));
        assertFalse(hit(matcher, "Hello everyone"));
        assertFalse(hit(matcher, "what a great title"));
        assertFalse(hit(matcher, "Passwort vergessen"));
        assertTrue(hit(matcher, "you ass"));
    }

    @Test
    void doubledLettersInAnEntryStayRequired() {
        WordMatcher matcher = matcher(List.of("nigger"));
        assertFalse(hit(matcher, "I flew to Niger"));
        assertTrue(hit(matcher, "n1gg3r"));
        assertTrue(hit(matcher, "niggggger"));
    }

    @Test
    void wildcardsMatchStartsEndsAndParts() {
        WordMatcher matcher = matcher(List.of("hurensohn*", "*fotze", "*schwuchtel*"));
        assertTrue(hit(matcher, "hurensohnmutter"));
        assertTrue(hit(matcher, "du Hurens\u00F6hne"));
        assertTrue(hit(matcher, "drecksfotze"));
        assertTrue(hit(matcher, "fotze"));
        assertTrue(hit(matcher, "riesenschwuchtelbande"));
        assertFalse(hit(matcher, "sohn"));
        assertFalse(hit(matcher, "fotzen"));
    }

    @Test
    void phrasesMatchWordsInARow() {
        WordMatcher matcher = matcher(List.of("kill yourself", "kys"));
        assertTrue(hit(matcher, "just kill yourself"));
        assertTrue(hit(matcher, "k1ll y0urself!"));
        assertTrue(hit(matcher, "killyourself"));
        assertTrue(hit(matcher, "kill-yourself"));
        assertTrue(hit(matcher, "k y s"));
        assertFalse(hit(matcher, "I will kill the dragon yourself"));
        assertFalse(hit(matcher, "my keys are gone"));
    }

    @Test
    void replacementKeepsPunctuationAroundTheWord() {
        WordMatcher matcher = matcher(List.of("idiot", "kill yourself"));
        assertEquals("you ***!", masked(matcher, "you idiot!"));
        assertEquals("(***)", masked(matcher, "(idiot)"));
        assertEquals("go *** now", masked(matcher, "go kill yourself now"));
        assertEquals("*** hi", masked(matcher, "i d i o t hi"));
    }

    @Test
    void patternsAreRegularExpressionsWithoutCase() {
        WordMatcher matcher = matcher(List.of(), List.of("free\\s*vbucks"), List.of());
        assertTrue(hit(matcher, "FREE VBUCKS here"));
        assertTrue(hit(matcher, "freevbucks"));
        assertFalse(hit(matcher, "free stuff"));
    }

    @Test
    void allowedWordsNeverCount() {
        WordMatcher matcher = matcher(List.of("*cunt*"), List.of(), List.of("Scunthorpe"));
        assertFalse(hit(matcher, "Scunthorpe United"));
        assertTrue(hit(matcher, "you cunt"));
    }

    @Test
    void brokenEntriesAreReported() {
        List<String> problems = new ArrayList<>();
        WordMatcher matcher = WordMatcher.compile(List.of("...", " ", "fine"), List.of("([a"), List.of(), problems);
        assertEquals(2, problems.size());
        assertTrue(hit(matcher, "fine"));
        assertFalse(matcher.empty());
        assertTrue(WordMatcher.compile(List.of(), List.of(), List.of(), new ArrayList<>()).empty());
    }

    @Test
    void normalCapitalWordsAndNumbersPass() {
        WordMatcher matcher = matcher(ChatFilterSettings.DEFAULT_WORDS);
        for (String text : List.of("OK", "GG WP", "Wie geht's dir?", "1v1 me on bw-2", "Klassenarbeit morgen",
                "Ich spiele Bedwars", "The Niger river is long", "Hast du Zeit?", "a b c", "Spastik ist eine Krankheit",
                "my keys", "fagottino", "retardant", "Assassins Creed", "Scunthorpe")) {
            assertFalse(hit(matcher, text), text);
        }
        for (String text : List.of("du hurensohn", "HURENSOHN!!!", "kys", "fick dich", "f1ck d1ch", "du spast")) {
            assertTrue(hit(matcher, text), text);
        }
    }
}
