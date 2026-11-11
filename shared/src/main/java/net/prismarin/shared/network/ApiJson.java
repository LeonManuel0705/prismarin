package net.prismarin.shared.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ApiJson {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Duration.class, new DurationAdapter().nullSafe())
            .disableHtmlEscaping()
            .create();

    private ApiJson() {
        throw new AssertionError();
    }

    public static JsonElement tree(Object value) {
        return GSON.toJsonTree(value);
    }

    public static <T> T read(JsonElement element, Class<T> type) {
        if (element == null || element.isJsonNull()) {
            throw new IllegalArgumentException("missing " + type.getSimpleName());
        }
        T value = GSON.fromJson(element, type);
        if (value == null) {
            throw new IllegalArgumentException("missing " + type.getSimpleName());
        }
        return value;
    }

    public static <T> Optional<T> optional(JsonElement element, Class<T> type) {
        return element == null || element.isJsonNull() ? Optional.empty() : Optional.of(read(element, type));
    }

    public static JsonArray array(Collection<?> values) {
        JsonArray array = new JsonArray();
        values.forEach(value -> array.add(tree(value)));
        return array;
    }

    public static <T> List<T> list(JsonElement element, Class<T> type) {
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }
        List<T> values = new ArrayList<>();
        element.getAsJsonArray().forEach(item -> values.add(read(item, type)));
        return List.copyOf(values);
    }

    public static String string(JsonObject body, String key) {
        String value = NetworkCalls.text(body.get(key));
        if (value.isEmpty()) {
            throw new IllegalArgumentException("missing " + key);
        }
        return value;
    }

    public static String stringOr(JsonObject body, String key, String fallback) {
        JsonElement value = body.get(key);
        return value == null || value.isJsonNull() ? fallback : NetworkCalls.text(value);
    }

    public static UUID uuid(JsonObject body, String key) {
        String value = string(body, key);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid " + key);
        }
    }

    public static boolean flag(JsonObject body, String key) {
        JsonElement value = body.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("missing " + key);
        }
        return value.getAsBoolean();
    }

    public static int number(JsonObject body, String key, int fallback) {
        JsonElement value = body.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        return value.getAsInt();
    }

    public static long longNumber(JsonObject body, String key, long fallback) {
        JsonElement value = body.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        return value.getAsLong();
    }

    private static final class DurationAdapter extends TypeAdapter<Duration> {

        @Override
        public void write(JsonWriter out, Duration value) throws IOException {
            out.value(value.toMillis());
        }

        @Override
        public Duration read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return Duration.ofMillis(in.nextLong());
        }
    }
}
