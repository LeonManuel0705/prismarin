package net.prismarin.core.vanish;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class SilentContainer implements InventoryHolder {

    private final Location location;
    private final ItemStack[] snapshot;
    private final Inventory inventory;

    SilentContainer(Location location, Inventory source, Component title) {
        this.location = location;
        this.snapshot = copy(source.getContents());
        this.inventory = Bukkit.createInventory(this, source.getSize(), title);
        this.inventory.setContents(copy(source.getContents()));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    Location location() {
        return location;
    }

    boolean unchanged(Inventory current) {
        ItemStack[] contents = current.getContents();
        if (contents.length != snapshot.length) {
            return false;
        }
        for (int i = 0; i < contents.length; i++) {
            ItemStack before = snapshot[i];
            ItemStack now = contents[i];
            if (before == null ? now != null && !now.isEmpty() : !before.equals(now)) {
                return false;
            }
        }
        return true;
    }

    private static ItemStack[] copy(ItemStack[] items) {
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            copy[i] = items[i] == null ? null : items[i].clone();
        }
        return copy;
    }
}
