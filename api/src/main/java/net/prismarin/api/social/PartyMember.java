package net.prismarin.api.social;

import java.util.Objects;
import java.util.UUID;

public record PartyMember(UUID uuid, String name, PartyRole role) {

    public PartyMember {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(role, "role");
        name = name == null ? "" : name;
    }
}
