package net.prismarin.api.queue;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public record QueueSentEvent(QueueTarget target, String server, UUID player, List<UUID> party, long waitedMillis,
                             int size) implements PrismarinEvent {

    public QueueSentEvent {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(player, "player");
        party = List.copyOf(party);
    }
}
