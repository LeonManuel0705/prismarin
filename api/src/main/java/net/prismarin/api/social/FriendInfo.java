package net.prismarin.api.social;

import java.util.Objects;
import java.util.UUID;

public record FriendInfo(UUID uuid, String name, long since) {

    public FriendInfo {
        Objects.requireNonNull(uuid, "uuid");
        name = name == null ? "" : name;
    }
}
