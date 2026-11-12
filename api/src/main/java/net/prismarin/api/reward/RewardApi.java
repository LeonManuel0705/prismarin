package net.prismarin.api.reward;

import java.util.Optional;

public final class RewardApi {

    private static volatile DailyRewardService daily;
    private static volatile VoteService votes;

    private RewardApi() {
        throw new AssertionError();
    }

    public static Optional<DailyRewardService> daily() {
        return Optional.ofNullable(daily);
    }

    public static Optional<VoteService> votes() {
        return Optional.ofNullable(votes);
    }

    public static RewardHandlers handlers() {
        return RewardHandlers.global();
    }

    public static void registerDaily(DailyRewardService service) {
        daily = service;
    }

    public static void registerVotes(VoteService service) {
        votes = service;
    }

    public static void unregister() {
        daily = null;
        votes = null;
    }
}
