package net.prismarin.api.level;

import java.util.Objects;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public record LevelChangedEvent(UUID player, int previousLevel, int level, String reason) implements PrismarinEvent {

    public LevelChangedEvent {
        Objects.requireNonNull(player, "player");
        reason = LevelService.reason(reason);
    }

    public boolean levelledUp() {
        return level > previousLevel;
    }

    public int difference() {
        return level - previousLevel;
    }
}
