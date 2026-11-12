package net.prismarin.api.reward;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface VoteService {

    CompletableFuture<List<VoteSite>> sites();

    CompletableFuture<Vote> vote(String name, String service);

    CompletableFuture<Vote> vote(UUID player, String service);

    CompletableFuture<VoteStats> stats(UUID player);

    CompletableFuture<List<Vote>> pending(UUID player);

    CompletableFuture<List<Vote>> claim(UUID player, String name, int limit);

    default CompletableFuture<List<Vote>> claim(UUID player, String name) {
        return claim(player, name, Integer.MAX_VALUE);
    }
}
