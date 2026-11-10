package net.prismarin.shared.locale;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlLocaleServiceTest {

    @TempDir
    Path dataDirectory;

    private void writeCustomFiles() throws IOException {
        Files.writeString(dataDirectory.resolve("messages_de.yml"), """
                test:
                  only-de: "nur deutsch"
                  only-en-missing: "deutscher fallback"
                  both: "beide deutsch"
                  styled: "<gray>hallo</gray>"
                """);
        Files.writeString(dataDirectory.resolve("messages_en.yml"), """
                test:
                  only-en: "english only"
                  both: "both english"
                  styled: "<gray>hello</gray>"
                """);
    }

    private YamlLocaleService serviceWithCustomFiles() throws IOException {
        writeCustomFiles();
        return new YamlLocaleService(dataDirectory, "de");
    }

    @Test
    void resolvesKeyInRequestedLocale() throws IOException {
        YamlLocaleService service = serviceWithCustomFiles();
        assertEquals("both english", service.raw("test.both", Locale.ENGLISH));
        assertEquals("beide deutsch", service.raw("test.both", Locale.GERMAN));
    }

    @Test
    void fallsBackToDefaultLanguageWhenKeyMissingInLocale() throws IOException {
        YamlLocaleService service = serviceWithCustomFiles();
        assertEquals("nur deutsch", service.raw("test.only-de", Locale.ENGLISH));
    }

    @Test
    void fallbackFollowsConfiguredDefaultLanguage() throws IOException {
        writeCustomFiles();
        YamlLocaleService service = new YamlLocaleService(dataDirectory, "en");
        assertEquals("english only", service.raw("test.only-en", Locale.GERMAN));
        assertEquals("both english", service.raw("test.both", null));
    }

    @Test
    void unknownLanguagesResolveToDefaultLanguage() throws IOException {
        YamlLocaleService service = serviceWithCustomFiles();
        assertEquals("beide deutsch", service.raw("test.both", Locale.forLanguageTag("fr")));
    }

    @Test
    void fallsBackToKeyItselfWhenMissingEverywhere() throws IOException {
        YamlLocaleService service = serviceWithCustomFiles();
        assertEquals("test.missing", service.raw("test.missing", Locale.GERMAN));
    }

    @Test
    void nullLocaleUsesDefaultLanguage() throws IOException {
        YamlLocaleService service = serviceWithCustomFiles();
        assertEquals("beide deutsch", service.raw("test.both", null));
    }

    @Test
    void renderDeserializesMiniMessage() throws IOException {
        YamlLocaleService service = serviceWithCustomFiles();
        Component rendered = service.render("test.styled", Locale.ENGLISH);
        assertEquals(MiniMessage.miniMessage().deserialize("<gray>hello</gray>"), rendered);
        assertEquals(rendered, service.render("test.styled", Locale.ENGLISH));
    }

    @Test
    void seedsBundledLocaleFilesOnFirstRun() {
        YamlLocaleService service = new YamlLocaleService(dataDirectory, "de");
        assertTrue(Files.exists(dataDirectory.resolve("messages_de.yml")));
        assertTrue(Files.exists(dataDirectory.resolve("messages_en.yml")));
        assertNotEquals("prefix", service.raw("prefix", Locale.GERMAN));
        assertNotEquals("economy.insufficient-funds", service.raw("economy.insufficient-funds", Locale.ENGLISH));
    }

    @Test
    void discoversLanguageFilesDroppedIntoTheDataFolder() throws IOException {
        writeCustomFiles();
        Files.writeString(dataDirectory.resolve("messages_fr.yml"), """
                language:
                  name: "Français"
                  icon: "BLUE_BANNER"
                test:
                  both: "les deux"
                """);
        YamlLocaleService service = new YamlLocaleService(dataDirectory, "de");
        assertEquals(List.of("de", "en", "fr"), List.copyOf(service.languages()));
        assertEquals("les deux", service.raw("test.both", Locale.FRENCH));
        assertEquals("nur deutsch", service.raw("test.only-de", Locale.FRENCH));
        assertEquals("Français", service.raw(YamlLocaleService.LANGUAGE_NAME, Locale.FRENCH));
        assertEquals("BLUE_BANNER", service.raw(YamlLocaleService.LANGUAGE_ICON, Locale.FRENCH));
    }

    @Test
    void languageBlockFallsBackToNativeNameAndNeutralIcon() throws IOException {
        writeCustomFiles();
        Files.writeString(dataDirectory.resolve("messages_nl.yml"), "test:\n  both: \"beide\"\n");
        YamlLocaleService service = new YamlLocaleService(dataDirectory, "de");
        Locale dutch = Locale.forLanguageTag("nl");
        assertEquals("Nederlands", service.raw(YamlLocaleService.LANGUAGE_NAME, dutch));
        assertEquals("PAPER", service.raw(YamlLocaleService.LANGUAGE_ICON, dutch));
    }

    @Test
    void regionalFilesAreMatchedBeforeTheirLanguage() throws IOException {
        writeCustomFiles();
        Files.writeString(dataDirectory.resolve("messages_pt_BR.yml"), "test:\n  both: \"ambos\"\n");
        YamlLocaleService service = new YamlLocaleService(dataDirectory, "de");
        assertTrue(service.languages().contains("pt-br"));
        assertEquals("ambos", service.raw("test.both", Locale.forLanguageTag("pt-BR")));
        assertEquals("beide deutsch", service.raw("test.both", Locale.forLanguageTag("pt-PT")));
    }

    @Test
    void ignoresFilesThatAreNoLanguageFiles() throws IOException {
        writeCustomFiles();
        Files.writeString(dataDirectory.resolve("messages_fr.yml.bak"), "test:\n  both: \"x\"\n");
        Files.writeString(dataDirectory.resolve("messages.yml"), "test:\n  both: \"x\"\n");
        Files.writeString(dataDirectory.resolve("messages_de_old.yml"), "test:\n  both: \"x\"\n");
        YamlLocaleService service = new YamlLocaleService(dataDirectory, "de");
        assertEquals(List.of("de", "en"), List.copyOf(service.languages()));
    }

    @Test
    void skipsBrokenLanguageFilesInsteadOfFailing() throws IOException {
        writeCustomFiles();
        Files.writeString(dataDirectory.resolve("messages_fr.yml"), "test:\n  both: [unclosed\n");
        Files.writeString(dataDirectory.resolve("messages_es.yml"), "test:\n  both: \"ambos\"\n");
        YamlLocaleService service = new YamlLocaleService(dataDirectory, "de");
        assertEquals(List.of("de", "en", "es"), List.copyOf(service.languages()));
    }

    @Test
    void bundledLanguagesNameThemselves() {
        YamlLocaleService service = new YamlLocaleService(dataDirectory, "de");
        assertEquals("Deutsch", service.raw(YamlLocaleService.LANGUAGE_NAME, Locale.GERMAN));
        assertEquals("English", service.raw(YamlLocaleService.LANGUAGE_NAME, Locale.ENGLISH));
    }
}
