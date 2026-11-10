package net.prismarin.core.essentials;

import com.google.inject.Singleton;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

@Singleton
public final class InventoryWatch implements Listener {

    private final Map<UUID, Inventory> viewOnly = new ConcurrentHashMap<>();

    public void open(Player viewer, Inventory inventory, boolean editable) {
        viewOnly.remove(viewer.getUniqueId());
        viewer.openInventory(inventory);
        if (!editable) {
            viewOnly.put(viewer.getUniqueId(), inventory);
        }
    }

    private boolean locked(Player player, Inventory top) {
        Inventory watched = viewOnly.get(player.getUniqueId());
        return watched != null && watched.equals(top);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && locked(player, event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && locked(player, event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory watched = viewOnly.get(event.getPlayer().getUniqueId());
        if (watched != null && watched.equals(event.getInventory())) {
            viewOnly.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        viewOnly.remove(event.getPlayer().getUniqueId());
    }
}
