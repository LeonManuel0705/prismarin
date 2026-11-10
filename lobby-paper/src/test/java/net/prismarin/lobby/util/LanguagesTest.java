package net.prismarin.lobby.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LanguagesTest {

    private static final List<String> LANGUAGES = List.of("de", "en", "pt-br");

    @Test
    void resolvesClientLocalesToLoadedLanguages() {
        assertEquals("de", Languages.resolve(LANGUAGES, Locale.forLanguageTag("de-AT"), "en"));
        assertEquals("en", Languages.resolve(LANGUAGES, Locale.US, "de"));
        assertEquals("pt-br", Languages.resolve(LANGUAGES, Locale.forLanguageTag("pt-BR"), "de"));
    }

    @Test
    void unknownLocalesFallBack() {
        assertEquals("de", Languages.resolve(LANGUAGES, Locale.FRENCH, "DE"));
        assertEquals("de", Languages.resolve(LANGUAGES, null, "de"));
        assertEquals("de", Languages.resolve(LANGUAGES, Locale.FRENCH, "es"));
    }

    @Test
    void togglesOnlyBetweenExactlyTwoLanguages() {
        assertEquals(Optional.of("en"), Languages.toggleTarget(List.of("de", "en"), "de"));
        assertEquals(Optional.of("de"), Languages.toggleTarget(List.of("de", "en"), "en"));
        assertEquals(Optional.of("de"), Languages.toggleTarget(List.of("de", "en"), "fr"));
        assertEquals(Optional.empty(), Languages.toggleTarget(LANGUAGES, "de"));
        assertEquals(Optional.empty(), Languages.toggleTarget(List.of("de"), "de"));
    }
}
