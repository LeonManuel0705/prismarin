package net.prismarin.lobby.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import java.util.OptionalDouble;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.prismarin.lobby.config.LobbySettings;
import net.prismarin.lobby.network.LobbyDirectory;
import net.prismarin.lobby.player.BuildModeService;
import net.prismarin.lobby.world.LobbyWorlds;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

@Singleton
public final class VoidTeleportListener implements Listener {

    private static final Sound RESCUE = Sound.sound(Key.key("entity.enderman.teleport"), Sound.Source.MASTER,
            0.7f, 0.9f);

    private final LobbyWorlds worlds;
    private final LobbyDirectory lobbies;
    private final BuildModeService buildMode;
    private final LobbySettings settings;

    @Inject
    public VoidTeleportListener(LobbyWorlds worlds, LobbyDirectory lobbies, BuildModeService buildMode,
                                LobbySettings settings) {
        this.worlds = worlds;
        this.lobbies = lobbies;
        this.buildMode = buildMode;
        this.settings = settings;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) {
            return;
        }
        if (below(event.getPlayer(), event.getTo())) {
            rescue(event.getPlayer());
        }
    }

    public boolean rescueIfFalling(Player player) {
        if (!below(player, player.getLocation())) {
            return false;
        }
        return rescue(player);
    }

    private boolean below(Player player, Location location) {
        if (!settings.enabled(LobbySettings.VOID_TELEPORT) || player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        if (!worlds.isLobbyWorld(location.getWorld()) || buildMode.active(player)) {
            return false;
        }
        OptionalDouble height = worlds.voidHeight(location.getWorld());
        return height.isPresent() && location.getY() < height.getAsDouble();
    }

    private boolean rescue(Player player) {
        Optional<Location> target = lobbies.rescueSpawn(player);
        if (target.isEmpty()) {
            return false;
        }
        player.setFallDistance(0.0f);
        player.teleport(target.get());
        player.playSound(RESCUE, Sound.Emitter.self());
        return true;
    }
}
