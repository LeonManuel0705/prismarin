package net.prismarin.core.gui;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.config.MenuSettings;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

@Singleton
public final class MenuService implements Listener {

    private final PrismarinCorePlugin plugin;
    private final Sound clickSound;
    private final Map<UUID, Menu> openMenus = new HashMap<>();

    @Inject
    public MenuService(PrismarinCorePlugin plugin, MenuSettings settings) {
        this.plugin = plugin;
        this.clickSound = Sound.sound(
                soundKey(settings.clickSound()),
                Sound.Source.MASTER,
                settings.clickVolume(),
                settings.clickPitch());
    }

    public void open(Player player, Menu menu) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            menu.prepare(player);
            MenuHolder holder = new MenuHolder(menu);
            Inventory inventory = plugin.getServer().createInventory(holder, menu.size(), menu.title());
            holder.inventory(inventory);
            menu.buttons().forEach((slot, button) -> inventory.setItem(slot, button.icon()));
            player.openInventory(inventory);
            openMenus.put(player.getUniqueId(), menu);
        });
    }

    public Optional<Menu> openMenu(Player player) {
        return Optional.ofNullable(openMenus.get(player.getUniqueId()));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() != event.getInventory()) {
            return;
        }
        MenuButton button = holder.menu().buttonAt(event.getSlot());
        if (button == null) {
            return;
        }
        player.playSound(clickSound, Sound.Emitter.self());
        button.action().accept(new ClickContext(player, event.getSlot(), event.getClick(), holder.menu(), this));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder holder
                && openMenus.get(event.getPlayer().getUniqueId()) == holder.menu()) {
            openMenus.remove(event.getPlayer().getUniqueId());
        }
    }

    private static Key soundKey(String configured) {
        try {
            return Key.key(configured);
        } catch (InvalidKeyException e) {
            return Key.key("minecraft", "ui.button.click");
        }
    }
}
