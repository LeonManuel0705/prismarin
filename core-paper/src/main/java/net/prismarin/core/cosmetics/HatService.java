package net.prismarin.core.cosmetics;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.prismarin.api.cosmetics.Cosmetic;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

final class HatService implements Listener {

    private record Worn(String id, ItemStack real) {
    }

    private final Plugin plugin;
    private final NamespacedKey idKey;
    private final NamespacedKey realKey;
    private final BiFunction<Player, Cosmetic, ItemStack> items;
    private final Consumer<Player> respawned;
    private final Map<UUID, Worn> worn = new HashMap<>();

    HatService(Plugin plugin, BiFunction<Player, Cosmetic, ItemStack> items, Consumer<Player> respawned) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "cosmetic-hat");
        this.realKey = new NamespacedKey(plugin, "cosmetic-real-helmet");
        this.items = items;
        this.respawned = respawned;
    }

    void wear(Player player, Cosmetic hat) {
        UUID id = player.getUniqueId();
        Worn current = worn.get(id);
        if (hat == null) {
            if (current != null) {
                takeOff(player);
            }
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack helmet = inventory.getHelmet();
        if (current != null && current.id().equals(hat.id()) && isHat(helmet)) {
            return;
        }
        ItemStack real = current != null ? current.real() : isHat(helmet) ? stored(helmet) : copy(helmet);
        ItemStack item = items.apply(player, hat);
        if (item == null) {
            return;
        }
        item.editMeta(meta -> mark(meta, hat.id(), real));
        worn.put(id, new Worn(hat.id(), real));
        inventory.setHelmet(item);
    }

    void takeOff(Player player) {
        Worn current = worn.remove(player.getUniqueId());
        PlayerInventory inventory = player.getInventory();
        ItemStack helmet = inventory.getHelmet();
        if (isHat(helmet)) {
            ItemStack real = current != null ? current.real() : stored(helmet);
            inventory.setHelmet(empty(real) ? null : real);
        } else if (current != null && !empty(current.real())) {
            give(player, current.real());
        }
        removeStray(player);
    }

    void cleanUp(Player player) {
        if (!worn.containsKey(player.getUniqueId())) {
            ItemStack helmet = player.getInventory().getHelmet();
            if (isHat(helmet)) {
                ItemStack real = stored(helmet);
                player.getInventory().setHelmet(empty(real) ? null : real);
            }
        }
        removeStray(player);
    }

    void takeOffAll() {
        for (UUID id : Map.copyOf(worn).keySet()) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                takeOff(player);
            }
        }
        worn.clear();
    }

    boolean wearing(UUID player) {
        return worn.containsKey(player);
    }

    boolean isHat(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(idKey, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent event) {
        if (isHat(event.getCurrentItem()) || isHat(event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0
                && isHat(player.getInventory().getItem(event.getHotbarButton()))) {
            event.setCancelled(true);
        } else if (event.getClick() == ClickType.SWAP_OFFHAND && isHat(player.getInventory().getItemInOffHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrag(InventoryDragEvent event) {
        if (isHat(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrop(PlayerDropItemEvent event) {
        if (isHat(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (isHat(event.getMainHandItem()) || isHat(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlace(BlockPlaceEvent event) {
        if (isHat(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isHat);
        if (event.getKeepInventory()) {
            return;
        }
        Worn current = worn.remove(event.getPlayer().getUniqueId());
        if (current != null && !empty(current.real())) {
            event.getDrops().add(current.real());
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                respawned.accept(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        takeOff(event.getPlayer());
    }

    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        if (event.getSlot() != EquipmentSlot.HEAD || !isHat(event.getOldItem()) || isHat(event.getNewItem())) {
            return;
        }
        Player player = event.getPlayer();
        Worn current = worn.get(player.getUniqueId());
        if (current == null || isHat(player.getInventory().getHelmet())) {
            return;
        }
        worn.remove(player.getUniqueId());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            removeStray(player);
            if (!empty(current.real())) {
                give(player, current.real());
            }
        });
    }

    private void mark(ItemMeta meta, String id, ItemStack real) {
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(idKey, PersistentDataType.STRING, id);
        if (!empty(real)) {
            try {
                data.set(realKey, PersistentDataType.BYTE_ARRAY, real.serializeAsBytes());
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.FINE, "Could not keep a copy of a helmet in a hat", e);
            }
        }
        if (meta instanceof Damageable) {
            meta.setUnbreakable(true);
        }
    }

    private ItemStack stored(ItemStack hat) {
        if (hat == null || !hat.hasItemMeta()) {
            return null;
        }
        byte[] bytes = hat.getItemMeta().getPersistentDataContainer().get(realKey, PersistentDataType.BYTE_ARRAY);
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(bytes);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "A helmet stored in a cosmetic hat could not be read", e);
            return null;
        }
    }

    private void removeStray(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            if (isHat(storage[slot])) {
                inventory.setItem(slot, null);
            }
        }
        if (isHat(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(null);
        }
        if (isHat(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
        Worn current = worn.get(player.getUniqueId());
        if (current == null && isHat(inventory.getHelmet())) {
            ItemStack real = stored(inventory.getHelmet());
            inventory.setHelmet(empty(real) ? null : real);
        }
    }

    private static void give(Player player, ItemStack item) {
        player.getInventory().addItem(item.clone()).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private static ItemStack copy(ItemStack item) {
        return empty(item) ? null : item.clone();
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.isEmpty();
    }
}
