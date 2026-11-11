package net.prismarin.lobby.extension;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.prismarin.api.event.Subscription;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.lobby.LobbyPlugin;
import net.prismarin.lobby.api.LobbyHotbarItem;
import net.prismarin.lobby.api.LobbyInfo;
import net.prismarin.lobby.api.LobbyMenu;
import net.prismarin.lobby.api.LobbyMenuEntry;
import net.prismarin.lobby.api.LobbyService;
import net.prismarin.lobby.menu.LobbyMenus;
import net.prismarin.lobby.network.LobbyArrangement;
import net.prismarin.lobby.network.LobbyDirectory;
import net.prismarin.lobby.network.LobbyTarget;
import net.prismarin.lobby.network.LobbyTarget.ServerTarget;
import net.prismarin.lobby.network.LobbyTarget.WorldTarget;
import net.prismarin.lobby.network.LobbyTravel;
import net.prismarin.lobby.player.LobbyPlayerSetup;
import net.prismarin.lobby.world.LobbyWorlds;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

@Singleton
public final class PaperLobbyService implements LobbyService {

    private final LobbyPlugin plugin;
    private final LobbyWorlds worlds;
    private final LobbyDirectory lobbies;
    private final LobbyTravel travel;
    private final LobbyMenus menus;
    private final LobbyPlayerSetup setup;
    private final LobbyExtensions extensions;

    @Inject
    public PaperLobbyService(LobbyPlugin plugin, LobbyWorlds worlds, LobbyDirectory lobbies, LobbyTravel travel,
                             LobbyMenus menus, LobbyPlayerSetup setup, LobbyExtensions extensions) {
        this.plugin = plugin;
        this.worlds = worlds;
        this.lobbies = lobbies;
        this.travel = travel;
        this.menus = menus;
        this.setup = setup;
        this.extensions = extensions;
    }

    @Override
    public Set<String> worlds() {
        return worlds.names();
    }

    @Override
    public boolean isLobbyWorld(World world) {
        return worlds.isLobbyWorld(world);
    }

    @Override
    public Optional<Location> spawn(String world) {
        return world == null ? Optional.empty() : worlds.spawn(world);
    }

    @Override
    public Optional<Location> spawnFor(Player player) {
        return lobbies.joinSpawn(player);
    }

    @Override
    public boolean sendToSpawn(Player player) {
        return spawnFor(player).map(player::teleport).orElse(false);
    }

    @Override
    public List<LobbyInfo> lobbies() {
        return lobbies.selectorTargets().stream().map(PaperLobbyService::info).toList();
    }

    @Override
    public List<NetworkServer> lobbyServers() {
        return lobbies.lobbyServers();
    }

    @Override
    public boolean travel(Player player, String lobbyId) {
        Optional<LobbyTarget> target = lobbies.selectorTargets().stream()
                .filter(candidate -> candidate.id().equalsIgnoreCase(lobbyId))
                .findFirst();
        target.ifPresent(found -> travel.travel(player, found));
        return target.isPresent();
    }

    @Override
    public boolean open(Player player, LobbyMenu menu) {
        return menus.open(player, menu.id());
    }

    @Override
    public Subscription addHotbarItem(LobbyHotbarItem item) {
        Subscription registered = extensions.hotbar().register(item);
        refreshLobbyPlayers();
        return () -> {
            registered.unsubscribe();
            refreshLobbyPlayers();
        };
    }

    @Override
    public void refreshHotbar(Player player) {
        if (player.isOnline() && worlds.isLobbyWorld(player.getWorld())) {
            setup.refreshItems(player);
        }
    }

    @Override
    public Subscription addNavigatorEntry(LobbyMenuEntry entry) {
        return extensions.navigator().register(entry);
    }

    @Override
    public Subscription addProfileEntry(LobbyMenuEntry entry) {
        return extensions.profile().register(entry);
    }

    @Override
    public Subscription addSelectorEntry(LobbyMenuEntry entry) {
        return extensions.selector().register(entry);
    }

    private void refreshLobbyPlayers() {
        if (!plugin.isEnabled()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                refreshHotbar(player);
            }
        });
    }

    private static LobbyInfo info(LobbyTarget target) {
        return switch (target) {
            case WorldTarget world -> new LobbyInfo(world.id(), world.displayName(), LobbyInfo.Kind.WORLD,
                    world.lobby().world(), world.permission(), world.lobby().softMaxPlayers());
            case ServerTarget server -> new LobbyInfo(server.id(), server.displayName(), LobbyInfo.Kind.SERVER,
                    server.server().name(), server.permission(), LobbyArrangement.softMax(server.server()));
        };
    }
}
