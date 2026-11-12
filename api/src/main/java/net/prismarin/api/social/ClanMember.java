package net.prismarin.api.social;

import java.util.Objects;
import java.util.UUID;

public record ClanMember(UUID uuid, String name, ClanRole role, long joined) {

    public ClanMember {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(role, "role");
        name = name == null ? "" : name;
    }
}
