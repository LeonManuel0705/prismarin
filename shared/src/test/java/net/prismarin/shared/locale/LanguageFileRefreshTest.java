package net.prismarin.shared.locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LanguageFileRefreshTest {

    @TempDir
    Path directory;

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void anUntouchedFileFollowsTheBundledTexts() throws IOException {
        Path file = directory.resolve("messages_en.yml");
        assertTrue(YamlLocaleService.seed(file, bytes("a: old\n")));
        assertFalse(YamlLocaleService.seed(file, bytes("a: old\n")));
        assertTrue(YamlLocaleService.seed(file, bytes("a: new\n")));
        assertEquals("a: new\n", Files.readString(file));
    }

    @Test
    void anEditedFileIsNeverReplaced() throws IOException {
        Path file = directory.resolve("messages_en.yml");
        YamlLocaleService.seed(file, bytes("a: old\n"));
        Files.writeString(file, "a: mine\n");
        assertFalse(YamlLocaleService.seed(file, bytes("a: new\n")));
        assertEquals("a: mine\n", Files.readString(file));
    }

    @Test
    void aFileFromBeforeTheMarkerIsKept() throws IOException {
        Path file = directory.resolve("messages_en.yml");
        Files.writeString(file, "a: legacy\n");
        assertFalse(YamlLocaleService.seed(file, bytes("a: new\n")));
        assertEquals("a: legacy\n", Files.readString(file));
    }
}
