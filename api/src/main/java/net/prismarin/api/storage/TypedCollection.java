package net.prismarin.api.storage;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class TypedCollection<T> {

    private static final Gson DEFAULT_GSON = new Gson();

    private final DataCollection raw;
    private final Class<T> type;
    private final Gson gson;

    private TypedCollection(DataCollection raw, Class<T> type, Gson gson) {
        this.raw = Objects.requireNonNull(raw, "raw");
        this.type = Objects.requireNonNull(type, "type");
        this.gson = Objects.requireNonNull(gson, "gson");
    }

    public static <T> TypedCollection<T> of(DataCollection raw, Class<T> type) {
        return new TypedCollection<>(raw, type, DEFAULT_GSON);
    }

    public static <T> TypedCollection<T> of(DataCollection raw, Class<T> type, Gson gson) {
        return new TypedCollection<>(raw, type, gson);
    }

    public DataCollection raw() {
        return raw;
    }

    public Class<T> type() {
        return type;
    }

    public CompletableFuture<Void> save(String id, T value) {
        return raw.save(id, encode(Objects.requireNonNull(value, "value")));
    }

    public CompletableFuture<Optional<T>> find(String id) {
        return raw.find(id).thenApply(found -> found.map(this::decode));
    }

    public CompletableFuture<Boolean> delete(String id) {
        return raw.delete(id);
    }

    public CompletableFuture<Map<String, T>> list() {
        return raw.list().thenApply(entries -> {
            Map<String, T> values = new LinkedHashMap<>();
            entries.forEach((id, json) -> values.put(id, decode(json)));
            return Collections.unmodifiableMap(values);
        });
    }

    public CompletableFuture<List<T>> findBy(String field, String value) {
        return raw.findBy(field, value).thenApply(found -> found.stream().map(this::decode).toList());
    }

    public CompletableFuture<List<String>> ids() {
        return raw.ids();
    }

    String encode(T value) {
        return gson.toJson(value, type);
    }

    T decode(String json) {
        T value = gson.fromJson(json, type);
        if (value == null) {
            throw new IllegalStateException("document in " + raw.namespace() + "/" + raw.name() + " is empty");
        }
        return value;
    }
}
