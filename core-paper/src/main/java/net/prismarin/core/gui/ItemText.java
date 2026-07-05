package net.prismarin.core.gui;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class ItemText {

    private ItemText() {
        throw new AssertionError();
    }

    public static Component sanitize(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static Component name(MiniMessage miniMessage, String raw) {
        return sanitize(miniMessage.deserialize(raw));
    }

    public static List<Component> lore(MiniMessage miniMessage, List<String> raws) {
        return raws.stream().map(raw -> name(miniMessage, raw)).toList();
    }
}
