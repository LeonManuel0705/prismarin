package net.prismarin.core.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

public record ClickContext(Player player, int slot, ClickType clickType, Menu menu, MenuService menus) {
}
