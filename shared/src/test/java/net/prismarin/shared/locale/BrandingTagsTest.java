package net.prismarin.shared.locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.prismarin.api.brand.Branding;
import org.junit.jupiter.api.Test;

class BrandingTagsTest {

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static TextColor colorOf(Component component, String text) {
        if (component instanceof TextComponent t && t.content().contains(text)) {
            return t.color();
        }
        for (Component child : component.children()) {
            TextColor found = colorOf(child, text);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Test
    void successAndErrorFollowTheNormalPrefixInTheirColour() {
        var formatter = BrandingTags.formatter(Branding.DEFAULT);
        Component ok = formatter.format("%success_prefix%Done.</gray>");
        Component bad = formatter.format("%error_prefix%Nope.</gray>");
        assertEquals("PRISMARIN | Done.", plain(ok));
        assertEquals("PRISMARIN | Nope.", plain(bad));
        assertEquals(TextColor.fromHexString("#86EFAC"), colorOf(ok, "Done"));
        assertEquals(TextColor.fromHexString("#FCA5A5"), colorOf(bad, "Nope"));
    }

    @Test
    void aCustomPrefixIsReusedAndCustomColoursWin() {
        Branding branding = new Branding("NETZ", "#112233", "#445566", "&8[&bNetz&8] &7", "%brand_prefix%&a", "");
        var formatter = BrandingTags.formatter(branding);
        assertEquals("[Netz] Saved", plain(formatter.format("%success_prefix%Saved")));
        assertEquals(TextColor.fromHexString("#55FF55"), colorOf(formatter.format("%success_prefix%Saved"), "Saved"));
        assertEquals(Branding.DEFAULT_ERROR_PREFIX, branding.errorPrefix());
        assertEquals(Branding.DEFAULT_SUCCESS_PREFIX, new Branding("X", "#000000", "#000000", "").successPrefix());
    }
}
