package net.prismarin.api.joinme;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public interface JoinMeHandler {

    String id();

    JoinMeOutcome arrive(JoinMeArrival arrival);

    default int priority() {
        return 0;
    }

    default boolean allows(JoinMeSpot spot) {
        return true;
    }

    default Map<String, String> placeholders(JoinMeSpot spot) {
        return Map.of();
    }

    default Map<Integer, String> lines(JoinMeSpot spot) {
        return Map.of();
    }

    static JoinMeHandler of(String id, Function<JoinMeArrival, JoinMeOutcome> arrive) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(arrive, "arrive");
        return new JoinMeHandler() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public JoinMeOutcome arrive(JoinMeArrival arrival) {
                return arrive.apply(arrival);
            }
        };
    }
}
