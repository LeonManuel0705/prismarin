package net.prismarin.velocity.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

class TextColorsTest {

    @Test
    void readsNamesHexValuesAndCodes() {
        assertEquals(NamedTextColor.RED, TextColors.parse("red").orElseThrow());
        assertEquals(NamedTextColor.LIGHT_PURPLE, TextColors.parse(" Light Purple ").orElseThrow());
        assertEquals(NamedTextColor.LIGHT_PURPLE, TextColors.parse("light-purple").orElseThrow());
        assertEquals(NamedTextColor.GRAY, TextColors.parse("grey").orElseThrow());
        assertEquals(NamedTextColor.GOLD, TextColors.parse("&6").orElseThrow());
        assertEquals(NamedTextColor.AQUA, TextColors.parse("§b").orElseThrow());
        assertEquals(TextColor.color(0xFF8C00), TextColors.parse("orange").orElseThrow());
        assertEquals(TextColor.color(0x22D3EE), TextColors.parse("#22d3ee").orElseThrow());
        assertEquals(TextColor.color(0x22D3EE), TextColors.parse("&#22D3EE").orElseThrow());
        assertEquals(TextColor.color(0x22D3EE), TextColors.parse("<#22D3EE>").orElseThrow());
        assertEquals(TextColor.color(0xFFAA00), TextColors.parse("#FA0").orElseThrow());
        assertEquals(NamedTextColor.YELLOW, TextColors.parse("<color:yellow>").orElseThrow());
        assertEquals(NamedTextColor.YELLOW, TextColors.parse("<yellow>").orElseThrow());
    }

    @Test
    void ignoresEverythingElse() {
        assertTrue(TextColors.parse(null).isEmpty());
        assertTrue(TextColors.parse("").isEmpty());
        assertTrue(TextColors.parse("   ").isEmpty());
        assertTrue(TextColors.parse("&l").isEmpty());
        assertTrue(TextColors.parse("&z").isEmpty());
        assertTrue(TextColors.parse("#12345").isEmpty());
        assertTrue(TextColors.parse("#GGGGGG").isEmpty());
        assertTrue(TextColors.parse("rainbow").isEmpty());
        assertTrue(TextColors.parse("<bold>").isEmpty());
    }
}
