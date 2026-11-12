package net.prismarin.api.reward;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record Vote(String id, UUID player, String name, String service, String address, long at, int streak,
                   Reward reward) {

    public Vote {
        Objects.requireNonNull(id, "id");
        name = name == null ? "" : name;
        service = service == null ? "" : service;
        address = address == null ? "" : address;
        reward = reward == null ? Reward.EMPTY : reward;
    }

    public Optional<UUID> playerId() {
        return Optional.ofNullable(player);
    }

    public boolean known() {
        return player != null;
    }
}
