package net.prismarin.velocity.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdminSettingsTest {

    @TempDir
    Path directory;

    @Test
    void firstStartWritesColouredReasonsAndTheClaimTimeout() throws IOException {
        Path file = directory.resolve("admin.yml");
        AdminSettings settings = AdminSettings.load(file);
        assertEquals(NamedTextColor.RED, settings.reportReasons().getFirst().color());
        assertEquals(TextColor.color(0xFF8C00), settings.reportReasons().stream()
                .filter(reason -> reason.id().equals("bugusing")).findFirst().orElseThrow().color());
        assertEquals(10, settings.reportBehaviour().claimMinutes());
        assertEquals(600_000L, settings.reportBehaviour().claimMillis());
        assertEquals(List.of(), settings.menuActions());
        String text = Files.readString(file);
        assertTrue(text.contains("claim-minutes: 10"), text);
        assertTrue(text.contains("menu-actions: {}"), text);
        assertTrue(text.contains("# color: a colour name"), text);
        AdminSettings.load(file);
        assertEquals(text, Files.readString(file));
    }

    @Test
    void existingFilesGetColoursForTheBundledReasonsOnly() throws IOException {
        Path file = directory.resolve("admin.yml");
        Files.writeString(file, String.join("\n",
                "reports:",
                "  reasons:",
                "    hacking:",
                "      icon: DIAMOND_SWORD",
                "      name:",
                "        de: \"&lHacking\"",
                "        en: \"<bold>Hacking</bold>\"",
                "    chat:",
                "      icon: WRITABLE_BOOK",
                "      color: \"#123456\"",
                "      name:",
                "        en: Chat",
                "    xray:",
                "      icon: DIAMOND_ORE",
                "      name:",
                "        en: X-ray",
                ""));
        AdminSettings settings = AdminSettings.load(file);
        assertEquals(List.of("hacking", "chat", "xray"), settings.reportReasons().stream()
                .map(ReportReason::id).toList());
        assertEquals(NamedTextColor.RED, settings.reportReasons().get(0).color());
        assertEquals(TextColor.color(0x123456), settings.reportReasons().get(1).color());
        assertNull(settings.reportReasons().get(2).color());
        assertEquals("Hacking", settings.reportReasons().getFirst().plain(Locale.GERMAN));
        String text = Files.readString(file);
        assertTrue(text.contains("color: red"), text);
        assertTrue(text.contains("color: '#123456'") || text.contains("color: \"#123456\""), text);
    }

    @Test
    void menuActionsComeFromTheFile() throws IOException {
        Path file = directory.resolve("admin.yml");
        Files.writeString(file, String.join("\n",
                "reports:",
                "  claim-minutes: 0",
                "  menu-actions:",
                "    spectate:",
                "      icon: ENDER_EYE",
                "      name:",
                "        de: Zuschauen",
                "        en: Spectate",
                "      command: \"/spectate %player%\"",
                "      permission: prismarin.staff.spectate",
                "    broken:",
                "      icon: STONE",
                "    note:",
                "      command: \"note %id%\"",
                "      console: true",
                "      close: false",
                ""));
        AdminSettings settings = AdminSettings.load(file);
        assertEquals(0, settings.reportBehaviour().claimMinutes());
        List<AdminSettings.MenuAction> actions = settings.menuActions();
        assertEquals(List.of("spectate", "note"), actions.stream().map(AdminSettings.MenuAction::id).toList());
        assertEquals("spectate %player%", actions.getFirst().command());
        assertEquals("Zuschauen", actions.getFirst().names().get("de"));
        assertEquals("prismarin.staff.spectate", actions.getFirst().permission());
        assertTrue(actions.getFirst().close());
        assertTrue(actions.get(1).console());
        assertEquals(false, actions.get(1).close());
    }
}
