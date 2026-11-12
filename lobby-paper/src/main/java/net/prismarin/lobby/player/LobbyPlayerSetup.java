package net.prismarin.lobby.player;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.prismarin.core.essentials.FlightGrants;
import net.prismarin.core.vanish.VanishService;
import net.prismarin.lobby.config.LobbySettings;
import net.prismarin.lobby.item.LobbyItems;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

@Singleton
public final class LobbyPlayerSetup {

    private final LobbySettings settings;
    private final LobbyItems items;
    private final PlayerHiderService hider;
    private final BuildModeService buildMode;
    private final VanishService vanish;
    private final FlightGrants grants;
    private final SilentLobbyService silent;

    @Inject
    public LobbyPlayerSetup(LobbySettings settings, LobbyItems items, PlayerHiderService hider,
                            BuildModeService buildMode, VanishService vanish, FlightGrants grants,
                            SilentLobbyService silent) {
        this.silent = silent;
        this.settings = settings;
        this.items = items;
        this.hider = hider;
        this.buildMode = buildMode;
        this.vanish = vanish;
        this.grants = grants;
    }

    public void enter(Player player, boolean clearInventory) {
        if (clearInventory) {
            player.getInventory().clear();
        }
        if (!buildMode.active(player)) {
            player.setGameMode(GameMode.ADVENTURE);
        }
        restore(player);
        if (settings.enabled(LobbySettings.DOUBLE_JUMP) && flightManaged(player)) {
            player.setAllowFlight(true);
        }
        if (!buildMode.active(player)) {
            items.give(player, hider.hiding(player));
        }
    }

    public void startBuilding(Player player) {
        items.removeAll(player);
    }

    public void stopBuilding(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.setStorageContents(new ItemStack[inventory.getStorageContents().length]);
        inventory.setItemInOffHand(null);
        enter(player, false);
    }

    public void leave(Player player) {
        items.removeAll(player);
        silent.end(player);
        if (settings.enabled(LobbySettings.DOUBLE_JUMP) && flightManaged(player) && !vanish.vanished(player)
                && !grants.granted(player)) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    public void removeItems(Player player) {
        items.removeAll(player);
    }

    public void refreshItems(Player player) {
        if (!buildMode.active(player)) {
            items.give(player, hider.hiding(player));
        }
    }

    private static boolean flightManaged(Player player) {
        return player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR;
    }

    private static void restore(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(maxHealth != null ? maxHealth.getValue() : 20.0);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setFireTicks(0);
    }
}
