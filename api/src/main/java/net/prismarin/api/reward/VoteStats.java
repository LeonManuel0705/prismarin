package net.prismarin.api.reward;

import java.util.Objects;
import java.util.UUID;

public record VoteStats(UUID player, long total, int streak, int bestStreak, long lastVote, int pending) {

    public VoteStats {
        Objects.requireNonNull(player, "player");
    }

    public static VoteStats none(UUID player) {
        return new VoteStats(player, 0L, 0, 0, 0L, 0);
    }
}
