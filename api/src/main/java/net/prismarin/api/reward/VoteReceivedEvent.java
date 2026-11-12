package net.prismarin.api.reward;

import java.util.Objects;
import net.prismarin.api.event.PrismarinEvent;

public record VoteReceivedEvent(Vote vote) implements PrismarinEvent {

    public VoteReceivedEvent {
        Objects.requireNonNull(vote, "vote");
    }
}
