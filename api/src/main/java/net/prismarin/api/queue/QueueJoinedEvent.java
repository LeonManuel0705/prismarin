package net.prismarin.api.queue;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public record QueueJoinedEvent(QueueTarget target, UUID player, List<UUID> party, int position, int size)
        implements PrismarinEvent {

    public QueueJoinedEvent {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(player, "player");
        party = List.copyOf(party);
    }
}
