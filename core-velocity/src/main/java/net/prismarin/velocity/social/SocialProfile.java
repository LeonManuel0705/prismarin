package net.prismarin.velocity.social;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record SocialProfile(UUID uuid, Map<UUID, Long> friends, Map<UUID, Long> incoming, Map<UUID, Long> outgoing,
                            Map<String, String> settings) {

    public SocialProfile {
        Objects.requireNonNull(uuid, "uuid");
        friends = Collections.unmodifiableMap(new LinkedHashMap<>(friends));
        incoming = Collections.unmodifiableMap(new LinkedHashMap<>(incoming));
        outgoing = Collections.unmodifiableMap(new LinkedHashMap<>(outgoing));
        settings = Collections.unmodifiableMap(new LinkedHashMap<>(settings));
    }

    public static SocialProfile empty(UUID uuid) {
        return new SocialProfile(uuid, Map.of(), Map.of(), Map.of(), Map.of());
    }

    public boolean friendsWith(UUID other) {
        return friends.containsKey(other);
    }

    public Set<UUID> friendIds() {
        return friends.keySet();
    }

    public String setting(SocialSetting setting, String fallback) {
        String stored = settings.get(setting.id());
        return setting.normalize(stored).orElse(setting.normalize(fallback).orElse(setting.fallback()));
    }

    public SocialProfile withFriend(UUID other, long since) {
        Map<UUID, Long> nextFriends = new LinkedHashMap<>(friends);
        nextFriends.put(other, since);
        Map<UUID, Long> nextIncoming = new LinkedHashMap<>(incoming);
        nextIncoming.remove(other);
        Map<UUID, Long> nextOutgoing = new LinkedHashMap<>(outgoing);
        nextOutgoing.remove(other);
        return new SocialProfile(uuid, nextFriends, nextIncoming, nextOutgoing, settings);
    }

    public SocialProfile withoutFriend(UUID other) {
        Map<UUID, Long> next = new LinkedHashMap<>(friends);
        next.remove(other);
        return new SocialProfile(uuid, next, incoming, outgoing, settings);
    }

    public SocialProfile withIncoming(UUID from, long at) {
        Map<UUID, Long> next = new LinkedHashMap<>(incoming);
        next.put(from, at);
        return new SocialProfile(uuid, friends, next, outgoing, settings);
    }

    public SocialProfile withoutIncoming(UUID from) {
        Map<UUID, Long> next = new LinkedHashMap<>(incoming);
        next.remove(from);
        return new SocialProfile(uuid, friends, next, outgoing, settings);
    }

    public SocialProfile withOutgoing(UUID to, long at) {
        Map<UUID, Long> next = new LinkedHashMap<>(outgoing);
        next.put(to, at);
        return new SocialProfile(uuid, friends, incoming, next, settings);
    }

    public SocialProfile withoutOutgoing(UUID to) {
        Map<UUID, Long> next = new LinkedHashMap<>(outgoing);
        next.remove(to);
        return new SocialProfile(uuid, friends, incoming, next, settings);
    }

    public SocialProfile withSetting(SocialSetting setting, String value) {
        Map<String, String> next = new LinkedHashMap<>(settings);
        next.put(setting.id(), value);
        return new SocialProfile(uuid, friends, incoming, outgoing, next);
    }

    public SocialProfile pruned(long now, long expiryMillis) {
        if (expiryMillis <= 0) {
            return this;
        }
        Map<UUID, Long> nextIncoming = new LinkedHashMap<>(incoming);
        nextIncoming.values().removeIf(at -> FriendRules.expired(at, now, expiryMillis));
        Map<UUID, Long> nextOutgoing = new LinkedHashMap<>(outgoing);
        nextOutgoing.values().removeIf(at -> FriendRules.expired(at, now, expiryMillis));
        if (nextIncoming.size() == incoming.size() && nextOutgoing.size() == outgoing.size()) {
            return this;
        }
        return new SocialProfile(uuid, friends, nextIncoming, nextOutgoing, settings);
    }

    public String toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("uuid", uuid.toString());
        object.add("friends", times(friends));
        object.add("incoming", times(incoming));
        object.add("outgoing", times(outgoing));
        JsonObject values = new JsonObject();
        settings.forEach(values::addProperty);
        object.add("settings", values);
        return object.toString();
    }

    public static SocialProfile fromJson(UUID uuid, String json) {
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) {
                return empty(uuid);
            }
            JsonObject object = element.getAsJsonObject();
            Map<String, String> settings = new LinkedHashMap<>();
            child(object, "settings").ifPresent(values -> values.entrySet().forEach(entry -> {
                if (entry.getValue().isJsonPrimitive()) {
                    settings.put(entry.getKey(), entry.getValue().getAsString());
                }
            }));
            return new SocialProfile(uuid, readTimes(object, "friends"), readTimes(object, "incoming"),
                    readTimes(object, "outgoing"), settings);
        } catch (JsonParseException | IllegalStateException e) {
            return empty(uuid);
        }
    }

    private static JsonObject times(Map<UUID, Long> values) {
        JsonObject object = new JsonObject();
        values.forEach((key, value) -> object.addProperty(key.toString(), value));
        return object;
    }

    private static Map<UUID, Long> readTimes(JsonObject object, String field) {
        Map<UUID, Long> values = new LinkedHashMap<>();
        child(object, field).ifPresent(child -> child.entrySet().forEach(entry -> {
            try {
                if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()) {
                    values.put(UUID.fromString(entry.getKey()), entry.getValue().getAsLong());
                }
            } catch (IllegalArgumentException ignored) {
                return;
            }
        }));
        return values;
    }

    private static Optional<JsonObject> child(JsonObject object, String field) {
        JsonElement element = object.get(field);
        return element != null && element.isJsonObject() ? Optional.of(element.getAsJsonObject()) : Optional.empty();
    }
}
