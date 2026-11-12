package net.prismarin.shared.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class NetworkCalls {

    public static final String REQUEST = "network-request";
    public static final String ANSWER = "network-answer";
    public static final String EVENT = "network-event";
    public static final String BUS_REQUESTS = "prismarin:network";
    public static final String BUS_ANSWERS = "prismarin:network-answer";
    public static final int MAX_BYTES = 30_000;
    public static final int BODY_BUDGET = MAX_BYTES - 2_000;
    public static final int MAX_ERROR = 200;
    public static final UUID NOBODY = new UUID(0L, 0L);

    public enum Action {
        AUDIT_RECORD,
        AUDIT_LATEST,
        MAINTENANCE_STATE,
        MAINTENANCE_SET,
        STAFF_ALERT,
        STAFF_CHAT,
        BROADCAST,
        MODERATION_CATALOG,
        REPORT_FILE,
        REPORT_OPEN,
        REPORT_FIND,
        REPORT_CLAIM,
        REPORT_RELEASE,
        REPORT_CLOSE,
        REASON_REGISTER,
        REASON_UNREGISTER,
        TEMPLATE_REGISTER,
        TEMPLATE_UNREGISTER,
        TEMPLATE_PREVIEW,
        TEMPLATE_PUNISH,
        ALTS_FIND;

        public String id() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }

        public static Optional<Action> byId(String id) {
            if (id == null) {
                return Optional.empty();
            }
            for (Action action : values()) {
                if (action.id().equals(id)) {
                    return Optional.of(action);
                }
            }
            return Optional.empty();
        }
    }

    public record Request(int id, Action action, JsonObject body) {
    }

    public record Answer(int id, boolean ok, String error, JsonObject body) {
    }

    private NetworkCalls() {
        throw new AssertionError();
    }

    public static JsonObject request(int id, Action action, JsonObject body) {
        JsonObject message = new JsonObject();
        message.addProperty("id", id);
        message.addProperty("action", action.id());
        message.add("body", body == null ? new JsonObject() : body);
        return message;
    }

    public static Optional<Request> parseRequest(JsonObject message) {
        if (message == null || !fits(message)) {
            return Optional.empty();
        }
        int id = id(message);
        Optional<Action> action = Action.byId(text(message.get("action")));
        JsonElement body = message.get("body");
        if (id <= 0 || action.isEmpty() || (body != null && !body.isJsonObject())) {
            return Optional.empty();
        }
        return Optional.of(new Request(id, action.get(), body == null ? new JsonObject() : body.getAsJsonObject()));
    }

    public static JsonObject answer(int id, JsonObject body) {
        JsonObject message = new JsonObject();
        message.addProperty("id", id);
        message.addProperty("ok", true);
        message.add("body", body == null ? new JsonObject() : body);
        if (!fits(message)) {
            return failure(id, "the answer is too large");
        }
        return message;
    }

    public static JsonObject failure(int id, String error) {
        JsonObject message = new JsonObject();
        message.addProperty("id", id);
        message.addProperty("ok", false);
        String text = error == null || error.isBlank() ? "failed" : error;
        message.addProperty("error", text.length() > MAX_ERROR ? text.substring(0, MAX_ERROR) : text);
        return message;
    }

    public static Optional<Answer> parseAnswer(JsonObject message) {
        if (message == null) {
            return Optional.empty();
        }
        int id = id(message);
        if (id <= 0) {
            return Optional.empty();
        }
        JsonElement ok = message.get("ok");
        boolean success = ok != null && ok.isJsonPrimitive() && ok.getAsJsonPrimitive().isBoolean() && ok.getAsBoolean();
        JsonElement body = message.get("body");
        return Optional.of(new Answer(id, success, success ? "" : text(message.get("error")),
                body != null && body.isJsonObject() ? body.getAsJsonObject() : new JsonObject()));
    }

    public static int bytes(JsonElement element) {
        return element.toString().getBytes(StandardCharsets.UTF_8).length;
    }

    public static boolean fits(JsonElement element) {
        return bytes(element) <= MAX_BYTES;
    }

    public static JsonArray within(JsonArray items, int budget) {
        JsonArray kept = new JsonArray();
        int used = 2;
        for (JsonElement item : items) {
            int size = bytes(item) + 1;
            if (used + size > budget) {
                break;
            }
            kept.add(item);
            used += size;
        }
        return kept;
    }

    public static int id(JsonObject message) {
        JsonElement id = message.get("id");
        if (id == null || !id.isJsonPrimitive() || !id.getAsJsonPrimitive().isNumber()) {
            return 0;
        }
        try {
            return id.getAsInt();
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String text(JsonElement element) {
        return element == null || !element.isJsonPrimitive() ? "" : element.getAsString();
    }
}
