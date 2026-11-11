package net.prismarin.lobby.api;

import java.util.Objects;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public record LobbyLeftEvent(UUID player, String world, String nextWorld, Cause cause) implements PrismarinEvent {

    public enum Cause {
        QUIT,
        WORLD_CHANGE
    }

    public LobbyLeftEvent {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(cause, "cause");
        nextWorld = nextWorld == null ? "" : nextWorld;
    }
}
