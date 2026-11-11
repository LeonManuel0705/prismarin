package net.prismarin.lobby.api;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.prismarin.api.event.Subscription;
import net.prismarin.api.server.NetworkServer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public interface LobbyService {

    Set<String> worlds();

    boolean isLobbyWorld(World world);

    default boolean inLobby(Player player) {
        return isLobbyWorld(player.getWorld());
    }

    Optional<Location> spawn(String world);

    default Optional<Location> spawn(World world) {
        return spawn(world.getName());
    }

    Optional<Location> spawnFor(Player player);

    boolean sendToSpawn(Player player);

    List<LobbyInfo> lobbies();

    List<NetworkServer> lobbyServers();

    boolean travel(Player player, String lobbyId);

    boolean open(Player player, LobbyMenu menu);

    Subscription addHotbarItem(LobbyHotbarItem item);

    void refreshHotbar(Player player);

    Subscription addNavigatorEntry(LobbyMenuEntry entry);

    Subscription addProfileEntry(LobbyMenuEntry entry);

    Subscription addSelectorEntry(LobbyMenuEntry entry);
}
