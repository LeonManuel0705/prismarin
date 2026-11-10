package net.prismarin.core.rank;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

public interface RankFormatProvider {

    Component prefix(Player player);

    int weight(Player player);

    NamedTextColor nameColor(Player player);

    default Component ownPrefix(Player player) {
        return prefix(player);
    }

    default Component suffix(Player player) {
        return Component.empty();
    }

    default TextColor chatColor(Player player) {
        return null;
    }
}
