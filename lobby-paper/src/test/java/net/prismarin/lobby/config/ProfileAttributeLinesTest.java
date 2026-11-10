package net.prismarin.lobby.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProfileAttributeLinesTest {

    @Test
    void attributeLinesAreParsed() {
        assertEquals(new ProfileLayout.Line(ProfileLayout.Line.Kind.ATTRIBUTE, "bedwars_wins"),
                ProfileLayout.Line.parse("attribute: BedWars_Wins"));
        assertEquals(new ProfileLayout.Line(ProfileLayout.Line.Kind.ATTRIBUTES, ""),
                ProfileLayout.Line.parse("attributes"));
        assertThrows(IllegalArgumentException.class, () -> ProfileLayout.Line.parse("attribute"));
    }

    @Test
    void placedAndListedAttributesAreFound() {
        List<String> warnings = new ArrayList<>();
        List<ProfileLayout.Line> head = ProfileLayout.parseHead(
                List.of("currencies", "attribute: kills", "attributes", "attribute: wins"), warnings);
        assertTrue(warnings.isEmpty());
        assertEquals(Set.of("kills", "wins"), ProfileLayout.placedAttributes(head));
        assertTrue(ProfileLayout.listsAttributes(head));
        assertFalse(ProfileLayout.listsAttributes(ProfileLayout.DEFAULT.head()));
        assertTrue(ProfileLayout.placedAttributes(ProfileLayout.DEFAULT.head()).isEmpty());
    }
}
