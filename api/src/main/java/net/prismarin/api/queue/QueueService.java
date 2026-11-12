package net.prismarin.api.queue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface QueueService {

    CompletableFuture<QueueResult> join(UUID player, QueueTarget target);

    CompletableFuture<Boolean> leave(UUID player);

    CompletableFuture<Boolean> leave(UUID player, QueueTarget target);

    CompletableFuture<List<QueuePosition>> positions(UUID player);

    default CompletableFuture<Optional<QueuePosition>> position(UUID player) {
        return positions(player).thenApply(found -> found.stream().findFirst());
    }

    default CompletableFuture<Integer> size(QueueTarget target) {
        return queue(target).thenApply(info -> info.map(QueueInfo::size).orElse(0));
    }

    CompletableFuture<List<QueueInfo>> queues();

    CompletableFuture<Optional<QueueInfo>> queue(QueueTarget target);

    CompletableFuture<Boolean> pause(QueueTarget target, String actor);

    CompletableFuture<Boolean> resume(QueueTarget target, String actor);

    CompletableFuture<Integer> clear(QueueTarget target);

    CompletableFuture<Optional<UUID>> sendNext(QueueTarget target);

    CompletableFuture<Boolean> joinable(String server, boolean joinable);

    CompletableFuture<Set<String>> closedServers();
}
