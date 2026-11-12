package net.prismarin.api.joinme;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.event.Subscription;

public interface JoinMeService {

    CompletableFuture<JoinMeResult> create(JoinMeRequest request);

    default CompletableFuture<JoinMeResult> create(UUID creator) {
        return create(JoinMeRequest.of(creator));
    }

    CompletableFuture<JoinMeResult> preview(JoinMeRequest request);

    default CompletableFuture<JoinMeResult> preview(UUID creator) {
        return preview(JoinMeRequest.of(creator));
    }

    CompletableFuture<List<JoinMe>> active();

    CompletableFuture<List<JoinMe>> visibleTo(UUID viewer);

    CompletableFuture<Optional<JoinMe>> find(String id);

    default CompletableFuture<Optional<JoinMe>> byCreator(UUID creator) {
        return active().thenApply(all -> all.stream().filter(joinMe -> joinMe.creator().equals(creator)).findFirst());
    }

    CompletableFuture<JoinMeResult> join(UUID player, String id);

    CompletableFuture<Boolean> cancel(String id);

    default CompletableFuture<Boolean> cancelFor(UUID creator) {
        return byCreator(creator).thenCompose(found -> found.map(joinMe -> cancel(joinMe.id()))
                .orElseGet(() -> CompletableFuture.completedFuture(false)));
    }

    Subscription registerHandler(JoinMeScope scope, JoinMeHandler handler);

    default Subscription registerHandler(JoinMeHandler handler) {
        return registerHandler(JoinMeScope.server(), handler);
    }

    List<JoinMeHandler> handlers();
}
