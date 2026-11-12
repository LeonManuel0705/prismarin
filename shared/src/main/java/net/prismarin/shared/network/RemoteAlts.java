package net.prismarin.shared.network;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.moderation.AltAccount;
import net.prismarin.api.moderation.AltService;

public final class RemoteAlts implements AltService {

    private final ProxyLink link;

    public RemoteAlts(ProxyLink link) {
        this.link = link;
    }

    @Override
    public CompletableFuture<List<AltAccount>> alts(UUID player) {
        JsonObject body = new JsonObject();
        body.addProperty("player", Objects.requireNonNull(player, "player").toString());
        return link.call(NetworkCalls.Action.ALTS_FIND, body)
                .thenApply(answer -> ApiJson.list(answer.get("alts"), AltAccount.class));
    }
}
