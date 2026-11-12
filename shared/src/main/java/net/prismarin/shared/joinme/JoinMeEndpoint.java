package net.prismarin.shared.joinme;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.joinme.JoinMe;
import net.prismarin.api.joinme.JoinMeResult;

public interface JoinMeEndpoint {

    CompletableFuture<JoinMeTerms> check(JoinMeDraft draft);

    CompletableFuture<JoinMeResult> create(JoinMeDraft draft);

    CompletableFuture<JoinMeResult> preview(JoinMeDraft draft);

    CompletableFuture<List<JoinMe>> list(UUID viewer);

    CompletableFuture<Optional<JoinMe>> find(String id);

    CompletableFuture<JoinMeResult> join(UUID player, String id);

    CompletableFuture<Boolean> cancel(String id, UUID creator);
}
