package net.prismarin.shared.player;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.prismarin.api.player.NetworkPlayer;
import net.prismarin.api.player.RankDisplay;
import net.prismarin.api.profile.PlayerProfile;

public final class PlayerSnapshots {

    private PlayerSnapshots() {
        throw new AssertionError();
    }

    public record StoredPresence(String name, String server, boolean online, long lastSeen) {

        public StoredPresence {
            name = name == null ? "" : name;
            server = server == null ? "" : server;
        }

        public static Optional<StoredPresence> fromJson(String json) {
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
                return Optional.of(new StoredPresence(name.getAsString(),
                        server != null && server.isJsonPrimitive() ? server.getAsString() : "",
                        online != null && online.isJsonPrimitive() && online.getAsBoolean(),
                        lastSeen != null && lastSeen.isJsonPrimitive() ? lastSeen.getAsLong() : 0L));
            } catch (RuntimeException e) {
                return Optional.empty();
            }
        }
    }

    public static Locale locale(String tag, Locale fallback) {
        if (tag == null || tag.isBlank()) {
            return fallback;
        }
        Locale parsed = Locale.forLanguageTag(tag.trim().replace('_', '-'));
        return parsed.getLanguage().isEmpty() ? fallback : parsed;
    }

    public static Locale locale(PlayerProfile profile, Locale fallback) {
        return locale(profile.getLocale(), fallback);
    }

    public static NetworkPlayer offline(PlayerProfile profile, Locale fallback) {
        return new NetworkPlayer(profile.getUuid(), profile.getName(), "", locale(profile, fallback), "", "", false,
                false, RankDisplay.NONE, RankDisplay.NONE, profile.getLevel(), millis(profile.getLastSeen()),
                NetworkPlayer.Source.STORAGE);
    }

    public static NetworkPlayer live(PlayerWire.Live live, Optional<PlayerProfile> profile, Locale fallback, long now,
                                     NetworkPlayer.Source source) {
        String name = !live.name().isEmpty() ? live.name() : profile.map(PlayerProfile::getName).orElse("");
        Locale stored = profile.map(found -> locale(found, fallback)).orElse(fallback);
        return new NetworkPlayer(live.uuid(), name, live.nick(), locale(live.locale(), stored), live.server(),
                live.world(), true, live.vanished(), RankDisplay.NONE, RankDisplay.NONE,
                profile.map(PlayerProfile::getLevel).orElse(0), now, source);
    }

    public static Optional<NetworkPlayer> stored(UUID uuid, Optional<StoredPresence> presence, Optional<String> nick,
                                                 Optional<PlayerProfile> profile, Locale fallback) {
        if (presence.isEmpty() && profile.isEmpty()) {
            return Optional.empty();
        }
        boolean online = presence.map(StoredPresence::online).orElse(false);
        String name = presence.map(StoredPresence::name).filter(value -> !value.isBlank())
                .or(() -> profile.map(PlayerProfile::getName))
                .orElse("");
        long lastSeen = Math.max(presence.map(StoredPresence::lastSeen).orElse(0L),
                profile.map(found -> millis(found.getLastSeen())).orElse(0L));
        return Optional.of(new NetworkPlayer(uuid, name, nick.orElse(""),
                profile.map(found -> locale(found, fallback)).orElse(fallback),
                presence.map(StoredPresence::server).orElse(""), "", online, false, RankDisplay.NONE,
                RankDisplay.NONE, profile.map(PlayerProfile::getLevel).orElse(0), lastSeen,
                NetworkPlayer.Source.STORAGE));
    }

    private static long millis(Instant instant) {
        return instant == null ? 0L : instant.toEpochMilli();
    }
}
