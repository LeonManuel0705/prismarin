package net.prismarin.core.player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.prismarin.api.player.RankDisplay;

public final class RankDisplays {

    private RankDisplays() {
        throw new AssertionError();
    }

    public static RankDisplay of(Component prefix, Component suffix, TextColor nameColor, TextColor chatColor,
                                 int weight) {
        return new RankDisplay(label(prefix), prefix, suffix, nameColor, chatColor, weight);
    }

    public static String label(Component prefix) {
        if (prefix == null) {
            return "";
        }
        return PlainTextComponentSerializer.plainText().serialize(prefix).strip();
    }
}
