package net.prismarin.api.level;

import java.util.Objects;
import java.util.UUID;

public record LevelChange(UUID player, LevelProgress before, LevelProgress after, long xpAdded, String reason) {

    public LevelChange {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        reason = LevelService.reason(reason);
    }

    public boolean levelChanged() {
        return before.level() != after.level();
    }

    public boolean levelledUp() {
        return after.level() > before.level();
    }

    public int levelsGained() {
        return after.level() - before.level();
    }
}
