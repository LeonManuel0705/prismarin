package net.prismarin.velocity.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PunishmentSettingsTest {

    @TempDir
    Path directory;

    @Test
    void writesCommentedDefaultsOnFirstStart() throws IOException {
        Path file = directory.resolve(PunishmentSettings.FILE_NAME);
        PunishmentSettings settings = PunishmentSettings.load(file);
        assertEquals(List.of(), settings.problems());
        assertEquals(PunishmentSettings.DEFAULT_TEMPLATES.size(), settings.templates().size());
        PunishmentTemplate hacking = settings.template("Hacking").orElseThrow();
        assertEquals(List.of(PunishmentKind.TEMPBAN, PunishmentKind.TEMPBAN, PunishmentKind.BAN),
                hacking.steps().stream().map(PunishmentTemplate.Step::kind).toList());
        assertEquals("Hacking oder Cheats", hacking.name(java.util.Locale.GERMAN));
        assertNull(hacking.forgetAfter());
        assertEquals(Duration.ofDays(180), settings.template("chat").orElseThrow().forgetAfter());
        assertEquals(PunishmentKind.WARN, settings.template("spam").orElseThrow().step(0).kind());
        assertTrue(settings.template("evasion").orElseThrow().restricted());
        assertTrue(settings.ipBans());
        assertFalse(settings.showActor());
        assertFalse(settings.countLifted());
        assertFalse(settings.broadcast().covers(PunishmentKind.BAN));
        assertEquals(Set.of(PunishmentKind.BAN, PunishmentKind.TEMPBAN, PunishmentKind.IPBAN),
                settings.broadcast().kinds());
        assertEquals(ZoneId.systemDefault(), settings.zone());
        String text = Files.readString(file);
        assertTrue(text.startsWith("# PRISMARIN punishments"));
        assertTrue(text.contains("# forget-after:"));
        assertEquals(text, reload(file));
    }

    private static String reload(Path file) throws IOException {
        PunishmentSettings.load(file);
        return Files.readString(file);
    }

    @Test
    void reportsBrokenEntriesAndKeepsTheRest() throws IOException {
        Path file = directory.resolve(PunishmentSettings.FILE_NAME);
        Files.writeString(file, String.join("\n",
                "appeal-url: \" https://example.net/appeal \"",
                "time-zone: Mars/Base",
                "public-broadcast:",
                "  enabled: true",
                "  kinds: [tempban, nonsense]",
                "templates:",
                "  jail:",
                "    type: jail",
                "    steps: [1d]",
                "  mixed:",
                "    name: Mixed",
                "    type: mute",
                "    steps: [\"1h\", \"tempban\", \"ban 1d\"]",
                "    forget-after: later",
                "  empty:",
                "    type: ban",
                "    steps: []",
                ""));
        PunishmentSettings settings = PunishmentSettings.load(file);
        assertEquals("https://example.net/appeal", settings.appealUrl());
        assertEquals(List.of("mixed"), settings.templates().stream().map(PunishmentTemplate::id).toList());
        PunishmentTemplate mixed = settings.templates().getFirst();
        assertEquals(List.of(PunishmentKind.TEMPMUTE, PunishmentKind.TEMPBAN),
                mixed.steps().stream().map(PunishmentTemplate.Step::kind).toList());
        assertEquals("Mixed", mixed.name(java.util.Locale.GERMAN));
        assertNull(mixed.forgetAfter());
        assertTrue(settings.broadcast().covers(PunishmentKind.TEMPBAN));
        assertFalse(settings.broadcast().covers(PunishmentKind.BAN));
        assertEquals(6, settings.problems().size(), settings.problems().toString());
    }
}
