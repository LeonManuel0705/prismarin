package net.prismarin.api.level;

import java.util.Objects;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public record XpGainedEvent(UUID player, long amount, String reason, LevelProgress before, LevelProgress after)
        implements PrismarinEvent {

    public XpGainedEvent {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        reason = LevelService.reason(reason);
    }

    public boolean levelledUp() {
        return after.level() > before.level();
    }
}
