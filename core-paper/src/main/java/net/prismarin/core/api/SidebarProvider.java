package net.prismarin.core.api;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public interface SidebarProvider {

    Component title(Player player);

    List<Component> lines(Player player);

    default boolean appliesTo(Player player) {
        return true;
    }
}
