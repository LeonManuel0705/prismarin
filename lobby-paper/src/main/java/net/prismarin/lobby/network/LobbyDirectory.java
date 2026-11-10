package net.prismarin.lobby.network;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;
import net.prismarin.api.PrismarinSystems;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.api.server.ServerStatus;
import net.prismarin.lobby.LobbyPlugin;
import net.prismarin.lobby.config.LobbySettings;
import net.prismarin.lobby.config.LocationsFile;
import net.prismarin.lobby.config.WorldLobby;
import net.prismarin.lobby.network.LobbyTarget.ServerTarget;
import net.prismarin.lobby.network.LobbyTarget.WorldTarget;
import net.prismarin.lobby.world.LobbyWorlds;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.status.LobbySelector;
import net.prismarin.shared.status.LobbySelector.Candidate;
import net.prismarin.shared.status.NetworkStatusService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

@Singleton
public final class LobbyDirectory {

    private final LobbyPlugin plugin;
    private final LobbySettings settings;
    private final LobbyWorlds worlds;
    private final LocationsFile locations;
    private final ServerDirectory directory;
    private final NetworkStatusService status;
    private final CoreConfig config;

    @Inject
    public LobbyDirectory(LobbyPlugin plugin, LobbySettings settings, LobbyWorlds worlds, LocationsFile locations,
                          ServerDirectory directory, NetworkStatusService status, CoreConfig config) {
        this.plugin = plugin;
        this.settings = settings;
        this.worlds = worlds;
        this.locations = locations;
        this.directory = directory;
        this.status = status;
        this.config = config;
    }

    public List<WorldLobby> worldLobbies() {
        return settings.lobbies().stream()
                .filter(WorldLobby::enabled)
                .filter(lobby -> plugin.getServer().getWorld(lobby.world()) != null)
                .toList();
    }

    public Optional<WorldLobby> worldLobby(String id) {
        return worldLobbies().stream().filter(lobby -> lobby.id().equalsIgnoreCase(id)).findFirst();
    }

    public List<NetworkServer> lobbyServers() {
        return directory.byGroup(NetworkServer.GROUP_LOBBY);
    }

    public boolean isThisServer(NetworkServer server) {
        return server.name().equalsIgnoreCase(config.serverId());
    }

    public boolean statusKnown() {
        return config.systemEnabled(PrismarinSystems.NETWORK_STATUS);
    }

    public Optional<ServerStatus> status(NetworkServer server) {
        return statusKnown() ? status.status(server.name()) : Optional.empty();
    }

    public boolean reachable(NetworkServer server) {
        return !statusKnown() || status(server).map(ServerStatus::joinable).orElse(false);
    }

    public int players(WorldLobby lobby, Player exclude) {
        World world = plugin.getServer().getWorld(lobby.world());
        if (world == null) {
            return 0;
        }
        return (int) world.getPlayers().stream().filter(player -> !player.equals(exclude)).count();
    }

    public int visiblePlayers(WorldLobby lobby, Player viewer) {
        World world = plugin.getServer().getWorld(lobby.world());
        if (world == null) {
            return 0;
        }
        return (int) world.getPlayers().stream()
                .filter(player -> player.equals(viewer) || viewer.canSee(player))
                .count();
    }

    public boolean permitted(Player player, LobbyTarget target) {
        return !target.premium() || player.hasPermission(target.permission());
    }

    public boolean isCurrent(Player player, LobbyTarget target) {
        return switch (target) {
            case WorldTarget world -> world.lobby().world().equals(player.getWorld().getName());
            case ServerTarget server -> isThisServer(server.server());
        };
    }

    public List<LobbyTarget> selectorTargets() {
        return LobbyArrangement.arrange(worldLobbies(), lobbyServers(), config.serverId());
    }

    public boolean hasChoice() {
        return selectorTargets().size() > 1;
    }

