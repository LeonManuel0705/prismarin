package net.prismarin.lobby.listener;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.prismarin.lobby.action.ActionRunner;
import net.prismarin.lobby.action.ItemAction;
import net.prismarin.lobby.action.ItemClick;
import net.prismarin.lobby.api.HotbarClick;
import net.prismarin.lobby.api.LobbyHotbarItem;
import net.prismarin.lobby.config.CustomItem;
import net.prismarin.lobby.cosmetics.CosmeticEntries;
import net.prismarin.lobby.extension.LobbyExtensions;
import net.prismarin.lobby.item.LobbyItems;
import net.prismarin.lobby.menu.LobbyMenus;
import net.prismarin.lobby.menu.NickToolMenu;
import net.prismarin.lobby.network.LobbyTravel;
import net.prismarin.lobby.player.BuildModeService;
import net.prismarin.lobby.player.PlayerHiderService;
import net.prismarin.lobby.player.SilentLobbyService;
import net.prismarin.lobby.util.CooldownTracker;
import net.prismarin.lobby.world.LobbyWorlds;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

@Singleton
public final class HotbarListener implements Listener {

    private static final Duration CLICK_DEBOUNCE = Duration.ofMillis(200);
    private static final long DROP_SWING_MILLIS = 150L;

    private final LobbyItems items;
    private final LobbyMenus menus;
    private final PlayerHiderService hider;
    private final BuildModeService buildMode;
    private final LobbyWorlds worlds;
    private final LobbyTravel travel;
    private final ActionRunner actions;
    private final NickToolMenu nickTool;
    private final SilentLobbyService silent;
    private final LobbyExtensions extensions;
    private final Provider<CosmeticEntries> cosmetics;
    private final CooldownTracker debounce = new CooldownTracker(CLICK_DEBOUNCE);
    private final Map<UUID, Long> lastDrop = new ConcurrentHashMap<>();

    @Inject
    public HotbarListener(LobbyItems items, LobbyMenus menus, PlayerHiderService hider, BuildModeService buildMode,
                          LobbyWorlds worlds, LobbyTravel travel, ActionRunner actions, NickToolMenu nickTool,
                          SilentLobbyService silent, LobbyExtensions extensions,
                          Provider<CosmeticEntries> cosmetics) {
        this.extensions = extensions;
        this.cosmetics = cosmetics;
        this.nickTool = nickTool;
        this.silent = silent;
        this.items = items;
        this.menus = menus;
        this.hider = hider;
        this.buildMode = buildMode;
        this.worlds = worlds;
        this.travel = travel;
        this.actions = actions;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if ((!left && !right) || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Optional<String> id = items.itemId(event.getItem());
        if (id.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        event.setUseItemInHand(Event.Result.DENY);
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        if (left && now - lastDrop.getOrDefault(player.getUniqueId(), 0L) < DROP_SWING_MILLIS) {
            return;
        }
        Optional<CustomItem> custom = items.customItem(id.get());
        if (custom.isPresent()) {
            List<ItemAction> list = custom.get().actionsFor(ItemClick.of(left, player.isSneaking()));
            if (!list.isEmpty() && debounce.tryUse(player.getUniqueId(), now)) {
                actions.run(player, list);
            }
            return;
        }
        Optional<LobbyHotbarItem> added = extensions.hotbarItem(id.get());
        if (added.isPresent()) {
            if (debounce.tryUse(player.getUniqueId(), now)) {
                extensions.click(added.get(), new HotbarClick(player, left, player.isSneaking()));
            }
            return;
        }
        if (!right || !debounce.tryUse(player.getUniqueId(), now)) {
            return;
        }
        switch (id.get()) {
            case LobbyItems.NAVIGATOR_ID -> menus.openNavigator(player);
            case LobbyItems.LOBBY_SELECTOR_ID -> menus.openLobbies(player);
            case LobbyItems.PROFILE_ID -> menus.openProfile(player);
            case LobbyItems.PREMIUM_ID -> travel.premium(player);
            case LobbyItems.HIDER_ID -> hider.toggle(player);
            case LobbyItems.NICK_TOOL_ID -> nickTool.open(player);
            case LobbyItems.SILENT_ID -> silent.toggle(player);
            case LobbyItems.COSMETICS_ID -> cosmetics.get().open(player, null);
            default -> {
            }
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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        lastDrop.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        if (items.isLobbyItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastDrop.remove(id);
        debounce.clear(id);
    }
}
