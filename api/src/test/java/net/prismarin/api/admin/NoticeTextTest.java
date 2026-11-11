package net.prismarin.api.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NoticeTextTest {

    @Test
    void picksExactTagThenLanguageThenAnyThenEnglish() {
        NoticeText text = NoticeText.of("any").and("de", "deutsch").and("en_GB", "british").and("en", "english");

        assertEquals("british", text.template(Locale.forLanguageTag("en-GB")));
        assertEquals("english", text.template(Locale.forLanguageTag("en-US")));
        assertEquals("deutsch", text.template(Locale.GERMANY));
        assertEquals("any", text.template(Locale.FRENCH));
        assertEquals("any", text.template(null));
        assertEquals("english", NoticeText.of(Map.of("en", "english", "de", "deutsch")).template(Locale.ITALIAN));
    }

    @Test
    void placeholdersAreCopiedAndValidated() {
        NoticeText base = NoticeText.of("%player% flagged");
        NoticeText filled = base.with("player", "Leon").with("check", null);

        assertEquals(Map.of(), base.placeholders());
        assertEquals("Leon", filled.placeholders().get("player"));
        assertEquals("", filled.placeholders().get("check"));
        assertThrows(IllegalArgumentException.class, () -> base.with("Player", "x"));
        assertThrows(IllegalArgumentException.class, () -> base.with(null, "x"));
        assertThrows(IllegalArgumentException.class, () -> base.with("player", "x".repeat(257)));
    }

    @Test
    void brokenTextsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> NoticeText.of(""));
        assertThrows(IllegalArgumentException.class, () -> NoticeText.of((String) null));
        assertThrows(IllegalArgumentException.class, () -> NoticeText.of(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> NoticeText.of("x".repeat(1025)));
        assertThrows(IllegalArgumentException.class, () -> NoticeText.of("ok").and("not a tag", "x"));
        Map<String, String> many = new LinkedHashMap<>();
        for (int index = 0; index < 17; index++) {
            many.put("l" + (char) ('a' + index) + "x", "text");
        }
        assertThrows(IllegalArgumentException.class, () -> NoticeText.of(many));
    }
}
