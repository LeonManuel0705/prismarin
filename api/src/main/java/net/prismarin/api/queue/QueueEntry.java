package net.prismarin.api.queue;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record QueueEntry(UUID owner, String name, List<UUID> party, int position, int priority, long joinedAt,
                         boolean online) {

    public QueueEntry {
        Objects.requireNonNull(owner, "owner");
        name = name == null ? "" : name;
        party = List.copyOf(party);
    }

    public int unitSize() {
        return party.size() + 1;
    }
}
