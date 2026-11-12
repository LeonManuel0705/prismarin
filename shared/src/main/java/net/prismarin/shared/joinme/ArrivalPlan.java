package net.prismarin.shared.joinme;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import net.prismarin.api.joinme.JoinMeArrivedEvent;
import net.prismarin.api.joinme.JoinMeOutcome;

public final class ArrivalPlan {

    public enum Position {
        SPAWN,
        CREATOR;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Position> byId(String id) {
            for (Position position : values()) {
                if (position.id().equalsIgnoreCase(id == null ? "" : id.strip())) {
                    return Optional.of(position);
                }
            }
            return Optional.empty();
        }
    }

    private ArrivalPlan() {
        throw new AssertionError();
    }

    public static JoinMeArrivedEvent.Placement decide(boolean handlerFound, Supplier<JoinMeOutcome> handler,
                                                      boolean sameWorld, Position position, boolean worldFound,
                                                      boolean creatorInWorld) {
        if (handlerFound) {
            JoinMeOutcome outcome = outcome(handler);
            if (outcome == JoinMeOutcome.HANDLED) {
                return JoinMeArrivedEvent.Placement.HANDLER;
            }
            if (outcome == JoinMeOutcome.REFUSED) {
                return JoinMeArrivedEvent.Placement.REFUSED;
            }
        }
        return fallback(sameWorld, position, worldFound, creatorInWorld);
    }

    public static JoinMeArrivedEvent.Placement fallback(boolean sameWorld, Position position, boolean worldFound,
                                                        boolean creatorInWorld) {
        if (!sameWorld || !worldFound) {
            return JoinMeArrivedEvent.Placement.SERVER;
        }
        if (position == Position.CREATOR && creatorInWorld) {
            return JoinMeArrivedEvent.Placement.NEAR_CREATOR;
        }
        return JoinMeArrivedEvent.Placement.WORLD_SPAWN;
    }

    private static JoinMeOutcome outcome(Supplier<JoinMeOutcome> handler) {
        try {
            JoinMeOutcome outcome = handler.get();
            return outcome == null ? JoinMeOutcome.FALLBACK : outcome;
        } catch (RuntimeException e) {
            return JoinMeOutcome.FALLBACK;
        }
    }
}
