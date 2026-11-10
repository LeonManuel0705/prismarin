package net.prismarin.velocity.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.prismarin.api.moderation.PunishmentType;
import org.junit.jupiter.api.Test;

class PunishmentTemplateTest {

    private static PunishmentTemplate.Step step(String text, PunishmentType fallback) {
        return PunishmentTemplate.parseStep(text, fallback).orElseThrow(() -> new AssertionError(text));
    }

    @Test
    void stepsTakeTheTemplateTypeWhenTheyOnlyNameADuration() {
        PunishmentTemplate.Step step = step("30d", PunishmentType.BAN);
        assertEquals(PunishmentType.BAN, step.type());
        assertEquals(Duration.ofDays(30), step.duration());
        assertEquals(PunishmentKind.TEMPBAN, step.kind());
        assertTrue(step("permanent", PunishmentType.MUTE).permanent());
        assertEquals(PunishmentKind.MUTE, step("perm", PunishmentType.MUTE).kind());
    }

    @Test
    void stepsCanNameTheirOwnType() {
        assertEquals(PunishmentKind.TEMPMUTE, step("mute 2h", PunishmentType.BAN).kind());
        assertEquals(PunishmentKind.BAN, step("ban", PunishmentType.MUTE).kind());
        assertEquals(PunishmentKind.BAN, step("BAN permanent", PunishmentType.MUTE).kind());
        assertEquals(PunishmentKind.TEMPBAN, step("tempban 7d", PunishmentType.MUTE).kind());
        assertEquals(PunishmentKind.WARN, step("warn", PunishmentType.MUTE).kind());
        assertNull(step("kick", PunishmentType.BAN).duration());
    }

    @Test
    void brokenStepsAreRejected() {
        assertEquals(Optional.empty(), PunishmentTemplate.parseStep("tempban", PunishmentType.BAN));
        assertEquals(Optional.empty(), PunishmentTemplate.parseStep("tempmute permanent", PunishmentType.BAN));
        assertEquals(Optional.empty(), PunishmentTemplate.parseStep("ipban 1d", PunishmentType.BAN));
        assertEquals(Optional.empty(), PunishmentTemplate.parseStep("kick 1d", PunishmentType.BAN));
        assertEquals(Optional.empty(), PunishmentTemplate.parseStep("ban 1d extra", PunishmentType.BAN));
        assertEquals(Optional.empty(), PunishmentTemplate.parseStep("soon", PunishmentType.BAN));
        assertEquals(Optional.empty(), PunishmentTemplate.parseStep("1d", PunishmentType.WARN));
        assertEquals(Optional.empty(), PunishmentTemplate.parseStep("", PunishmentType.BAN));
    }

    @Test
    void theLastStepRepeats() {
        PunishmentTemplate template = new PunishmentTemplate("Chat", Map.of("de", "Chat"), null, " ",
                List.of(step("1h", PunishmentType.MUTE), step("1d", PunishmentType.MUTE)), Duration.ZERO);
        assertEquals("chat", template.id());
        assertEquals("PAPER", template.icon());
        assertFalse(template.restricted());
        assertNull(template.forgetAfter());
        assertEquals(Duration.ofHours(1), template.step(0).duration());
        assertEquals(Duration.ofDays(1), template.step(1).duration());
        assertEquals(Duration.ofDays(1), template.step(7).duration());
        assertEquals(1, template.stepIndex(9));
    }

    @Test
    void namesFollowTheViewerLanguage() {
        PunishmentTemplate template = new PunishmentTemplate("hacking", Map.of("de", "Cheats", "en", "Cheating"),
                "DIAMOND_SWORD", "prismarin.punish.template.hacking", List.of(step("ban", null)), null);
        assertEquals("Cheats", template.name(Locale.GERMANY));
        assertEquals("Cheating", template.name(Locale.FRENCH));
        assertTrue(template.restricted());
        assertThrows(IllegalArgumentException.class, () -> new PunishmentTemplate("empty", Map.of(), "", "",
                List.of(), null));
    }
}
