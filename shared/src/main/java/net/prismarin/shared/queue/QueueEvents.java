package net.prismarin.shared.queue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Optional;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;
import net.prismarin.api.queue.QueueJoinedEvent;
import net.prismarin.api.queue.QueueLeftEvent;
import net.prismarin.api.queue.QueuePausedEvent;
import net.prismarin.api.queue.QueueSentEvent;
import net.prismarin.api.queue.QueueTarget;

public final class QueueEvents {

    public static final String PREFIX = "queue-";
    static final String JOINED = "queue-joined";
    static final String LEFT = "queue-left";
    static final String SENT = "queue-sent";
    static final String PAUSED = "queue-paused";

    private QueueEvents() {
        throw new AssertionError();
    }

    public static Optional<JsonObject> encode(PrismarinEvent event) {
        JsonObject message = new JsonObject();
        switch (event) {
            case QueueJoinedEvent joined -> {
                message.addProperty("event", JOINED);
                head(message, joined.target(), joined.player());
                message.add("party", QueueCodec.uuids(joined.party()));
                message.addProperty("position", joined.position());
                message.addProperty("size", joined.size());
            }
            case QueueLeftEvent left -> {
                message.addProperty("event", LEFT);
                head(message, left.target(), left.player());
                message.add("party", QueueCodec.uuids(left.party()));
                message.addProperty("reason", left.reason().id());
                message.addProperty("size", left.size());
            }
            case QueueSentEvent sent -> {
                message.addProperty("event", SENT);
                head(message, sent.target(), sent.player());
                message.add("party", QueueCodec.uuids(sent.party()));
                message.addProperty("server", sent.server());
                message.addProperty("waited", sent.waitedMillis());
                message.addProperty("size", sent.size());
            }
            case QueuePausedEvent paused -> {
                message.addProperty("event", PAUSED);
                message.addProperty("target", paused.target().id());
                message.addProperty("paused", paused.paused());
                message.addProperty("actor", paused.actor());
            }
            default -> {
                return Optional.empty();
            }
        }
        return Optional.of(message);
    }

    public static Optional<PrismarinEvent> decode(JsonObject message) {
        try {
            String event = QueueCodec.text(message.get("event"));
            if (!event.startsWith(PREFIX)) {
                return Optional.empty();
            }
            Optional<QueueTarget> target = QueueCodec.target(message.get("target"));
            if (target.isEmpty()) {
                return Optional.empty();
            }
            PrismarinEvent result = switch (event) {
                case JOINED -> new QueueJoinedEvent(target.get(), player(message),
                        QueueCodec.uuids(message.get("party")), number(message.get("position")),
                        number(message.get("size")));
                case LEFT -> new QueueLeftEvent(target.get(), player(message), QueueCodec.uuids(message.get("party")),
                        QueueLeftEvent.Reason.byId(QueueCodec.text(message.get("reason")))
                                .orElse(QueueLeftEvent.Reason.LEFT), number(message.get("size")));
                case SENT -> new QueueSentEvent(target.get(), QueueCodec.text(message.get("server")), player(message),
                        QueueCodec.uuids(message.get("party")), longNumber(message.get("waited")),
                        number(message.get("size")));
                case PAUSED -> new QueuePausedEvent(target.get(), message.has("paused")
                        && message.get("paused").getAsBoolean(), QueueCodec.text(message.get("actor")));
                default -> null;
            };
            return Optional.ofNullable(result);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static void head(JsonObject message, QueueTarget target, UUID player) {
        message.addProperty("target", target.id());
        message.addProperty("player", player.toString());
    }

    private static UUID player(JsonObject message) {
        return UUID.fromString(QueueCodec.text(message.get("player")));
    }

    private static int number(JsonElement element) {
        return element == null || !element.isJsonPrimitive() ? 0 : element.getAsInt();
    }

    private static long longNumber(JsonElement element) {
        return element == null || !element.isJsonPrimitive() ? 0L : element.getAsLong();
    }
}