    public Optional<WorldLobby> distribute(Player player) {
        List<Candidate<WorldLobby>> candidates = worldLobbies().stream()
                .filter(lobby -> worlds.spawn(lobby.world()).isPresent())
                .map(lobby -> new Candidate<>(lobby, players(lobby, player), lobby.softMaxPlayers(), true,
                        lobby.premium(), !lobby.premium() || player.hasPermission(lobby.permission())))
                .toList();
        return LobbySelector.pick(candidates, settings.distribution(), settings.premiumAutoJoin());
    }

    public Optional<Location> joinSpawn(Player player) {
        if (settings.enabled(LobbySettings.LOBBY_DISTRIBUTION)) {
            Optional<Location> chosen = distribute(player).flatMap(lobby -> worlds.spawn(lobby.world()));
            if (chosen.isPresent()) {
                return chosen;
            }
        }
        return defaultSpawn(player);
    }

    public Optional<Location> rescueSpawn(Player player) {
        Optional<Location> own = worlds.spawn(player.getWorld()).filter(this::aboveVoid);
        if (own.isPresent()) {
            return own;
        }
        return distribute(player).flatMap(lobby -> worlds.spawn(lobby.world())).filter(this::aboveVoid)
                .or(() -> defaultSpawn(player).filter(this::aboveVoid));
    }

    public boolean aboveVoid(Location location) {
        OptionalDouble height = worlds.voidHeight(location.getWorld());
        return height.isEmpty() || location.getY() >= height.getAsDouble();
    }

    public Optional<Location> defaultSpawn(Player player) {
        for (WorldLobby lobby : worldLobbies()) {
            boolean allowed = !lobby.premium()
                    || (settings.premiumAutoJoin() && player.hasPermission(lobby.permission()));
            Optional<Location> spawn = allowed ? worlds.spawn(lobby.world()) : Optional.empty();
            if (spawn.isPresent()) {
                return spawn;
            }
        }
        Set<String> premiumWorlds = settings.lobbies().stream()
                .filter(WorldLobby::premium)
                .map(WorldLobby::world)
                .collect(Collectors.toSet());
        for (String world : locations.spawns().keySet()) {
            if (premiumWorlds.contains(world)) {
                continue;
            }
            Optional<Location> spawn = worlds.spawn(world);
            if (spawn.isPresent()) {
                return spawn;
            }
        }
        return Optional.empty();
    }

    public Optional<LobbyTarget> premium(Player player) {
        String configured = settings.item(LobbySettings.PREMIUM_ITEM).target();
        if (!configured.isEmpty()) {
            Optional<LobbyTarget> world = worldLobby(configured).map(WorldTarget::new);
            return world.isPresent() ? world : directory.byName(configured).map(ServerTarget::new);
        }
        Optional<WorldLobby> world = worldLobbies().stream().filter(WorldLobby::premium).findFirst();
        if (world.isPresent()) {
            return world.map(WorldTarget::new);
        }
        List<ServerTarget> servers = lobbyServers().stream()
                .filter(server -> !isThisServer(server))
                .map(ServerTarget::new)
                .filter(ServerTarget::premium)
                .toList();
        if (servers.isEmpty()) {
            return Optional.empty();
        }
        List<Candidate<ServerTarget>> candidates = new ArrayList<>();
        for (ServerTarget target : servers) {
            int players = status(target.server()).filter(ServerStatus::online).map(ServerStatus::players).orElse(0);
            candidates.add(new Candidate<>(target, players, LobbyArrangement.softMax(target.server()),
                    reachable(target.server()), true, player == null || player.hasPermission(target.permission())));
        }
        return LobbySelector.pick(candidates, LobbySelector.Strategy.LEAST_PLAYERS, true)
                .<LobbyTarget>map(target -> target)
                .or(() -> Optional.of(servers.getFirst()));
    }

    public boolean hasPremium() {
        return premium(null).isPresent();
    }
}
