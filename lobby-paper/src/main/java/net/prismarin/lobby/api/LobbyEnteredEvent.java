package net.prismarin.lobby.api;

import java.util.Objects;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public record LobbyEnteredEvent(UUID player, String world, String previousWorld, Cause cause)
        implements PrismarinEvent {

    public enum Cause {
        JOIN,
        WORLD_CHANGE
    }

    public LobbyEnteredEvent {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(cause, "cause");
        previousWorld = previousWorld == null ? "" : previousWorld;
    }
}
