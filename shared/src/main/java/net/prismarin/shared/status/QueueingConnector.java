package net.prismarin.shared.status;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.prismarin.api.queue.QueueResult;
import net.prismarin.api.queue.QueueService;
import net.prismarin.api.queue.QueueStatus;
import net.prismarin.api.queue.QueueTarget;
import net.prismarin.api.server.NetworkConnector;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;

public final class QueueingConnector implements NetworkConnector {

    private final NetworkConnector direct;
    private final ServerDirectory directory;
    private final Supplier<Optional<QueueService>> queue;

    public QueueingConnector(NetworkConnector direct, ServerDirectory directory,
                             Supplier<Optional<QueueService>> queue) {
        this.direct = direct;
        this.directory = directory;
        this.queue = queue;
    }

    @Override
    public CompletableFuture<Boolean> connect(UUID player, String server) {
        return direct.connect(player, server);
    }

    @Override
    public Optional<NetworkServer> pick(String group) {
        return direct.pick(group);
    }

    @Override
    public CompletableFuture<Optional<NetworkServer>> connectToGroup(UUID player, String group) {
        return travel(player, QueueTarget.group(group)).thenApply(result -> result.sent()
                ? result.server().flatMap(directory::byName)
                : Optional.<NetworkServer>empty());
    }

    public CompletableFuture<QueueResult> travel(UUID player, QueueTarget target) {
        Optional<QueueService> service = queue.get();
        if (service.isEmpty()) {
            return fallback(player, target);
        }
        return service.get().join(player, target)
                .exceptionally(error -> QueueResult.of(QueueStatus.DISABLED))
                .thenCompose(result -> result.status() == QueueStatus.DISABLED
                        ? fallback(player, target)
                        : CompletableFuture.completedFuture(result));
    }

    private CompletableFuture<QueueResult> fallback(UUID player, QueueTarget target) {
        if (target.isGroup()) {
            if (directory.byGroup(target.name()).isEmpty()) {
                return CompletableFuture.completedFuture(QueueResult.of(QueueStatus.UNKNOWN_TARGET));
            }
            return direct.connectToGroup(player, target.name()).thenApply(server -> server
                    .map(found -> QueueResult.sent(found.name()))
                    .orElseGet(() -> QueueResult.of(QueueStatus.NO_SERVER)));
        }
        Optional<NetworkServer> server = directory.byName(target.name());
        if (server.isEmpty()) {
            return CompletableFuture.completedFuture(QueueResult.of(QueueStatus.UNKNOWN_TARGET));
        }
        return direct.connect(player, server.get().name()).thenApply(connected -> connected
                ? QueueResult.sent(server.get().name())
                : QueueResult.of(QueueStatus.FAILED));
    }
}
