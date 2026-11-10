package net.prismarin.api.player;

import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public record RankDisplay(String label, Component prefix, Component suffix, TextColor nameColor, TextColor chatColor,
                          int weight) {

    public static final RankDisplay NONE = new RankDisplay("", Component.empty(), Component.empty(),
            NamedTextColor.WHITE, null, 0);

    public RankDisplay {
        label = label == null ? "" : label.trim();
        prefix = prefix == null ? Component.empty() : prefix;
        suffix = suffix == null ? Component.empty() : suffix;
        nameColor = nameColor == null ? NamedTextColor.WHITE : nameColor;
    }

    public boolean known() {
        return !equals(NONE);
    }

    public TextColor chatColorOr(TextColor fallback) {
        return chatColor == null ? fallback : chatColor;
    }

    public Component name(String name) {
        return Component.text(Objects.requireNonNull(name, "name"), nameColor);
    }
}
