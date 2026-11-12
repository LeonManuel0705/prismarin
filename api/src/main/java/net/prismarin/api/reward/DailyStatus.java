package net.prismarin.api.reward;

import java.util.Objects;
import java.util.UUID;

public record DailyStatus(UUID player, int streak, int bestStreak, long claims, boolean claimable, long lastClaim,
                          long nextReset, int nextStreak) {

    public DailyStatus {
        Objects.requireNonNull(player, "player");
    }
}
