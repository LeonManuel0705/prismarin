package net.prismarin.api.joinme;

import java.util.Locale;
import java.util.Optional;
import net.prismarin.api.event.PrismarinEvent;

public record JoinMeExpiredEvent(JoinMe joinMe, Reason reason) implements PrismarinEvent {

    public enum Reason {
        EXPIRED,
        USED_UP,
        CANCELLED,
        CREATOR_LEFT,
        REPLACED;

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

    public JoinMeExpiredEvent {
        reason = reason == null ? Reason.EXPIRED : reason;
    }
}
