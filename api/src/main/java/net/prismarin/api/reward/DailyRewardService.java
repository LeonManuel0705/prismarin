package net.prismarin.api.reward;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface DailyRewardService {

    CompletableFuture<DailyStatus> status(UUID player);

    Optional<DailyStatus> cached(UUID player);

    CompletableFuture<DailyClaim> claim(UUID player);

    Reward reward(int streak);

    int cycle();
}
