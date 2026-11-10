package net.prismarin.velocity.server;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.api.server.ServerGroup;
import net.prismarin.api.server.ServerStatus;
import net.prismarin.api.server.ServerStatusService;
import net.prismarin.shared.status.LobbySelector;
import net.prismarin.shared.status.LobbySelector.Candidate;
import net.prismarin.shared.status.LobbySelector.Strategy;
import net.prismarin.velocity.admin.ServerMaintenance;
import net.prismarin.velocity.config.ProxyConfigStore;

public final class LobbyBalancer {

    public static final String PERMISSION_PROPERTY = "permission";
    public static final String SOFT_MAX_PROPERTY = "soft-max-players";
    public static final String DISTRIBUTION_PROPERTY = "distribution";
    public static final String PREMIUM_AUTO_JOIN_PROPERTY = "premium-auto-join";
    private static final long REFRESH_SECONDS = 10L;

    private final ProxyServer proxy;
    private final ServerDirectory directory;
    private final ProxyConfigStore configStore;
    private final boolean enabled;
    private volatile ServerStatusService status;
    private volatile Predicate<String> blocked = server -> false;
    private volatile boolean refreshed;

    public LobbyBalancer(ProxyServer proxy, ServerDirectory directory, ProxyConfigStore configStore,
                         boolean enabled) {
        this.proxy = proxy;
        this.directory = directory;
        this.configStore = configStore;
        this.enabled = enabled;
    }

    public void start(Object plugin, ServerStatusService status, ServerMaintenance maintenance) {
        this.status = status;
        if (maintenance != null) {
            this.blocked = maintenance::blocked;
        }
        if (!enabled) {
            return;
        }
        proxy.getEventManager().register(plugin, this);
        proxy.getScheduler().buildTask(plugin, this::refresh).repeat(REFRESH_SECONDS, TimeUnit.SECONDS).schedule();
    }

    @Subscribe
    public void onChooseInitialServer(PlayerChooseInitialServerEvent event) {
        Optional<RegisteredServer> initial = event.getInitialServer();
        if (initial.isPresent() && !isLobby(initial.get().getServerInfo().getName())) {
            return;
        }
        pick(event.getPlayer(), null).ifPresent(event::setInitialServer);
    }

    public boolean isLobby(String serverName) {
        if (serverName.equalsIgnoreCase(configStore.config().lobbyServer())) {
            return true;
        }
        return directory.byName(serverName).map(server -> server.inGroup(NetworkServer.GROUP_LOBBY)).orElse(false);
    }

    public Optional<RegisteredServer> pick(Player player, String exclude) {
        if (enabled) {
            Optional<RegisteredServer> balanced = balanced(player, exclude);
            if (balanced.isPresent()) {
                return balanced;
            }
        }
        String fallback = configStore.config().lobbyServer();
        if (exclude != null && fallback.equalsIgnoreCase(exclude)) {
            return Optional.empty();
        }
        return proxy.getServer(fallback);
    }

    private Optional<RegisteredServer> balanced(Player player, String exclude) {
        List<NetworkServer> lobbies = directory.byGroup(NetworkServer.GROUP_LOBBY).stream()
                .filter(server -> exclude == null || !server.name().equalsIgnoreCase(exclude))
                .sorted(Comparator.comparing(NetworkServer::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (lobbies.isEmpty()) {
            return Optional.empty();
        }
        Optional<ServerGroup> group = directory.group(NetworkServer.GROUP_LOBBY);
        Strategy strategy = group.flatMap(value -> value.property(DISTRIBUTION_PROPERTY))
                .flatMap(Strategy::parse)
                .orElse(Strategy.LEAST_PLAYERS);
        boolean includePremium = group.flatMap(value -> value.property(PREMIUM_AUTO_JOIN_PROPERTY))
                .map(Boolean::parseBoolean)
                .orElse(false);
        boolean bypass = player.hasPermission(ServerMaintenance.BYPASS);
        List<Candidate<RegisteredServer>> candidates = new ArrayList<>();
        for (NetworkServer server : lobbies) {
            Optional<RegisteredServer> registered = proxy.getServer(server.name());
            if (registered.isEmpty()) {
                continue;
            }
            int players = registered.get().getPlayersConnected().size();
            String permission = server.property(PERMISSION_PROPERTY).map(String::strip).orElse("");
            boolean premium = !permission.isEmpty();
            boolean available = available(server.name(), players) && (bypass || !blocked.test(server.name()));
            candidates.add(new Candidate<>(registered.get(), players, softMax(server), available, premium,
                    !premium || player.hasPermission(permission)));
        }
        return LobbySelector.pick(candidates, strategy, includePremium);
    }

    private boolean available(String server, int players) {
        ServerStatusService current = status;
        if (current == null || !refreshed) {
            return true;
        }
        Optional<ServerStatus> entry = current.status(server);
        if (entry.isEmpty() || !entry.get().online()) {
            return false;
        }
        int max = entry.get().maxPlayers();
        return entry.get().state().joinable() && (max <= 0 || players < max);
    }

    private void refresh() {
        ServerStatusService current = status;
        if (current != null) {
            current.refresh().thenRun(() -> refreshed = true);
        }
    }

    static int softMax(NetworkServer server) {
        return server.property(SOFT_MAX_PROPERTY).map(value -> {
            try {
                return Math.max(0, Integer.parseInt(value.strip()));
            } catch (NumberFormatException e) {
                return 0;
            }
        }).orElse(0);
    }
}
