package net.prismarin.core.nick;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.prismarin.api.nick.NickService;
import net.prismarin.shared.storage.DocumentStore;

public final class NickStore {

    static final String CLAIMS = "nick-claims";
    static final String OWNERS = "nick-owners";
    static final String SKINS = "nick-skins";

    private static final String KIND_FIELD = "auto";
    private static final String ROUND_KIND = "round";

    private final DocumentStore documents;

    public NickStore(DocumentStore documents) {
        this.documents = documents;
    }

    public Optional<Claim> claim(String name) {
        return documents.find(CLAIMS, key(name)).flatMap(NickStore::parseClaim);
    }

    public boolean claimedByOther(String name, UUID player, long now, long expiryMillis) {
        return claim(name)
                .filter(claim -> !claim.owner().equals(player))
                .filter(claim -> !claim.expired(now, expiryMillis))
                .isPresent();
    }

    public boolean claim(UUID player, NickIdentity identity, String server, long now, long expiryMillis) {
        if (claimedByOther(identity.name(), player, now, expiryMillis)) {
            return false;
        }
        writeClaim(player, identity, server, now);
        boolean won = claim(identity.name()).map(claim -> claim.owner().equals(player)).orElse(false);
        if (won) {
            writeOwner(player, identity, now);
        }
        return won;
    }

    public void touch(UUID player, NickIdentity identity, String server, long now) {
        writeClaim(player, identity, server, now);
        writeOwner(player, identity, now);
    }

    public void release(UUID player, String name) {
        releaseClaim(player, name);
        documents.delete(OWNERS, player.toString());
    }

    public void releaseClaim(UUID player, String name) {
        if (name != null && claim(name).map(claim -> claim.owner().equals(player)).orElse(false)) {
            documents.delete(CLAIMS, key(name));
        }
    }

    public Optional<NickIdentity> owner(UUID player) {
        return documents.find(OWNERS, player.toString()).flatMap(json -> object(json)
                .flatMap(object -> string(object, "name").map(name -> new NickIdentity(name,
                        string(object, "skin").orElse(NickIdentity.OWN_SKIN), kind(object)))));
    }

    public Optional<NickIdentity> resolve(UUID player, Map<String, String> settings, long now, long expiryMillis) {
        Optional<NickIdentity> owned = owner(player);
        if (owned.isPresent()) {
            if (claimedByOther(owned.get().name(), player, now, expiryMillis)) {
                documents.delete(OWNERS, player.toString());
                return Optional.empty();
            }
            return owned;
        }
        String name = settings.get(NickService.SETTING_NAME);
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        Optional<Claim> claim = claim(name);
        if (claim.isEmpty() || !claim.get().owner().equals(player)) {
            return Optional.empty();
        }
        NickIdentity identity = new NickIdentity(claim.get().name(), claim.get().skin(), claim.get().kind());
        writeOwner(player, identity, claim.get().updated());
        return Optional.of(identity);
    }

    public Optional<StoredSkin> skin(String name) {
        return documents.find(SKINS, key(name)).flatMap(json -> object(json).flatMap(object -> {
            Optional<String> value = string(object, "value");
            Optional<String> signature = string(object, "signature");
            if (value.isEmpty() || signature.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new StoredSkin(string(object, "name").orElse(name), value.get(), signature.get(),
                    number(object, "fetched")));
        }));
    }

    public void saveSkin(StoredSkin skin) {
        JsonObject object = new JsonObject();
        object.addProperty("name", skin.name());
        object.addProperty("value", skin.value());
        object.addProperty("signature", skin.signature());
        object.addProperty("fetched", skin.fetched());
        documents.save(SKINS, key(skin.name()), object.toString());
    }

    private void writeClaim(UUID player, NickIdentity identity, String server, long now) {
        JsonObject claim = new JsonObject();
        claim.addProperty("uuid", player.toString());
        claim.addProperty("name", identity.name());
        claim.addProperty("skin", identity.skin());
        addKind(claim, identity.kind());
        claim.addProperty("server", server);
        claim.addProperty("updated", now);
        documents.save(CLAIMS, key(identity.name()), claim.toString());
    }

    private void writeOwner(UUID player, NickIdentity identity, long now) {
        JsonObject owner = new JsonObject();
        owner.addProperty("name", identity.name());
        owner.addProperty("skin", identity.skin());
        addKind(owner, identity.kind());
        owner.addProperty("updated", now);
        documents.save(OWNERS, player.toString(), owner.toString());
    }

    private static Optional<Claim> parseClaim(String json) {
        return object(json).flatMap(object -> {
            Optional<String> uuid = string(object, "uuid");
            Optional<String> name = string(object, "name");
            if (uuid.isEmpty() || name.isEmpty()) {
                return Optional.empty();
            }
            try {
                return Optional.of(new Claim(UUID.fromString(uuid.get()), name.get(),
                        string(object, "skin").orElse(NickIdentity.OWN_SKIN), kind(object),
                        string(object, "server").orElse(""), number(object, "updated")));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        });
    }

    private static Optional<JsonObject> object(String json) {
        try {
            JsonElement element = JsonParser.parseString(json);
            return element.isJsonObject() ? Optional.of(element.getAsJsonObject()) : Optional.empty();
        } catch (JsonParseException e) {
            return Optional.empty();
        }
    }

    private static Optional<String> string(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive()) {
            return Optional.empty();
        }
        String value = element.getAsString();
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static void addKind(JsonObject object, NickIdentity.Kind kind) {
        switch (kind) {
            case MANUAL -> object.addProperty(KIND_FIELD, false);
            case AUTO -> object.addProperty(KIND_FIELD, true);
            case ROUND -> object.addProperty(KIND_FIELD, ROUND_KIND);
        }
    }

    private static NickIdentity.Kind kind(JsonObject object) {
        JsonElement element = object.get(KIND_FIELD);
        if (element == null || !element.isJsonPrimitive()) {
            return NickIdentity.Kind.MANUAL;
        }
        if (element.getAsJsonPrimitive().isBoolean()) {
            return element.getAsBoolean() ? NickIdentity.Kind.AUTO : NickIdentity.Kind.MANUAL;
        }
        return ROUND_KIND.equals(element.getAsString()) ? NickIdentity.Kind.ROUND : NickIdentity.Kind.MANUAL;
    }

    private static long number(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return 0L;
        }
        return element.getAsLong();
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public record Claim(UUID owner, String name, String skin, NickIdentity.Kind kind, String server, long updated) {

        public boolean expired(long now, long expiryMillis) {
            return now - updated >= expiryMillis;
        }
    }

    public record StoredSkin(String name, String value, String signature, long fetched) {

        public boolean fresh(long now, long maxAgeMillis) {
            return now - fetched < maxAgeMillis;
        }
    }
}
