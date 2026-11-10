package net.prismarin.lobby.world;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import net.prismarin.lobby.LobbyPlugin;
import net.prismarin.lobby.config.LobbySettings;
import net.prismarin.lobby.config.LocationsFile;
import net.prismarin.lobby.config.SpawnPoint;
import net.prismarin.lobby.config.WorldLobby;
import org.bukkit.Location;
import org.bukkit.World;

@Singleton
public final class LobbyWorlds {

    private final LobbyPlugin plugin;
    private final LocationsFile locations;
    private final LobbySettings settings;
    private volatile Set<String> names;

    @Inject
    public LobbyWorlds(LobbyPlugin plugin, LocationsFile locations, LobbySettings settings) {
        this.plugin = plugin;
        this.locations = locations;
        this.settings = settings;
        refresh();
    }

    public void refresh() {
        Set<String> collected = new LinkedHashSet<>(settings.worlds());
        settings.lobbies().stream().filter(WorldLobby::enabled).map(WorldLobby::world).forEach(collected::add);
        collected.addAll(locations.spawns().keySet());
        names = Collections.unmodifiableSet(collected);
    }

    public Set<String> names() {
        return names;
    }

    public boolean isLobbyWorld(World world) {
        return world != null && names.contains(world.getName());
    }

    public Optional<Location> spawn(String worldName) {
        Optional<SpawnPoint> point = locations.spawn(worldName);
        if (point.isEmpty()) {
            return Optional.empty();
        }
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            return Optional.empty();
        }
        SpawnPoint spawn = point.get();
        return Optional.of(new Location(world, spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch()));
    }

    public Optional<Location> spawn(World world) {
        return spawn(world.getName());
    }

    public OptionalDouble voidHeight(World world) {
        return locations.voidHeight(world.getName());
    }

    public void lockWeather() {
        for (String name : names) {
            World world = plugin.getServer().getWorld(name);
            if (world != null) {
                world.setStorm(false);
                world.setThundering(false);
            }
        }
    }
}
