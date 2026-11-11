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

    private static final String SEPARATORS = "\u2503|\u00bb:\u2022";

    private static String plain(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void trailingSeparatorsAndSpacesAreTrimmedButColoursStay() {
        Component trimmed = PrefixStyle.trimTrailing(TextFormatter.plain().format("&cAdmin &8\u2503 "), SEPARATORS);
        assertEquals("Admin", plain(trimmed));
        assertEquals(Optional.of(NamedTextColor.RED), Optional.ofNullable(trimmed.children().getFirst().color()));
    }

    @Test
    void separatorsInsideTheRankAndGradientsSurvive() {
        assertEquals("A|B", plain(PrefixStyle.trimTrailing(TextFormatter.plain().format("&6A|B &8\u00bb"), SEPARATORS)));
        Component gradient = TextFormatter.plain().format("<gradient:#22D3EE:#2DD4BF>Team</gradient> <dark_gray>|</dark_gray> ");
        assertEquals("Team", plain(PrefixStyle.trimTrailing(gradient, SEPARATORS)));
    }

    @Test
    void aRankMadeOnlyOfSeparatorsIsLeftAlone() {
        Component only = TextFormatter.plain().format("&8\u2503");
        assertEquals("\u2503", plain(PrefixStyle.trimTrailing(only, SEPARATORS)));
        assertEquals("", plain(PrefixStyle.trimTrailing(Component.empty(), SEPARATORS)));
        assertEquals("", plain(PrefixStyle.trimTrailing(null, SEPARATORS)));
    }
}
