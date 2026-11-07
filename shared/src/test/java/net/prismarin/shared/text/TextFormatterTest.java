package net.prismarin.shared.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.prismarin.api.brand.Branding;
import net.prismarin.shared.locale.BrandingTags;
import org.junit.jupiter.api.Test;

class TextFormatterTest {

    private final TextFormatter formatter = BrandingTags.formatter(
            new Branding("NETZ", "#112233", "#445566", "%brand% | "));

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static TextColor colorOf(Component component) {
        return component.color() != null || component.children().isEmpty()
                ? component.color()
                : component.children().getFirst().color();
    }

    @Test
    void percentPlaceholdersAreResolved() {
        Component result = formatter.format("Hallo %player%!", Placeholder.unparsed("player", "Leon"));
        assertEquals("Hallo Leon!", plain(result));
    }

    @Test
    void angleBracketPlaceholdersStillWork() {
        Component result = formatter.format("Hallo <player>!", Placeholder.unparsed("player", "Leon"));
        assertEquals("Hallo Leon!", plain(result));
    }

    @Test
    void unknownPercentPlaceholdersStayLiteral() {
        assertEquals("Kills: %bedwars_kills% und 50%", plain(formatter.format("Kills: %bedwars_kills% und 50%")));
    }

    @Test
    void brandAndPrefixExpand() {
        assertEquals("NETZ | Hallo", plain(formatter.format("%brand_prefix%Hallo")));
        assertEquals("NETZ", plain(formatter.format("%brand%")));
    }

    @Test
    void brandColorsAreAvailableAsTags() {
        Component result = formatter.format("<brand_primary>x</brand_primary>");
        assertEquals(TextColor.fromHexString("#112233"), colorOf(result));
    }

    @Test
    void legacyColorsAreRendered() {
        Component result = formatter.format("&cRot");
        assertEquals(NamedTextColor.RED, colorOf(result));
    }

    @Test
    void percentConversionOnlyTouchesKnownNames() {
        TagResolver known = Placeholder.unparsed("coins", "5");
        assertEquals("<coins> %gems%", TextFormatter.percentToTags("%coins% %gems%", known));
    }
}
