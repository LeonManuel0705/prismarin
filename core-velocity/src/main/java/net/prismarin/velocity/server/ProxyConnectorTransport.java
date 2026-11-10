package net.prismarin.velocity.server;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.prismarin.shared.status.SelectingConnector;

public final class ProxyConnectorTransport implements SelectingConnector.Transport {

    private final ProxyServer proxy;

    public ProxyConnectorTransport(ProxyServer proxy) {
        this.proxy = proxy;
    }

    @Override
    public CompletableFuture<Boolean> connect(UUID playerId, String server) {
        Optional<Player> player = proxy.getPlayer(playerId);
        Optional<RegisteredServer> target = proxy.getServer(server);
        if (player.isEmpty() || target.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        return player.get().createConnectionRequest(target.get()).connect()
                .thenApply(result -> result.isSuccessful())
                .exceptionally(error -> false);
    }
}
