package net.prismarin.lobby.world;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import net.prismarin.lobby.LobbyPlugin;
import net.prismarin.lobby.config.LocationsFile;
import net.prismarin.lobby.config.SpawnPoint;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

@Singleton
public final class LobbyWorlds {

    private final LobbyPlugin plugin;
    private final LocationsFile locations;

    @Inject
    public LobbyWorlds(LobbyPlugin plugin, LocationsFile locations) {
        this.plugin = plugin;
        this.locations = locations;
    }

    public boolean isLobbyWorld(World world) {
        return world.getName().equals(locations.spawn().world());
    }

    public Optional<Location> spawnLocation() {
        SpawnPoint spawn = locations.spawn();
        World world = plugin.getServer().getWorld(spawn.world());
        if (world == null) {
            return Optional.empty();
        }
        return Optional.of(new Location(world, spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch()));
    }

    public void teleportToSpawn(Player player) {
        spawnLocation().ifPresent(player::teleport);
    }

    public void lockWeather() {
        World world = plugin.getServer().getWorld(locations.spawn().world());
        if (world != null) {
            world.setStorm(false);
            world.setThundering(false);
        }
    }
}
