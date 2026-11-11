package net.prismarin.api.vanish;

import java.util.Objects;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public record VanishChangedEvent(UUID player, boolean vanished, int level, Cause cause) implements PrismarinEvent {

    public enum Cause {
        COMMAND,
        STAFF_MODE,
        JOIN,
        PLUGIN
    }

    public VanishChangedEvent {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(cause, "cause");
        level = vanished ? Math.max(1, level) : 0;
    }
}
