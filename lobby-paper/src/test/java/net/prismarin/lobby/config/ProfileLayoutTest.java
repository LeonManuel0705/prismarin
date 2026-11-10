package net.prismarin.lobby.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.prismarin.lobby.config.ProfileLayout.Line;
import org.junit.jupiter.api.Test;

class ProfileLayoutTest {

    @Test
    void parsesHeadLines() {
        assertEquals(new Line(Line.Kind.CURRENCIES, ""), Line.parse("currencies"));
        assertEquals(new Line(Line.Kind.CURRENCY, "gems"), Line.parse("currency: Gems"));
        assertEquals(new Line(Line.Kind.LEVEL, ""), Line.parse(" Level "));
        assertEquals(new Line(Line.Kind.RANK, ""), Line.parse("rank"));
        assertEquals(new Line(Line.Kind.EMPTY, ""), Line.parse(""));
        assertEquals(new Line(Line.Kind.EMPTY, ""), Line.parse("blank"));
        assertEquals(new Line(Line.Kind.TEXT, "&7Kills: %stat_kills%"), Line.parse("text: &7Kills: %stat_kills%"));
    }

    @Test
    void rejectsUnknownOrIncompleteHeadLines() {
        assertThrows(IllegalArgumentException.class, () -> Line.parse("money"));
        assertThrows(IllegalArgumentException.class, () -> Line.parse("currency:"));
        assertThrows(IllegalArgumentException.class, () -> Line.parse("text:   "));
        List<String> warnings = new ArrayList<>();
        assertEquals(List.of(new Line(Line.Kind.LEVEL, "")), ProfileLayout.parseHead(List.of("money", "level"),
                warnings));
        assertEquals(1, warnings.size());
    }

    @Test
    void readsEntryPlacements() {
        assertEquals(Optional.of(-1), ProfileLayout.placement("auto", 27));
        assertEquals(Optional.of(-1), ProfileLayout.placement("true", 27));
        assertEquals(Optional.empty(), ProfileLayout.placement("off", 27));
        assertEquals(Optional.empty(), ProfileLayout.placement("false", 27));
        assertEquals(Optional.empty(), ProfileLayout.placement(null, 27));
        assertEquals(Optional.of(13), ProfileLayout.placement(" 13 ", 27));
        assertThrows(IllegalArgumentException.class, () -> ProfileLayout.placement("27", 27));
        assertThrows(IllegalArgumentException.class, () -> ProfileLayout.placement("middle", 27));
    }

    @Test
    void defaultLayoutSpreadsTheProfileOverFiveRows() {
        ProfileLayout layout = ProfileLayout.DEFAULT;
        assertEquals(5, layout.rows());
        assertEquals(13, layout.headSlot());
        assertEquals(2, layout.entryRow());
        assertEquals(3, layout.actionRow());
        assertEquals(36, layout.backSlot());
        assertEquals(44, layout.closeSlot());
        assertEquals(ProfileLayout.LanguageClick.MENU, layout.languageClick());
        assertTrue(ProfileLayout.classicDefaults(3, 4, 1, 18, 26));
        assertFalse(ProfileLayout.classicDefaults(5, 13, 2, 36, 44));
        assertEquals(List.of(ProfileLayout.Entry.Kind.CURRENCIES, ProfileLayout.Entry.Kind.LEVEL,
                ProfileLayout.Entry.Kind.LANGUAGE), layout.entries().stream().map(ProfileLayout.Entry::kind).toList());
        assertEquals(List.of(Line.Kind.CURRENCIES, Line.Kind.LEVEL), layout.head().stream().map(Line::kind).toList());
    }
}
