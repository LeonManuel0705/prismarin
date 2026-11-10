package net.prismarin.velocity.player;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import net.prismarin.shared.storage.DocumentStore;

public final class ProxyNickClaims {

    static final String COLLECTION = "nick-claims";
    private static final long MAX_AGE_MILLIS = 5_000L;

    private final DocumentStore documents;
    private final Executor executor;
    private final AtomicBoolean loading = new AtomicBoolean();
    private volatile Map<UUID, String> byOwner = Map.of();
    private volatile long loadedAt;

    public ProxyNickClaims(DocumentStore documents, Executor executor) {
        this.documents = documents;
        this.executor = executor;
    }

    public Optional<String> nickOf(UUID owner) {
        refreshSoon();
        return Optional.ofNullable(byOwner.get(owner));
    }

    public void refreshSoon() {
        if (System.currentTimeMillis() - loadedAt < MAX_AGE_MILLIS || !loading.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(this::load);
        } catch (RejectedExecutionException e) {
            loading.set(false);
        }
    }

    private void load() {
        try {
            read().ifPresent(claims -> byOwner = claims);
        } finally {
            loadedAt = System.currentTimeMillis();
            loading.set(false);
        }
    }

    private Optional<Map<UUID, String>> read() {
        try {
            return Optional.of(parse(documents.findAll(COLLECTION)));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    static Map<UUID, String> parse(Collection<String> claims) {
        Map<UUID, String> owners = new HashMap<>();
        for (String json : claims) {
            claim(json).ifPresent(claim -> owners.put(claim.getKey(), claim.getValue()));
        }
        return Map.copyOf(owners);
    }

    private static Optional<Map.Entry<UUID, String>> claim(String json) {
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) {
                return Optional.empty();
            }
            JsonObject object = element.getAsJsonObject();
            JsonElement uuid = object.get("uuid");
            JsonElement name = object.get("name");
            if (uuid == null || name == null || !uuid.isJsonPrimitive() || !name.isJsonPrimitive()
                    || name.getAsString().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(Map.entry(UUID.fromString(uuid.getAsString()), name.getAsString()));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
