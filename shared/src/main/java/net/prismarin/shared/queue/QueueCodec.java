package net.prismarin.shared.queue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.prismarin.api.queue.QueueEntry;
import net.prismarin.api.queue.QueueInfo;
import net.prismarin.api.queue.QueuePosition;
import net.prismarin.api.queue.QueueResult;
import net.prismarin.api.queue.QueueStatus;
import net.prismarin.api.queue.QueueTarget;

public final class QueueCodec {

    private QueueCodec() {
        throw new AssertionError();
    }

    public static Optional<QueueTarget> target(JsonElement element) {
        return QueueTarget.parse(text(element));
    }

    public static JsonObject position(QueuePosition position) {
        JsonObject json = new JsonObject();
        json.addProperty("target", position.target().id());
        json.addProperty("owner", position.owner().toString());
        json.add("party", uuids(position.party()));
        json.addProperty("position", position.position());
        json.addProperty("size", position.size());
        json.addProperty("players", position.players());
        json.addProperty("priority", position.priority());
        json.addProperty("paused", position.paused());
        json.addProperty("joined", position.joinedAt());
        json.addProperty("online", position.online());
        return json;
    }

    public static Optional<QueuePosition> position(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject json = element.getAsJsonObject();
        try {
            return target(json.get("target")).map(target -> new QueuePosition(target,
                    UUID.fromString(text(json.get("owner"))), uuids(json.get("party")), number(json, "position"),
                    number(json, "size"), number(json, "players"), number(json, "priority"), flag(json, "paused"),
                    longNumber(json, "joined"), flag(json, "online")));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static JsonObject entry(QueueEntry entry) {
        JsonObject json = new JsonObject();
        json.addProperty("owner", entry.owner().toString());
        json.addProperty("name", entry.name());
        json.add("party", uuids(entry.party()));
        json.addProperty("position", entry.position());
        json.addProperty("priority", entry.priority());
        json.addProperty("joined", entry.joinedAt());
        json.addProperty("online", entry.online());
        return json;
    }

    public static Optional<QueueEntry> entry(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject json = element.getAsJsonObject();
        try {
            return Optional.of(new QueueEntry(UUID.fromString(text(json.get("owner"))), text(json.get("name")),
                    uuids(json.get("party")), number(json, "position"), number(json, "priority"),
                    longNumber(json, "joined"), flag(json, "online")));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static JsonObject info(QueueInfo info) {
        JsonObject json = new JsonObject();
        json.addProperty("target", info.target().id());
        json.addProperty("display", info.displayName());
        json.addProperty("size", info.size());
        json.addProperty("players", info.players());
        json.addProperty("paused", info.paused());
        JsonArray entries = new JsonArray();
        info.entries().forEach(entry -> entries.add(entry(entry)));
        json.add("entries", entries);
        return json;
    }

    public static Optional<QueueInfo> info(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject json = element.getAsJsonObject();
        List<QueueEntry> entries = new ArrayList<>();
        JsonElement raw = json.get("entries");
        if (raw != null && raw.isJsonArray()) {
            raw.getAsJsonArray().forEach(item -> entry(item).ifPresent(entries::add));
        }
        return target(json.get("target")).map(target -> new QueueInfo(target, text(json.get("display")),
                number(json, "size"), number(json, "players"), flag(json, "paused"), entries));
    }

    public static JsonObject result(QueueResult result) {
        JsonObject json = new JsonObject();
        json.addProperty("status", result.status().id());
        result.server().ifPresent(server -> json.addProperty("server", server));
        result.position().ifPresent(position -> json.add("position", position(position)));
        return json;
    }

    public static QueueResult result(JsonObject json) {
        QueueStatus status = QueueStatus.byId(text(json.get("status"))).orElse(QueueStatus.FAILED);
        String server = text(json.get("server"));
        return new QueueResult(status, server.isEmpty() ? Optional.empty() : Optional.of(server),
                position(json.get("position")));
    }

    public static JsonArray uuids(List<UUID> values) {
        JsonArray array = new JsonArray();
        values.forEach(value -> array.add(value.toString()));
        return array;
    }

    public static List<UUID> uuids(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }
        List<UUID> values = new ArrayList<>();
        for (JsonElement item : element.getAsJsonArray()) {
            values.add(UUID.fromString(text(item)));
        }
        return List.copyOf(values);
    }

    static String text(JsonElement element) {
        return element == null || !element.isJsonPrimitive() ? "" : element.getAsString();
    }

    private static int number(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() ? 0
                : value.getAsInt();
    }

    private static long longNumber(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() ? 0L
                : value.getAsLong();
    }

    private static boolean flag(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()
                && value.getAsBoolean();
    }
}
