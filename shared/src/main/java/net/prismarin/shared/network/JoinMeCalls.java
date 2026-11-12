package net.prismarin.shared.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import net.prismarin.api.joinme.JoinMe;
import net.prismarin.api.joinme.JoinMeResult;
import net.prismarin.shared.joinme.JoinMeCodec;
import net.prismarin.shared.joinme.JoinMeDraft;
import net.prismarin.shared.joinme.JoinMeEndpoint;
import net.prismarin.shared.joinme.JoinMeTerms;

public final class JoinMeCalls {

    public static final String OFF = "joinme is off on the proxy";

    private final ProxyLink link;

    public JoinMeCalls(ProxyLink link) {
        this.link = link;
    }

    public CompletableFuture<JoinMeTerms> check(JoinMeDraft draft) {
        JsonObject body = new JsonObject();
        body.add("draft", JoinMeCodec.encode(draft));
        return link.callAs(draft.spot().creator(), NetworkCalls.Action.JOINME_CHECK, body)
                .thenApply(answer -> JoinMeCodec.terms(answer.get("terms")))
                .exceptionally(error -> JoinMeTerms.refused(unreachable(error)));
    }

    public CompletableFuture<JoinMeResult> create(JoinMeDraft draft) {
        JsonObject body = new JsonObject();
        body.add("draft", JoinMeCodec.encode(draft));
        return link.callAs(draft.spot().creator(), NetworkCalls.Action.JOINME_CREATE, body)
                .thenApply(answer -> JoinMeCodec.result(answer.get("result")))
                .exceptionally(error -> JoinMeResult.of(unreachable(error)));
    }

    public CompletableFuture<JoinMeResult> preview(JoinMeDraft draft) {
        JsonObject body = new JsonObject();
        body.add("draft", JoinMeCodec.encode(draft));
        return link.callAs(draft.spot().creator(), NetworkCalls.Action.JOINME_PREVIEW, body)
                .thenApply(answer -> JoinMeCodec.result(answer.get("result")))
                .exceptionally(error -> JoinMeResult.of(unreachable(error)));
    }

    public CompletableFuture<List<JoinMe>> list(UUID viewer) {
        JsonObject body = new JsonObject();
        if (viewer != null) {
            body.addProperty("viewer", viewer.toString());
        }
        return link.callAs(viewer, NetworkCalls.Action.JOINME_LIST, body)
                .thenApply(answer -> joinMes(answer.get("joinmes")));
    }

    public CompletableFuture<Optional<JoinMe>> find(String id) {
        if (!JoinMeCodec.validId(id)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        JsonObject body = new JsonObject();
        body.addProperty("id", id);
        return link.call(NetworkCalls.Action.JOINME_FIND, body)
                .thenApply(answer -> JoinMeCodec.joinMe(answer.get("joinme")));
    }

    public CompletableFuture<JoinMeResult> join(UUID player, String id) {
        if (!JoinMeCodec.validId(id)) {
            return CompletableFuture.completedFuture(JoinMeResult.of(JoinMeResult.Status.NOT_FOUND));
        }
        JsonObject body = new JsonObject();
        body.addProperty("player", player.toString());
        body.addProperty("id", id);
        return link.callAs(player, NetworkCalls.Action.JOINME_JOIN, body)
                .thenApply(answer -> JoinMeCodec.result(answer.get("result")))
                .exceptionally(error -> JoinMeResult.of(unreachable(error)));
    }

    public CompletableFuture<Boolean> cancel(String id, UUID creator) {
        JsonObject body = new JsonObject();
        if (id != null) {
            body.addProperty("id", id);
        }
        if (creator != null) {
            body.addProperty("creator", creator.toString());
        }
        return link.call(NetworkCalls.Action.JOINME_CANCEL, body).thenApply(answer -> {
            JsonElement cancelled = answer.get("cancelled");
            return cancelled != null && cancelled.isJsonPrimitive() && cancelled.getAsBoolean();
        });
    }

    public static CompletableFuture<JsonObject> dispatch(JoinMeEndpoint endpoint, NetworkCalls.Action action,
                                                         JsonObject body) {
        return switch (action) {
            case JOINME_CHECK -> endpoint.check(draft(body)).thenApply(terms -> single("terms",
                    JoinMeCodec.encode(terms)));
            case JOINME_CREATE -> endpoint.create(draft(body)).thenApply(result -> single("result",
                    JoinMeCodec.encode(result)));
            case JOINME_PREVIEW -> endpoint.preview(draft(body)).thenApply(result -> single("result",
                    JoinMeCodec.encode(result)));
            case JOINME_LIST -> endpoint.list(viewer(body)).thenApply(JoinMeCalls::listed);
            case JOINME_FIND -> endpoint.find(ApiJson.string(body, "id")).thenApply(found -> {
                JsonObject answer = new JsonObject();
                found.ifPresent(joinMe -> answer.add("joinme", JoinMeCodec.encode(joinMe)));
                return answer;
            });
            case JOINME_JOIN -> endpoint.join(ApiJson.uuid(body, "player"), ApiJson.string(body, "id"))
                    .thenApply(result -> single("result", JoinMeCodec.encode(result)));
            case JOINME_CANCEL -> endpoint.cancel(blankToNull(ApiJson.stringOr(body, "id", "")),
                    body.has("creator") ? ApiJson.uuid(body, "creator") : null).thenApply(cancelled -> {
                        JsonObject answer = new JsonObject();
                        answer.addProperty("cancelled", cancelled);
                        return answer;
                    });
            default -> throw new IllegalArgumentException("no JoinMe call");
        };
    }

    private static JoinMeDraft draft(JsonObject body) {
        return JoinMeCodec.draft(body.get("draft")).orElseThrow(() -> new IllegalArgumentException("invalid draft"));
    }

    private static UUID viewer(JsonObject body) {
        return body.has("viewer") ? ApiJson.uuid(body, "viewer") : null;
    }

    private static JsonObject listed(List<JoinMe> joinMes) {
        JsonArray all = new JsonArray();
        joinMes.forEach(joinMe -> all.add(JoinMeCodec.encode(joinMe)));
        JsonArray kept = NetworkCalls.within(all, NetworkCalls.BODY_BUDGET);
        JsonObject answer = new JsonObject();
        answer.add("joinmes", kept);
        answer.addProperty("truncated", kept.size() < all.size());
        return answer;
    }

    private static List<JoinMe> joinMes(JsonElement element) {
        List<JoinMe> found = new ArrayList<>();
        if (element != null && element.isJsonArray()) {
            element.getAsJsonArray().forEach(item -> JoinMeCodec.joinMe(item).ifPresent(found::add));
        }
        return List.copyOf(found);
    }

    private static JsonObject single(String key, JsonElement value) {
        JsonObject answer = new JsonObject();
        answer.add(key, value);
        return answer;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    static JoinMeResult.Status unreachable(Throwable error) {
        if (ProxyLink.noRoute(error)) {
            return JoinMeResult.Status.UNAVAILABLE;
        }
        Throwable cause = error;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof RemoteCallException && OFF.equals(cause.getMessage())) {
            return JoinMeResult.Status.DISABLED;
        }
        if (cause instanceof TimeoutException) {
            return JoinMeResult.Status.UNAVAILABLE;
        }
        return JoinMeResult.Status.FAILED;
    }
}
