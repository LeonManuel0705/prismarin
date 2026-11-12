package net.prismarin.api.queue;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record QueuePosition(QueueTarget target, UUID owner, List<UUID> party, int position, int size, int players,
                            int priority, boolean paused, long joinedAt, boolean online) {

    public QueuePosition {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(owner, "owner");
        party = List.copyOf(party);
    }

    public boolean first() {
        return position == 1;
    }

    public int unitSize() {
        return party.size() + 1;
    }

    public boolean includes(UUID player) {
        return owner.equals(player) || party.contains(player);
    }
}
