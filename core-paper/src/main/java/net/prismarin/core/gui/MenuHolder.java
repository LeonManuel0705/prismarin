package net.prismarin.core.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class MenuHolder implements InventoryHolder {

    private final Menu menu;
    private Inventory inventory;

    MenuHolder(Menu menu) {
        this.menu = menu;
    }

    Menu menu() {
        return menu;
    }

    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
