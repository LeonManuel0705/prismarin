package net.prismarin.api.reward;

import java.util.Objects;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public record DailyStreakBrokenEvent(UUID player, int lostStreak, int missedDays) implements PrismarinEvent {

    public DailyStreakBrokenEvent {
        Objects.requireNonNull(player, "player");
    }
}
