package net.prismarin.velocity.admin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.proxy.Player;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.prismarin.shared.storage.DocumentStore;

public final class NickDirectory {

    private static final String CLAIMS = "nick-claims";
    private static final long CACHE_MILLIS = 5_000L;

    private record Claim(UUID owner, String name) {
    }

    private final DocumentStore documents;
    private volatile Map<UUID, Claim> byOwner = Map.of();
    private volatile Map<String, Claim> byName = Map.of();
    private volatile long loadedAt;

    public NickDirectory(DocumentStore documents) {
        this.documents = documents;
    }

    public Optional<UUID> ownerOf(String nick) {
        refresh();
        return Optional.ofNullable(byName.get(nick.toLowerCase(Locale.ROOT))).map(Claim::owner);
    }

    public Optional<String> shownNick(Player player) {
        return shownNick(player.getUniqueId());
    }

    public Optional<String> shownNick(UUID owner) {
        refresh();
        return Optional.ofNullable(byOwner.get(owner)).map(Claim::name);
    }

    public String shownName(Player player) {
        return shownNick(player).orElse(player.getUsername());
    }

    private void refresh() {
        long now = System.currentTimeMillis();
        if (now - loadedAt < CACHE_MILLIS) {
            return;
        }
        loadedAt = now;
        Map<UUID, Claim> owners = new HashMap<>();
        Map<String, Claim> names = new HashMap<>();
        try {
            for (String json : documents.findAll(CLAIMS)) {
                JsonObject object = JsonParser.parseString(json).getAsJsonObject();
                if (!object.has("uuid") || !object.has("name")) {
                    continue;
                }
                Claim claim = new Claim(UUID.fromString(object.get("uuid").getAsString()),
                        object.get("name").getAsString());
                owners.put(claim.owner(), claim);
                names.put(claim.name().toLowerCase(Locale.ROOT), claim);
            }
        } catch (RuntimeException e) {
            return;
        }
        byOwner = Map.copyOf(owners);
        byName = Map.copyOf(names);
    }
}
