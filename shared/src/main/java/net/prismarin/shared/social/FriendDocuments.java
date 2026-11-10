package net.prismarin.shared.social;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.prismarin.api.social.FriendInfo;
import net.prismarin.shared.storage.DocumentStore;

public final class FriendDocuments {

    public static final String FRIENDS = "friends";
    public static final String PRESENCE = "presence";

    private final DocumentStore documents;

    public FriendDocuments(DocumentStore documents) {
        this.documents = documents;
    }

    public Map<UUID, Long> friendTimes(UUID player) {
        Map<UUID, Long> friends = new LinkedHashMap<>();
        Optional<JsonObject> profile = documents.find(FRIENDS, player.toString()).flatMap(FriendDocuments::object);
        if (profile.isEmpty()) {
            return friends;
        }
        JsonElement raw = profile.get().get("friends");
        if (raw == null || !raw.isJsonObject()) {
            return friends;
        }
        raw.getAsJsonObject().entrySet().forEach(entry -> {
            try {
                if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()) {
                    friends.put(UUID.fromString(entry.getKey()), entry.getValue().getAsLong());
                }
            } catch (IllegalArgumentException ignored) {
                return;
            }
        });
        return friends;
    }

    public boolean areFriends(UUID first, UUID second) {
        return !first.equals(second) && friendTimes(first).containsKey(second);
    }

    public Optional<String> knownName(UUID player) {
        return documents.find(PRESENCE, player.toString()).flatMap(FriendDocuments::object)
                .map(object -> object.get("name"))
                .filter(JsonElement::isJsonPrimitive)
                .map(JsonElement::getAsString)
                .filter(name -> !name.isBlank());
    }

    public List<FriendInfo> friends(UUID player, Function<UUID, Optional<String>> fallbackNames) {
        List<FriendInfo> friends = new ArrayList<>();
        friendTimes(player).forEach((uuid, since) -> {
            String name = knownName(uuid).or(() -> fallbackNames.apply(uuid)).orElse("");
            friends.add(new FriendInfo(uuid, name, since));
        });
        friends.sort(Comparator.comparing((FriendInfo info) -> info.name().toLowerCase(Locale.ROOT))
                .thenComparing(info -> info.uuid().toString()));
        return friends;
    }

    private static Optional<JsonObject> object(String json) {
        try {
            JsonElement element = JsonParser.parseString(json);
            return element.isJsonObject() ? Optional.of(element.getAsJsonObject()) : Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
