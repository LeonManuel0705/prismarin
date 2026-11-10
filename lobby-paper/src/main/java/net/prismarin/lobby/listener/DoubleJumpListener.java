package net.prismarin.lobby.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.prismarin.core.essentials.FlightGrants;
import net.prismarin.core.vanish.VanishService;
import net.prismarin.lobby.world.LobbyWorlds;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.util.Vector;

@Singleton
public final class DoubleJumpListener implements Listener {

    private static final double FORWARD_STRENGTH = 1.4;
    private static final double UPWARD_STRENGTH = 0.9;

    private final LobbyWorlds worlds;
    private final VanishService vanish;
    private final FlightGrants grants;

    @Inject
    public DoubleJumpListener(LobbyWorlds worlds, VanishService vanish, FlightGrants grants) {
        this.worlds = worlds;
        this.vanish = vanish;
        this.grants = grants;
    }

    private boolean freeFlight(Player player) {
        return grants.granted(player) || vanish.vanished(player) && vanish.settings().flight();
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (!worlds.isLobbyWorld(player.getWorld()) || freeFlight(player)) {
            return;
        }
        event.setCancelled(true);
        player.setAllowFlight(false);
        player.setFlying(false);
        Vector velocity = player.getLocation().getDirection().multiply(FORWARD_STRENGTH).setY(UPWARD_STRENGTH);
        player.setVelocity(velocity);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 24, 0.4, 0.2, 0.4, 0.08);
        player.playSound(Sound.sound(Key.key("entity.ender_dragon.flap"), Sound.Source.MASTER, 0.8f, 1.1f),
                Sound.Emitter.self());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getAllowFlight() || freeFlight(player)) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (!worlds.isLobbyWorld(player.getWorld())) {
            return;
        }
        if (standsOnBlock(player)) {
            player.setAllowFlight(true);
        }
    }

    private static boolean standsOnBlock(Player player) {
        return player.getLocation().subtract(0.0, 0.1, 0.0).getBlock().getType().isSolid();
    }
}
