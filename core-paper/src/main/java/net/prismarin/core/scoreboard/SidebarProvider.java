package net.prismarin.core.scoreboard;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public interface SidebarProvider {

    Component title(Player player);

    List<Component> lines(Player player);
}
