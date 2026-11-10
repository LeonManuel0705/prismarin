package net.prismarin.velocity.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.api.server.ServerGroup;
import net.prismarin.velocity.config.ProxyConfigStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class LobbyBalancerTest {

    @TempDir
    Path directory;

    private final ProxyServer proxy = mock(ProxyServer.class);
    private final Map<String, RegisteredServer> registered = new HashMap<>();
    private ProxyConfigStore configStore;

    @BeforeEach
    void setUp() {
        configStore = new ProxyConfigStore(directory.resolve("proxy.yml"), Runnable::run,
                LoggerFactory.getLogger(LobbyBalancerTest.class));
        when(proxy.getServer(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(registered.get(invocation.getArgument(0, String.class))));
    }

    private void register(String name, int players) {
        RegisteredServer server = mock(RegisteredServer.class);
        when(server.getServerInfo()).thenReturn(new ServerInfo(name, InetSocketAddress.createUnresolved("x", 1)));
        when(server.getPlayersConnected()).thenReturn(Collections.nCopies(players, mock(Player.class)));
        registered.put(name, server);
    }

    private static NetworkServer lobby(String name, Map<String, String> properties) {
        return new NetworkServer(name, name, "x", 1, NetworkServer.GROUP_LOBBY, properties);
    }

    private static ServerDirectory directoryOf(List<NetworkServer> servers, Map<String, String> groupProperties) {
        return new ServerDirectory() {
            @Override
            public List<NetworkServer> servers() {
                return servers;
            }

            @Override
            public Optional<NetworkServer> byName(String name) {
                return servers.stream().filter(server -> server.name().equalsIgnoreCase(name)).findFirst();
            }

            @Override
            public List<ServerGroup> groups() {
                return List.of(new ServerGroup(NetworkServer.GROUP_LOBBY, "Lobby", groupProperties));
            }
        };
    }

    private static Player player(String... permissions) {
        Player player = mock(Player.class);
        Set<String> granted = Set.of(permissions);
        when(player.hasPermission(anyString())).thenAnswer(invocation ->
                granted.contains(invocation.getArgument(0, String.class)));
        return player;
    }

    private String picked(LobbyBalancer balancer, Player player, String exclude) {
        return balancer.pick(player, exclude).map(server -> server.getServerInfo().getName()).orElse("none");
    }

    @Test
    void sendsPlayersToTheEmptiestLobby() {
        register("lobby-1", 5);
        register("lobby-2", 2);
        LobbyBalancer balancer = new LobbyBalancer(proxy,
                directoryOf(List.of(lobby("lobby-1", Map.of()), lobby("lobby-2", Map.of())), Map.of()),
                configStore, true);
        assertEquals("lobby-2", picked(balancer, player(), null));
        assertEquals("lobby-1", picked(balancer, player(), "lobby-2"));
    }

    @Test
    void premiumLobbiesNeedThePermissionAndAnOptIn() {
        register("lobby-1", 9);
        register("vip", 0);
        List<NetworkServer> servers = List.of(lobby("lobby-1", Map.of()), lobby("vip", Map.of("permission", "rank.vip")));
        LobbyBalancer strict = new LobbyBalancer(proxy, directoryOf(servers, Map.of()), configStore, true);
        assertEquals("lobby-1", picked(strict, player("rank.vip"), null));
        LobbyBalancer optIn = new LobbyBalancer(proxy, directoryOf(servers, Map.of("premium-auto-join", "true")),
                configStore, true);
        assertEquals("vip", picked(optIn, player("rank.vip"), null));
        assertEquals("lobby-1", picked(optIn, player(), null));
    }

    @Test
    void fillFirstComesFromTheGroupProperties() {
        register("lobby-1", 10);
        register("lobby-2", 3);
        List<NetworkServer> servers = List.of(lobby("lobby-1", Map.of("soft-max-players", "20")),
                lobby("lobby-2", Map.of("soft-max-players", "20")));
        LobbyBalancer balancer = new LobbyBalancer(proxy, directoryOf(servers, Map.of("distribution", "fill-first")),
                configStore, true);
        assertEquals("lobby-1", picked(balancer, player(), null));
    }

    @Test
    void fallsBackToTheConfiguredLobbyServer() {
        register("lobby", 50);
        LobbyBalancer empty = new LobbyBalancer(proxy, directoryOf(List.of(), Map.of()), configStore, true);
        assertEquals("lobby", picked(empty, player(), null));
        assertEquals("none", picked(empty, player(), "lobby"));
        register("lobby-2", 0);
        LobbyBalancer disabled = new LobbyBalancer(proxy, directoryOf(List.of(lobby("lobby-2", Map.of())), Map.of()),
                configStore, false);
        assertEquals("lobby", picked(disabled, player(), null));
    }

    @Test
    void knowsWhichServersAreLobbies() {
        LobbyBalancer balancer = new LobbyBalancer(proxy, directoryOf(List.of(lobby("lobby-2", Map.of()),
                new NetworkServer("bedwars", "bedwars", "x", 1, "bedwars", Map.of())), Map.of()), configStore, true);
        assertTrue(balancer.isLobby("lobby"));
        assertTrue(balancer.isLobby("LOBBY-2"));
        assertFalse(balancer.isLobby("bedwars"));
    }
}
