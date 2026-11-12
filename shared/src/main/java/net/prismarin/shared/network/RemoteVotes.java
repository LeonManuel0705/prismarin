package net.prismarin.shared.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.reward.Vote;
import net.prismarin.api.reward.VoteService;
import net.prismarin.api.reward.VoteSite;
import net.prismarin.api.reward.VoteStats;

public final class RemoteVotes implements VoteService {

    private static final int MAX_ROUNDS = 50;

    private final ProxyLink link;

    public RemoteVotes(ProxyLink link) {
        this.link = link;
    }

    @Override
    public CompletableFuture<List<VoteSite>> sites() {
        return link.call(NetworkCalls.Action.VOTE_SITES, new JsonObject())
                .thenApply(answer -> ApiJson.list(answer.get("sites"), VoteSite.class));
    }

    @Override
    public CompletableFuture<Vote> vote(String name, String service) {
        if (name == null || name.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("a vote needs a player name"));
        }
        JsonObject body = new JsonObject();
        body.addProperty("name", name.strip());
        body.addProperty("service", service == null ? "" : service);
        return cast(body);
    }

    @Override
    public CompletableFuture<Vote> vote(UUID player, String service) {
        JsonObject body = new JsonObject();
        body.addProperty("player", Objects.requireNonNull(player, "player").toString());
        body.addProperty("service", service == null ? "" : service);
        return cast(body);
    }

    @Override
    public CompletableFuture<VoteStats> stats(UUID player) {
        return link.call(NetworkCalls.Action.VOTE_STATS, player(player))
                .thenApply(answer -> ApiJson.read(answer.get("stats"), VoteStats.class));
    }

    @Override
    public CompletableFuture<List<Vote>> pending(UUID player) {
        return link.call(NetworkCalls.Action.VOTE_PENDING, player(player))
                .thenApply(answer -> ApiJson.list(answer.get("votes"), Vote.class));
    }

    @Override
    public CompletableFuture<List<Vote>> claim(UUID player, String name, int limit) {
        Objects.requireNonNull(player, "player");
        return round(player, name == null ? "" : name, Math.max(0, limit), new ArrayList<>(), 0);
    }

    private CompletableFuture<List<Vote>> round(UUID player, String name, int limit, List<Vote> claimed, int rounds) {
        int wanted = Math.min(RemoteDispatcher.CLAIM_BATCH, limit - claimed.size());
        if (wanted <= 0 || rounds >= MAX_ROUNDS) {
            return CompletableFuture.completedFuture(List.copyOf(claimed));
        }
        JsonObject body = player(player);
        body.addProperty("name", name);
        body.addProperty("limit", wanted);
        return link.call(NetworkCalls.Action.VOTE_CLAIM, body).thenCompose(answer -> {
            List<Vote> batch = ApiJson.list(answer.get("votes"), Vote.class);
            claimed.addAll(batch);
            if (batch.isEmpty() || truncated(answer)) {
                return CompletableFuture.completedFuture(List.copyOf(claimed));
            }
            return round(player, name, limit, claimed, rounds + 1);
        });
    }

    private CompletableFuture<Vote> cast(JsonObject body) {
        return link.call(NetworkCalls.Action.VOTE_CAST, body)
                .thenApply(answer -> ApiJson.read(answer.get("vote"), Vote.class));
    }

    private static boolean truncated(JsonObject answer) {
        JsonElement value = answer.get("truncated");
        return value != null && value.isJsonPrimitive() && value.getAsBoolean();
    }

    private static JsonObject player(UUID player) {
        JsonObject body = new JsonObject();
        body.addProperty("player", Objects.requireNonNull(player, "player").toString());
        return body;
    }
}
