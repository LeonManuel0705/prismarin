package net.prismarin.lobby.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.prismarin.lobby.config.LocationsFile;
import net.prismarin.lobby.player.BuildModeService;
import net.prismarin.lobby.world.LobbyWorlds;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

@Singleton
public final class ProtectionListener implements Listener {

    private final LobbyWorlds worlds;
    private final LocationsFile locations;
    private final BuildModeService buildMode;

    @Inject
    public ProtectionListener(LobbyWorlds worlds, LocationsFile locations, BuildModeService buildMode) {
        this.worlds = worlds;
        this.locations = locations;
        this.buildMode = buildMode;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (protectedFor(event.getPlayer(), event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (protectedFor(event.getPlayer(), event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && worlds.isLobbyWorld(player.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && worlds.isLobbyWorld(player.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (protectedFor(event.getPlayer(), event.getPlayer().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && protectedFor(player, player.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onWeatherChange(WeatherChangeEvent event) {
        if (event.toWeatherState() && worlds.isLobbyWorld(event.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onThunderChange(ThunderChangeEvent event) {
        if (event.toThunderState() && worlds.isLobbyWorld(event.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() == EntityType.ARMOR_STAND) {
            return;
        }
        if (worlds.isLobbyWorld(event.getLocation().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onIgnite(BlockIgniteEvent event) {
        if (worlds.isLobbyWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBurn(BlockBurnEvent event) {
        if (worlds.isLobbyWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (worlds.isLobbyWorld(event.getLocation().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        if (worlds.isLobbyWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) {
            return;
        }
        Player player = event.getPlayer();
        if (!worlds.isLobbyWorld(player.getWorld())) {
            return;
        }
        if (event.getTo().getY() >= locations.voidTeleportY()) {
            return;
        }
        worlds.teleportToSpawn(player);
        player.playSound(Sound.sound(Key.key("entity.enderman.teleport"), Sound.Source.MASTER, 0.7f, 0.9f),
                Sound.Emitter.self());
    }

    private boolean protectedFor(Player player, World world) {
        return worlds.isLobbyWorld(world) && !buildMode.active(player);
    }
}
