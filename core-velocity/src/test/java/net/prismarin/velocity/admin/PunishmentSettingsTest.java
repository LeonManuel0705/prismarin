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

    @Test
    void templatesCanBeReorderedRemovedAndExtended() throws IOException {
        Path file = directory.resolve(PunishmentSettings.FILE_NAME);
        Files.writeString(file, String.join("\n",
                "templates:",
                "  teaming:",
                "    name: {de: Teaming, en: Teaming}",
                "    type: ban",
                "    steps: [warn, \"mute 1h\", 1d, 3d, 7d, 30d, permanent]",
                "  hacking:",
                "    name: {en: Hacking}",
                "    description: {de: \"Nur mit Beweis\", en: \"Only with proof\"}",
                "    type: ban",
                "    steps: [permanent]",
                "  caps:",
                "    name: Caps",
                "    description: \"Too many capitals\"",
                "    type: mute",
                "    steps: [warn, 10m]",
                ""));
        PunishmentSettings settings = PunishmentSettings.load(file);
        assertEquals(List.of(), settings.problems());
        assertEquals(List.of("teaming", "hacking", "caps"),
                settings.templates().stream().map(PunishmentTemplate::id).toList());
        PunishmentTemplate teaming = settings.templates().getFirst();
        assertEquals(7, teaming.steps().size());
        assertEquals(PunishmentKind.WARN, teaming.step(0).kind());
        assertEquals(PunishmentKind.TEMPMUTE, teaming.step(1).kind());
        assertEquals(PunishmentKind.BAN, teaming.step(99).kind());
        assertEquals("Nur mit Beweis", settings.template("hacking").orElseThrow()
                .description(java.util.Locale.GERMAN));
        assertEquals("Too many capitals", settings.template("caps").orElseThrow()
                .description(java.util.Locale.GERMAN));
        assertEquals("", teaming.description(java.util.Locale.ENGLISH));
        String text = Files.readString(file);
        assertTrue(text.contains("# description: optional short note"), text);
        assertFalse(text.contains("evasion"), text);
    }

    @Test
    void anEmptyTemplateSectionStaysEmpty() throws IOException {
        Path file = directory.resolve(PunishmentSettings.FILE_NAME);
        Files.writeString(file, "templates: {}\n");
        assertEquals(List.of(), PunishmentSettings.load(file).templates());
        assertEquals(List.of(), PunishmentSettings.load(file).templates());
    }

    @Test
    void outdatedTemplateCommentsAreReplaced() throws IOException {
        Path file = directory.resolve(PunishmentSettings.FILE_NAME);
        PunishmentSettings.load(file);
        String fresh = Files.readString(file);
        String old = fresh.replace("# Every key below is one template id.", "# steps: one entry per offence.");
        Files.writeString(file, old);
        PunishmentSettings.load(file);
        assertEquals(fresh, Files.readString(file));
    }
}
