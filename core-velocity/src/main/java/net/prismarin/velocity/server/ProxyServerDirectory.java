package net.prismarin.velocity.server;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.api.server.ServerGroup;

public final class ProxyServerDirectory implements ServerDirectory {

    private final ProxyServer proxy;
    private final ServerDirectory configured;
    private final String lobbyServer;

    public ProxyServerDirectory(ProxyServer proxy, ServerDirectory configured, String lobbyServer) {
        this.proxy = proxy;
        this.configured = configured;
        this.lobbyServer = lobbyServer;
    }

    @Override
    public List<NetworkServer> servers() {
        return proxy.getAllServers().stream().map(this::describe).toList();
    }

    @Override
    public List<ServerGroup> groups() {
        return configured.groups();
    }

    @Override
    public Optional<NetworkServer> byName(String name) {
        return proxy.getServer(name).map(this::describe);
    }

    private NetworkServer describe(RegisteredServer server) {
        String name = server.getServerInfo().getName();
        InetSocketAddress address = server.getServerInfo().getAddress();
        return configured.byName(name)
                .map(known -> new NetworkServer(name, known.displayName(), address.getHostString(),
                        address.getPort(), known.group(), known.properties()))
                .orElseGet(() -> new NetworkServer(name, name, address.getHostString(), address.getPort(),
                        name.equalsIgnoreCase(lobbyServer) ? NetworkServer.GROUP_LOBBY : NetworkServer.GROUP_DEFAULT,
                        Map.of()));
    }
}
