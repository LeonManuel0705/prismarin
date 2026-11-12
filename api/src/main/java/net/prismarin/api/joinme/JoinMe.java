package net.prismarin.api.joinme;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.prismarin.api.server.GameState;

public record JoinMe(String id, UUID creator, String creatorName, String shownName, String server, String group,
                     String world, GameState state, String handler, Map<String, String> placeholders,
                     JoinMeAudience audience, int maxJoins, int joins, long createdAt, long expiresAt) {

    public JoinMe {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(creator, "creator");
        creatorName = creatorName == null ? "" : creatorName;
        shownName = shownName == null || shownName.isBlank() ? creatorName : shownName;
        server = server == null ? "" : server;
        group = group == null ? "" : group;
        world = world == null ? "" : world;
        state = state == null ? GameState.UNKNOWN : state;
        handler = handler == null ? "" : handler;
        Map<String, String> copied = new LinkedHashMap<>();
        if (placeholders != null) {
            placeholders.forEach((key, value) -> {
                if (key != null && value != null) {
                    copied.put(key, value);
                }
            });
        }
        placeholders = Collections.unmodifiableMap(copied);
        audience = audience == null ? JoinMeAudience.NETWORK : audience;
        maxJoins = Math.max(0, maxJoins);
        joins = Math.max(0, joins);
    }

    public JoinMeSpot spot() {
        return new JoinMeSpot(creator, server, group, world, state);
    }

    public boolean expired(long now) {
        return now >= expiresAt;
    }

    public boolean usedUp() {
        return maxJoins > 0 && joins >= maxJoins;
    }

    public boolean open(long now) {
        return !expired(now) && !usedUp();
    }

    public long remainingMillis(long now) {
        return Math.max(0L, expiresAt - now);
    }

    public int joinsLeft() {
        return maxJoins <= 0 ? -1 : Math.max(0, maxJoins - joins);
    }

    public boolean handled() {
        return !handler.isEmpty();
    }

    public boolean nicked() {
        return !shownName.equals(creatorName);
    }

    public String name(boolean revealed) {
        return revealed ? creatorName : shownName;
    }

    public Optional<String> placeholder(String key) {
        return Optional.ofNullable(placeholders.get(key));
    }

    public JoinMe withJoins(int count) {
        return new JoinMe(id, creator, creatorName, shownName, server, group, world, state, handler, placeholders,
                audience, maxJoins, count, createdAt, expiresAt);
    }

    public JoinMe withWorld(String current) {
        return new JoinMe(id, creator, creatorName, shownName, server, group, current, state, handler, placeholders,
                audience, maxJoins, joins, createdAt, expiresAt);
    }
}
