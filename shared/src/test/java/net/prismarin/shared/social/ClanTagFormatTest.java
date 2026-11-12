package net.prismarin.shared.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.prismarin.shared.text.TextFormatter;
import org.junit.jupiter.api.Test;

class ClanTagFormatTest {

    private final TextFormatter formatter = TextFormatter.plain();

    @Test
    void namedColoursCodesAndHexAreAccepted() {
        assertEquals(Optional.of("red"), ClanColors.normalize("RED"));
        assertEquals(Optional.of("dark_aqua"), ClanColors.normalize("dark-aqua"));
        assertEquals(Optional.of("gold"), ClanColors.normalize("orange"));
        assertEquals(Optional.of("green"), ClanColors.normalize("grün"));
        assertEquals(Optional.of("red"), ClanColors.normalize("&c"));
        assertEquals(Optional.of("aqua"), ClanColors.normalize("§b"));
        assertEquals(Optional.of("#ff8800"), ClanColors.normalize("#FF8800"));
        assertEquals(Optional.of("#ff8800"), ClanColors.normalize("&#FF8800"));
        assertEquals(Optional.of("#ff8800"), ClanColors.normalize("ff8800"));
        assertEquals(Optional.of("light_purple"), ClanColors.normalize("<light_purple>"));
        assertEquals(Optional.of(ClanColors.DEFAULT), ClanColors.normalize("reset"));
        assertEquals(Optional.of(ClanColors.DEFAULT), ClanColors.normalize(" "));
    }

    @Test
    void formattingCodesAndNonsenseAreRejected() {
        for (String input : List.of("&l", "&k", "&r", "#12345", "#GGGGGG", "rainbow", "&#12", "<bold>")) {
            assertEquals(Optional.empty(), ClanColors.normalize(input), input);
        }
        assertEquals(Optional.empty(), ClanColors.normalize(null));
        assertEquals(Optional.of(NamedTextColor.RED), ClanColors.color("red"));
        assertEquals(Optional.of(TextColor.color(0xFF8800)), ClanColors.color("#ff8800"));
        assertEquals(Optional.empty(), ClanColors.color(""));
        assertTrue(ClanColors.suggestions().contains("gold"));
        assertTrue(ClanColors.suggestions().contains("reset"));
    }

    @Test
    void theDefaultFormatWrapsTheTagInGrayBrackets() {
        Component tag = ClanTagFormat.DEFAULT.render(formatter, "OWL", "Nightowls", "");
        assertEquals("[OWL]", plain(tag));
        assertEquals(TextColor.color(NamedTextColor.WHITE), colorOf(tag, "OWL"));
        assertEquals(TextColor.color(NamedTextColor.GRAY), colorOf(tag, "["));
    }

    @Test
    void theClanColourReplacesTheWhiteOfTheTag() {
        Component tag = ClanTagFormat.DEFAULT.render(formatter, "OWL", "Nightowls", "#ff8800");
        assertEquals("[OWL]", plain(tag));
        assertEquals(TextColor.color(0xFF8800), colorOf(tag, "OWL"));
    }

    @Test
    void ownFormatsMayShowTheFullNameAndPositionsParse() {
        ClanTagFormat format = new ClanTagFormat("&8(%name%&8) &7%clan%", ClanTagFormat.Position.BEFORE, true, false);
        assertEquals("(OWL) Nightowls", plain(format.render(formatter, "OWL", "Nightowls", "red")));
        assertTrue(format.before());
        assertEquals(Optional.of(ClanTagFormat.Position.AFTER), ClanTagFormat.Position.byId("Suffix"));
        assertEquals(Optional.of(ClanTagFormat.Position.BEFORE), ClanTagFormat.Position.byId("vor"));
        assertEquals(Optional.empty(), ClanTagFormat.Position.byId("middle"));
        assertEquals(ClanTagFormat.DEFAULT_FORMAT, new ClanTagFormat(" ", null, true, true).format());
        assertEquals(ClanTagFormat.Position.AFTER, new ClanTagFormat(null, null, true, true).position());
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static TextColor colorOf(Component root, String text) {
        return find(root, text, null);
    }

    private static TextColor find(Component component, String text, TextColor inherited) {
        TextColor own = component.color() != null ? component.color() : inherited;
        if (component instanceof TextComponent literal && literal.content().contains(text)) {
            return own;
        }
        for (Component child : component.children()) {
            TextColor found = find(child, text, own);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
