package net.prismarin.api.reward;

import java.util.Objects;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public record VoteRewardGivenEvent(UUID player, Vote vote) implements PrismarinEvent {

    public VoteRewardGivenEvent {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(vote, "vote");
    }
}
