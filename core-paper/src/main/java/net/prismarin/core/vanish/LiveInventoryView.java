package net.prismarin.core.vanish;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

public final class LiveInventoryView implements InventoryHolder {

    static final int SIZE = 54;
    static final int HELMET = 0;
    static final int CHESTPLATE = 1;
    static final int LEGGINGS = 2;
    static final int BOOTS = 3;
    static final int OFFHAND = 5;
    static final int INFO = 8;
    static final int STORAGE_START = 9;
    static final int SEPARATOR_START = 36;
    static final int HOTBAR_START = 45;

    private final UUID target;
    private final Inventory inventory;

    LiveInventoryView(UUID target, Component title) {
        this.target = target;
        this.inventory = Bukkit.createInventory(this, SIZE, title);
    }

    public UUID target() {
        return target;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void refresh(Player owner, ItemStack info) {
        PlayerInventory source = owner.getInventory();
        ItemStack pane = pane();
        inventory.setItem(HELMET, source.getHelmet());
        inventory.setItem(CHESTPLATE, source.getChestplate());
        inventory.setItem(LEGGINGS, source.getLeggings());
        inventory.setItem(BOOTS, source.getBoots());
        inventory.setItem(4, pane);
        inventory.setItem(OFFHAND, source.getItemInOffHand());
        inventory.setItem(6, pane);
        inventory.setItem(7, pane);
        inventory.setItem(INFO, info);
        for (int slot = 9; slot < 36; slot++) {
            inventory.setItem(STORAGE_START + slot - 9, source.getItem(slot));
        }
        for (int slot = SEPARATOR_START; slot < HOTBAR_START; slot++) {
            inventory.setItem(slot, slot - SEPARATOR_START == source.getHeldItemSlot() ? selected() : pane);
        }
        for (int slot = 0; slot < 9; slot++) {
            inventory.setItem(HOTBAR_START + slot, source.getItem(slot));
        }
    }


    private static ItemStack pane() {
        return named(new ItemStack(Material.BLACK_STAINED_GLASS_PANE));
    }

    private static ItemStack selected() {
        return named(new ItemStack(Material.CYAN_STAINED_GLASS_PANE));
    }

    private static ItemStack named(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.space());
        item.setItemMeta(meta);
        return item;
    }
}
