package net.prismarin.shared.locale;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private YamlLocaleService serviceWithCustomFiles() throws IOException {
        Files.writeString(dataDirectory.resolve("messages_de.yml"), """
                test:
                  only-de: "nur deutsch"
                  both: "beide deutsch"
                  styled: "<gray>hallo</gray>"
                """);
        Files.writeString(dataDirectory.resolve("messages_en.yml"), """
                test:
                  both: "both english"
                  styled: "<gray>hello</gray>"
                """);
        return new YamlLocaleService(dataDirectory);
    }

    @Test
    void resolvesKeyInRequestedLocale() throws IOException {
        YamlLocaleService service = serviceWithCustomFiles();
        assertEquals("both english", service.raw("test.both", Locale.ENGLISH));
        assertEquals("beide deutsch", service.raw("test.both", Locale.GERMAN));
    }

    @Test
    void fallsBackToGermanWhenKeyMissingInLocale() throws IOException {
        YamlLocaleService service = serviceWithCustomFiles();
        assertEquals("nur deutsch", service.raw("test.only-de", Locale.ENGLISH));
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
        YamlLocaleService service = new YamlLocaleService(dataDirectory);
        assertTrue(Files.exists(dataDirectory.resolve("messages_de.yml")));
        assertTrue(Files.exists(dataDirectory.resolve("messages_en.yml")));
        assertNotEquals("prefix", service.raw("prefix", Locale.GERMAN));
        assertNotEquals("economy.insufficient-funds", service.raw("economy.insufficient-funds", Locale.ENGLISH));
    }
}
