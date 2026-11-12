package net.prismarin.api.queue;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public record QueueLeftEvent(QueueTarget target, UUID player, List<UUID> party, Reason reason, int size)
        implements PrismarinEvent {

    public enum Reason {
        LEFT,
        SWITCHED_SERVER,
        DISCONNECTED,
        OTHER_QUEUE,
        SENT_ELSEWHERE,
        PARTY_CHANGED,
        CLEARED,
        FAILED;

        public String id() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }

        public static Optional<Reason> byId(String id) {
            for (Reason reason : values()) {
                if (reason.id().equals(id)) {
                    return Optional.of(reason);
                }
            }
            return Optional.empty();
        }
    }

    public QueueLeftEvent {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(reason, "reason");
        party = List.copyOf(party);
    }
}
