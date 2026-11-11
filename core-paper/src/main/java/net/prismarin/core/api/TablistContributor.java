package net.prismarin.core.api;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public interface TablistContributor {

    default List<Component> header(Player viewer) {
        return List.of();
    }

    default List<Component> footer(Player viewer) {
        return List.of();
    }

    default Component prefix(Player viewer, Player target) {
        return Component.empty();
    }

    default Component suffix(Player viewer, Player target) {
        return Component.empty();
    }
}
