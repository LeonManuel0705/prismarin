package net.prismarin.shared.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.prismarin.api.queue.QueueInfo;
import net.prismarin.api.queue.QueuePosition;
import net.prismarin.api.queue.QueueResult;
import net.prismarin.api.queue.QueueService;
import net.prismarin.api.queue.QueueStatus;
import net.prismarin.api.queue.QueueTarget;
import net.prismarin.shared.queue.QueueCodec;

public final class QueueCalls implements QueueService {

    static final int ENTRY_BUDGET = NetworkCalls.BODY_BUDGET - 1_000;

    private final ProxyLink link;

    public QueueCalls(ProxyLink link) {
        this.link = link;
    }

    @Override
    public CompletableFuture<QueueResult> join(UUID player, QueueTarget target) {
        JsonObject body = player(player);
        body.addProperty("target", target.id());
        return link.call(NetworkCalls.Action.QUEUE_JOIN, body)
                .thenApply(QueueCodec::result)
                .exceptionally(error -> QueueResult.of(refused(error) ? QueueStatus.DISABLED : QueueStatus.FAILED));
    }

    @Override
    public CompletableFuture<Boolean> leave(UUID player) {
        return link.call(NetworkCalls.Action.QUEUE_LEAVE, player(player)).thenApply(answer -> flag(answer, "left"));
    }

    @Override
    public CompletableFuture<Boolean> leave(UUID player, QueueTarget target) {
        JsonObject body = player(player);
        body.addProperty("target", target.id());
        return link.call(NetworkCalls.Action.QUEUE_LEAVE, body).thenApply(answer -> flag(answer, "left"));
    }

    @Override
    public CompletableFuture<List<QueuePosition>> positions(UUID player) {
        return link.call(NetworkCalls.Action.QUEUE_POSITIONS, player(player)).thenApply(answer -> {
            List<QueuePosition> positions = new ArrayList<>();
            JsonElement raw = answer.get("positions");
            if (raw != null && raw.isJsonArray()) {
                raw.getAsJsonArray().forEach(item -> QueueCodec.position(item).ifPresent(positions::add));
            }
            return List.copyOf(positions);
        });
    }

    @Override
    public CompletableFuture<List<QueueInfo>> queues() {
        return link.call(NetworkCalls.Action.QUEUE_LIST, new JsonObject()).thenApply(answer -> {
            List<QueueInfo> infos = new ArrayList<>();
            JsonElement raw = answer.get("queues");
            if (raw != null && raw.isJsonArray()) {
                raw.getAsJsonArray().forEach(item -> QueueCodec.info(item).ifPresent(infos::add));
            }
            return List.copyOf(infos);
        });
    }

    @Override
    public CompletableFuture<Optional<QueueInfo>> queue(QueueTarget target) {
        JsonObject body = new JsonObject();
        body.addProperty("target", target.id());
        return link.call(NetworkCalls.Action.QUEUE_LIST, body).thenApply(answer -> QueueCodec.info(answer.get("queue")));
    }

    @Override
    public CompletableFuture<Boolean> pause(QueueTarget target, String actor) {
        return manage("pause", target, actor).thenApply(answer -> flag(answer, "changed"));
    }

    @Override
    public CompletableFuture<Boolean> resume(QueueTarget target, String actor) {
        return manage("resume", target, actor).thenApply(answer -> flag(answer, "changed"));
    }

    @Override
    public CompletableFuture<Integer> clear(QueueTarget target) {
        return manage("clear", target, "").thenApply(answer -> ApiJson.number(answer, "count", 0));
    }

    @Override
    public CompletableFuture<Optional<UUID>> sendNext(QueueTarget target) {
        return manage("send", target, "").thenApply(answer -> {
            String player = ApiJson.stringOr(answer, "player", "");
            return player.isEmpty() ? Optional.<UUID>empty() : Optional.of(UUID.fromString(player));
        });
    }

    @Override
    public CompletableFuture<Boolean> joinable(String server, boolean joinable) {
        JsonObject body = new JsonObject();
        body.addProperty("op", "joinable");
        body.addProperty("server", server);
        body.addProperty("joinable", joinable);
        return link.call(NetworkCalls.Action.QUEUE_MANAGE, body).thenApply(answer -> flag(answer, "changed"));
    }

    @Override
    public CompletableFuture<Set<String>> closedServers() {
        JsonObject body = new JsonObject();
        body.addProperty("op", "closed");
        return link.call(NetworkCalls.Action.QUEUE_MANAGE, body).thenApply(answer -> {
            Set<String> servers = new LinkedHashSet<>();
            JsonElement raw = answer.get("servers");
            if (raw != null && raw.isJsonArray()) {
                raw.getAsJsonArray().forEach(item -> servers.add(NetworkCalls.text(item)));
            }
            return Set.copyOf(servers);
        });
    }

