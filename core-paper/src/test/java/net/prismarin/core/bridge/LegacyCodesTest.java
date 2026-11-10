package net.prismarin.core.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

class LegacyCodesTest {

    @Test
    void namedColorsUseTheirLegacyDigit() {
        assertEquals("§c", LegacyCodes.color(NamedTextColor.RED));
        assertEquals("§0", LegacyCodes.color(NamedTextColor.BLACK));
        assertEquals("§f", LegacyCodes.color(NamedTextColor.WHITE));
        assertEquals("§c", LegacyCodes.color(TextColor.color(0xFF5555)));
    }

    @Test
    void hexColorsUseTheLongForm() {
        assertEquals("§x§1§2§3§4§5§6", LegacyCodes.color(TextColor.color(0x123456)));
        assertEquals("", LegacyCodes.color(null));
    }
}
