package net.prismarin.velocity.social;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.prismarin.shared.storage.DocumentStore;

public final class SocialStore {

    public static final String FRIENDS = "friends";
    public static final String PRESENCE = "presence";

    private static final long PROFILE_CACHE_MILLIS = 3_000L;
    private static final int PRESENCE_CACHE_LIMIT = 20_000;

    public record Presence(UUID uuid, String name, String server, boolean online, long lastSeen) {

        public String toJson() {
            JsonObject object = new JsonObject();
            object.addProperty("uuid", uuid.toString());
            object.addProperty("name", name);
            object.addProperty("server", server == null ? "" : server);
            object.addProperty("online", online);
            object.addProperty("last-seen", lastSeen);
            return object.toString();
        }

        public static Optional<Presence> fromJson(UUID uuid, String json) {
            try {
                JsonElement element = JsonParser.parseString(json);
                if (!element.isJsonObject()) {
                    return Optional.empty();
                }
                JsonObject object = element.getAsJsonObject();
                JsonElement name = object.get("name");
                if (name == null || !name.isJsonPrimitive()) {
                    return Optional.empty();
                }
                JsonElement server = object.get("server");
                JsonElement online = object.get("online");
                JsonElement lastSeen = object.get("last-seen");
                return Optional.of(new Presence(uuid, name.getAsString(),
                        server != null && server.isJsonPrimitive() ? server.getAsString() : "",
                        online != null && online.isJsonPrimitive() && online.getAsBoolean(),
                        lastSeen != null && lastSeen.isJsonPrimitive() ? lastSeen.getAsLong() : 0L));
            } catch (JsonParseException | IllegalStateException | NumberFormatException e) {
                return Optional.empty();
            }
        }
    }

    private record Cached(SocialProfile profile, long loadedAt) {
    }

    private final DocumentStore documents;
    private final Map<UUID, Cached> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, Presence> presence = new ConcurrentHashMap<>();
    private final Object lock = new Object();

    public SocialStore(DocumentStore documents) {
        this.documents = documents;
    }

    public Object lock() {
        return lock;
    }

    public SocialProfile load(UUID uuid) {
        SocialProfile profile = documents.find(FRIENDS, uuid.toString())
                .map(json -> SocialProfile.fromJson(uuid, json))
                .orElseGet(() -> SocialProfile.empty(uuid));
        profiles.put(uuid, new Cached(profile, System.currentTimeMillis()));
        return profile;
    }

    public SocialProfile cached(UUID uuid) {
        Cached cached = profiles.get(uuid);
        if (cached != null && System.currentTimeMillis() - cached.loadedAt() < PROFILE_CACHE_MILLIS) {
            return cached.profile();
        }
        return load(uuid);
    }

    public void save(SocialProfile profile) {
        documents.save(FRIENDS, profile.uuid().toString(), profile.toJson());
        profiles.put(profile.uuid(), new Cached(profile, System.currentTimeMillis()));
    }

    public void forget(UUID uuid) {
        profiles.remove(uuid);
    }

    public Optional<Presence> presence(UUID uuid) {
        Presence known = presence.get(uuid);
        if (known != null) {
            return Optional.of(known);
        }
        Optional<Presence> stored = documents.find(PRESENCE, uuid.toString())
                .flatMap(json -> Presence.fromJson(uuid, json));
        stored.ifPresent(this::remember);
        return stored;
    }

    public void savePresence(Presence entry) {
        remember(entry);
        documents.save(PRESENCE, entry.uuid().toString(), entry.toJson());
    }

    public int closeStalePresence(long now) {
        int closed = 0;
        for (String json : documents.findBy(PRESENCE, "online", "true")) {
            try {
                JsonObject object = JsonParser.parseString(json).getAsJsonObject();
                UUID uuid = UUID.fromString(object.get("uuid").getAsString());
                Optional<Presence> entry = Presence.fromJson(uuid, json);
                if (entry.isPresent()) {
                    Presence value = entry.get();
                    savePresence(new Presence(uuid, value.name(), "", false,
                            value.lastSeen() > 0 ? value.lastSeen() : now));
                    closed++;
                }
            } catch (RuntimeException ignored) {
                continue;
            }
        }
        return closed;
    }

    private void remember(Presence entry) {
        if (presence.size() >= PRESENCE_CACHE_LIMIT) {
            presence.clear();
        }
        presence.put(entry.uuid(), entry);
    }
}
