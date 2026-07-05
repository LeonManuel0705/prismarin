package net.prismarin.core.gui;

import java.util.Map;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class MenuHolder implements InventoryHolder {

    private final Menu menu;
    private final Map<Integer, MenuButton> buttons;
    private Inventory inventory;

    MenuHolder(Menu menu, Map<Integer, MenuButton> buttons) {
        this.menu = menu;
        this.buttons = Map.copyOf(buttons);
    }

    Menu menu() {
        return menu;
    }

    Map<Integer, MenuButton> buttons() {
        return buttons;
    }

    MenuButton buttonAt(int slot) {
        return buttons.get(slot);
    }

    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
