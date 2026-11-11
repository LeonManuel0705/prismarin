package net.prismarin.core.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class DisplayPartsTest {

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void singlePrefixMatchesTheOldFormat() {
        Component rank = Component.text("[Admin]");

        assertEquals(rank.append(Component.space()), DisplayParts.prefix(List.of(rank)));
        assertEquals(Component.space().append(rank), DisplayParts.suffix(List.of(rank)));
    }

    @Test
    void invisiblePartsAreDropped() {
        assertEquals(Component.empty(), DisplayParts.prefix(List.of(Component.empty(), Component.text(" "))));
        assertEquals(Component.empty(), DisplayParts.suffix(Arrays.asList(null, Component.empty())));
    }

    @Test
    void severalPartsAreJoinedWithSpaces() {
        Component prefix = DisplayParts.prefix(List.of(Component.text("[Red]"), Component.empty(),
                Component.text("[Admin]")));
        Component suffix = DisplayParts.suffix(List.of(Component.empty(), Component.text("AFK"),
                Component.text("[Clan]")));

        assertEquals("[Red] [Admin] ", plain(prefix));
        assertEquals(" AFK [Clan]", plain(suffix));
    }

    @Test
    void linesAppendBelowTheBase() {
        Component base = Component.text("Header");

        assertEquals(base, DisplayParts.lines(base, List.of()));
        assertEquals("Header\nOne\nTwo", plain(DisplayParts.lines(base, List.of(Component.text("One"),
                Component.text("Two")))));
        assertEquals("One", plain(DisplayParts.lines(Component.empty(), List.of(Component.text("One")))));
    }

    @Test
    void placeholdersKeepTheFirstValueAndSkipReservedOrInvalidKeys() {
        Map<String, Component> merged = DisplayParts.placeholders(Arrays.asList(
                Map.of("team", Component.text("Red"), "name", Component.text("Fake")),
                null,
                Map.of("team", Component.text("Blue"), "Level", Component.text("x"), "level", Component.text("12"))),
                Set.of("prefix", "name", "suffix", "message"));

        assertEquals(Set.of("team", "level"), merged.keySet());
        assertEquals("Red", plain(merged.get("team")));
        assertEquals("12", plain(merged.get("level")));
    }

    @Test
    void placeholderKeysFollowMiniMessageRules() {
        assertTrue(DisplayParts.validPlaceholder("bedwars_wins"));
        assertTrue(DisplayParts.validPlaceholder("clan-tag"));
        assertFalse(DisplayParts.validPlaceholder("Clan"));
        assertFalse(DisplayParts.validPlaceholder("with space"));
        assertFalse(DisplayParts.validPlaceholder(""));
        assertFalse(DisplayParts.validPlaceholder(null));
    }
}
