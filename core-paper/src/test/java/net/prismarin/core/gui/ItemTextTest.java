package net.prismarin.core.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class ItemTextTest {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final PlainTextComponentSerializer plainText = PlainTextComponentSerializer.plainText();

    @Test
    void nameRendersPlainContent() {
        Component name = ItemText.name(miniMessage, "<color:#22D3EE>Kompass</color>");
        assertEquals("Kompass", plainText.serialize(name));
    }

    @Test
    void nameKeepsMiniMessageColor() {
        Component name = ItemText.name(miniMessage, "<color:#22D3EE>Kompass</color>");
        assertEquals(TextColor.fromHexString("#22D3EE"), name.color());
    }

    @Test
    void nameDisablesItalicByDefault() {
        Component name = ItemText.name(miniMessage, "<color:#22D3EE>Kompass</color>");
        assertEquals(TextDecoration.State.FALSE, name.decoration(TextDecoration.ITALIC));
    }

    @Test
    void nameKeepsExplicitItalic() {
        Component name = ItemText.name(miniMessage, "<i>Kursiv</i>");
        assertEquals(TextDecoration.State.TRUE, name.decoration(TextDecoration.ITALIC));
    }

    @Test
    void loreRendersEveryLineInOrder() {
        List<Component> lore = ItemText.lore(miniMessage, List.of("<gray>Zeile eins</gray>", "<gray>Zeile zwei</gray>"));
        assertEquals(2, lore.size());
        assertEquals("Zeile eins", plainText.serialize(lore.get(0)));
        assertEquals("Zeile zwei", plainText.serialize(lore.get(1)));
    }

    @Test
    void loreLinesAreNotItalic() {
        List<Component> lore = ItemText.lore(miniMessage, List.of("<gray>Zeile</gray>"));
        assertEquals(TextDecoration.State.FALSE, lore.get(0).decoration(TextDecoration.ITALIC));
    }

    @Test
    void sanitizeLeavesExistingDecorationUntouched() {
        Component decorated = Component.text("Test").decoration(TextDecoration.ITALIC, true);
        assertEquals(TextDecoration.State.TRUE, ItemText.sanitize(decorated).decoration(TextDecoration.ITALIC));
    }
}
