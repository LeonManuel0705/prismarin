package net.prismarin.api.reward;

import java.util.Objects;

public record DailyClaim(Result result, DailyStatus status, Reward reward, int day, boolean milestone,
                         int lostStreak) {

    public enum Result {
        CLAIMED,
        ALREADY_CLAIMED
    }

    public DailyClaim {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(status, "status");
        reward = reward == null ? Reward.EMPTY : reward;
    }

    public boolean claimed() {
        return result == Result.CLAIMED;
    }

    public boolean streakBroken() {
        return lostStreak > 0;
    }
}
