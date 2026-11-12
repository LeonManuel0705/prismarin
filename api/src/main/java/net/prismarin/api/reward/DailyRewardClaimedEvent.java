package net.prismarin.api.reward;

import java.util.Objects;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public record DailyRewardClaimedEvent(UUID player, DailyClaim claim) implements PrismarinEvent {

    public DailyRewardClaimedEvent {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(claim, "claim");
    }
}
