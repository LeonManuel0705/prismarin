package net.prismarin.lobby.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import net.prismarin.lobby.item.LobbyItems;
import net.prismarin.lobby.menu.LobbyMenus;
import net.prismarin.lobby.player.BuildModeService;
import net.prismarin.lobby.player.PlayerHiderService;
import net.prismarin.lobby.world.LobbyWorlds;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

@Singleton
public final class HotbarListener implements Listener {

    private final LobbyItems items;
    private final LobbyMenus menus;
    private final PlayerHiderService hider;
    private final BuildModeService buildMode;
    private final LobbyWorlds worlds;

    @Inject
    public HotbarListener(LobbyItems items, LobbyMenus menus, PlayerHiderService hider,
                          BuildModeService buildMode, LobbyWorlds worlds) {
        this.items = items;
        this.menus = menus;
        this.hider = hider;
        this.buildMode = buildMode;
        this.worlds = worlds;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Optional<String> id = items.itemId(event.getItem());
        if (id.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        String value = id.get();
        if (LobbyItems.NAVIGATOR_ID.equals(value)) {
            menus.openNavigator(player);
        } else if (LobbyItems.PROFILE_ID.equals(value)) {
            menus.openProfile(player);
        } else if (LobbyItems.HIDER_ID.equals(value)) {
            hider.toggle(player);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!worlds.isLobbyWorld(player.getWorld()) || buildMode.active(player)) {
            return;
        }
        if (items.isLobbyItem(event.getCurrentItem()) || items.isLobbyItem(event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        if (event.getHotbarButton() >= 0
                && items.isLobbyItem(player.getInventory().getItem(event.getHotbarButton()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (items.isLobbyItem(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (items.isLobbyItem(event.getMainHandItem()) || items.isLobbyItem(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (items.isLobbyItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }
}
