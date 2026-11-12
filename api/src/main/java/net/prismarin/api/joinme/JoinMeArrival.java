package net.prismarin.api.joinme;

import java.util.Objects;
import java.util.UUID;

public record JoinMeArrival(UUID player, JoinMe joinMe, boolean party) {

    public JoinMeArrival {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(joinMe, "joinMe");
    }

    public UUID creator() {
        return joinMe.creator();
    }
}
