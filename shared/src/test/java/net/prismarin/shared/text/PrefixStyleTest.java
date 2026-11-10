package net.prismarin.shared.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.Test;

class PrefixStyleTest {

    private static Component legacy(String raw) {
        return MiniMessage.miniMessage().deserialize(LegacyColors.toMiniMessage(raw));
    }

    @Test
    void aColourOnlyPrefixIsInvisibleButColoursTheName() {
        assertFalse(PrefixStyle.visible(legacy("&c")));
        assertEquals(Optional.of(NamedTextColor.RED), PrefixStyle.lastColor("&c"));
        assertEquals(Optional.of(NamedTextColor.GOLD), PrefixStyle.lastColor("&6"));
    }

    @Test
    void theLastColourOfAVisiblePrefixWins() {
        assertTrue(PrefixStyle.visible(legacy("&6[VIP] &e")));
        assertEquals(Optional.of(NamedTextColor.YELLOW), PrefixStyle.lastColor("&6[VIP] &e"));
        assertEquals(Optional.of(NamedTextColor.RED), PrefixStyle.lastColor("<red>Admin</red>"));
    }

    @Test
    void parsesNamesCodesAndHex() {
        assertEquals(Optional.of(NamedTextColor.GOLD), PrefixStyle.parse("gold"));
        assertEquals(Optional.of(NamedTextColor.DARK_RED), PrefixStyle.parse("&4"));
        assertEquals(Optional.of(TextColor.color(0x22D3EE)), PrefixStyle.parse("&#22D3EE"));
        assertEquals(Optional.empty(), PrefixStyle.parse(""));
        assertEquals(NamedTextColor.AQUA, PrefixStyle.nearest(TextColor.color(0x55FFFF)));
    }
}
