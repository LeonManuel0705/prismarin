package net.prismarin.shared.status;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.server.NetworkConnector;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.api.server.ServerStatusService;

public final class SelectingConnector implements NetworkConnector {

    @FunctionalInterface
    public interface Transport {
        CompletableFuture<Boolean> connect(UUID player, String server);
    }

    private final ServerDirectory directory;
    private final ServerStatusService statuses;
    private final Transport transport;

    public SelectingConnector(ServerDirectory directory, ServerStatusService statuses, Transport transport) {
        this.directory = directory;
        this.statuses = statuses;
        this.transport = transport;
    }

    @Override
    public CompletableFuture<Boolean> connect(UUID player, String server) {
        if (directory.byName(server).isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        return transport.connect(player, server);
    }

    @Override
    public Optional<NetworkServer> pick(String group) {
        return ServerSelector.pick(directory.byGroup(group), statuses::status);
    }

    @Override
    public CompletableFuture<Optional<NetworkServer>> connectToGroup(UUID player, String group) {
        Optional<NetworkServer> target = pick(group);
        if (target.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return transport.connect(player, target.get().name())
                .thenApply(connected -> connected ? target : Optional.<NetworkServer>empty());
    }
}