    private CompletableFuture<JsonObject> manage(String op, QueueTarget target, String actor) {
        JsonObject body = new JsonObject();
        body.addProperty("op", op);
        body.addProperty("target", target.id());
        body.addProperty("actor", actor == null ? "" : actor);
        return link.call(NetworkCalls.Action.QUEUE_MANAGE, body);
    }

    public static CompletableFuture<JsonObject> dispatch(QueueService queue, NetworkCalls.Action action,
                                                         JsonObject body) {
        return switch (action) {
            case QUEUE_JOIN -> queue.join(ApiJson.uuid(body, "player"), target(body)).thenApply(QueueCodec::result);
            case QUEUE_LEAVE -> (body.has("target") ? queue.leave(ApiJson.uuid(body, "player"), target(body))
                    : queue.leave(ApiJson.uuid(body, "player"))).thenApply(left -> flagged("left", left));
            case QUEUE_POSITIONS -> queue.positions(ApiJson.uuid(body, "player")).thenApply(positions -> {
                JsonArray array = new JsonArray();
                positions.forEach(position -> array.add(QueueCodec.position(position)));
                JsonObject answer = new JsonObject();
                answer.add("positions", NetworkCalls.within(array, NetworkCalls.BODY_BUDGET));
                return answer;
            });
            case QUEUE_LIST -> body.has("target") ? queue.queue(target(body)).thenApply(QueueCalls::single)
                    : queue.queues().thenApply(QueueCalls::listed);
            case QUEUE_MANAGE -> manage(queue, body);
            default -> throw new IllegalArgumentException("no queue call");
        };
    }

    private static CompletableFuture<JsonObject> manage(QueueService queue, JsonObject body) {
        String op = ApiJson.string(body, "op");
        return switch (op) {
            case "pause" -> queue.pause(target(body), ApiJson.stringOr(body, "actor", ""))
                    .thenApply(changed -> flagged("changed", changed));
            case "resume" -> queue.resume(target(body), ApiJson.stringOr(body, "actor", ""))
                    .thenApply(changed -> flagged("changed", changed));
            case "clear" -> queue.clear(target(body)).thenApply(count -> {
                JsonObject answer = new JsonObject();
                answer.addProperty("count", count);
                return answer;
            });
            case "send" -> queue.sendNext(target(body)).thenApply(player -> {
                JsonObject answer = new JsonObject();
                player.ifPresent(found -> answer.addProperty("player", found.toString()));
                return answer;
            });
            case "joinable" -> queue.joinable(ApiJson.string(body, "server"), ApiJson.flag(body, "joinable"))
                    .thenApply(changed -> flagged("changed", changed));
            case "closed" -> queue.closedServers().thenApply(servers -> {
                JsonArray array = new JsonArray();
                servers.stream().sorted().forEach(array::add);
                JsonObject answer = new JsonObject();
                answer.add("servers", NetworkCalls.within(array, NetworkCalls.BODY_BUDGET));
                return answer;
            });
            default -> throw new IllegalArgumentException("unknown queue action");
        };
    }

    private static JsonObject single(Optional<QueueInfo> info) {
        JsonObject answer = new JsonObject();
        info.ifPresent(found -> {
            JsonObject json = QueueCodec.info(found);
            json.add("entries", NetworkCalls.within(json.getAsJsonArray("entries"), ENTRY_BUDGET));
            answer.add("queue", json);
        });
        return answer;
    }

    private static JsonObject listed(List<QueueInfo> infos) {
        JsonArray all = new JsonArray();
        for (QueueInfo info : infos) {
            JsonObject json = QueueCodec.info(info);
            json.add("entries", new JsonArray());
            all.add(json);
        }
        JsonArray kept = NetworkCalls.within(all, NetworkCalls.BODY_BUDGET);
        JsonObject answer = new JsonObject();
        answer.add("queues", kept);
        answer.addProperty("truncated", kept.size() < all.size());
        return answer;
    }

    private static QueueTarget target(JsonObject body) {
        return QueueTarget.parse(ApiJson.string(body, "target"))
                .orElseThrow(() -> new IllegalArgumentException("invalid target"));
    }

    private static JsonObject player(UUID player) {
        JsonObject body = new JsonObject();
        body.addProperty("player", player.toString());
        return body;
    }

    private static JsonObject flagged(String key, boolean value) {
        JsonObject answer = new JsonObject();
        answer.addProperty(key, value);
        return answer;
    }

    private static boolean flag(JsonObject answer, String key) {
        JsonElement value = answer.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()
                && value.getAsBoolean();
    }

    static boolean refused(Throwable error) {
        Throwable cause = error;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause instanceof RemoteCallException;
    }
}
