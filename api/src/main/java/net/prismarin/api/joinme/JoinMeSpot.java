package net.prismarin.api.joinme;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.prismarin.api.server.GameState;

public record JoinMeSpot(UUID creator, String server, String group, String world, GameState state) {

    public JoinMeSpot {
        Objects.requireNonNull(creator, "creator");
        server = server == null ? "" : server;
        group = group == null ? "" : group.toLowerCase(Locale.ROOT);
        world = world == null ? "" : world;
        state = state == null ? GameState.UNKNOWN : state;
    }

    public boolean inWorld(String name) {
        return name != null && world.equalsIgnoreCase(name);
    }

    public boolean inGroup(String name) {
        return name != null && group.equalsIgnoreCase(name);
    }
}
