package net.prismarin.api.social;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;

public record ClanMemberLeftEvent(Clan clan, UUID player, Reason reason) implements PrismarinEvent {

    public enum Reason {
        LEFT,
        KICKED,
        REMOVED;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
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
}
