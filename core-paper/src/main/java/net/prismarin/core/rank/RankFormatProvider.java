package net.prismarin.core.rank;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public interface RankFormatProvider {

    Component prefix(Player player);

    int weight(Player player);

    NamedTextColor nameColor(Player player);
}
