package net.prismarin.api.social;

import java.util.Locale;
import java.util.Optional;

public enum PartyRole {

    MEMBER("member"),
    MODERATOR("moderator"),
    LEADER("leader");

    private final String id;

    PartyRole(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public boolean atLeast(PartyRole minimum) {
        return minimum == null || ordinal() >= minimum.ordinal();
    }

    public boolean outranks(PartyRole other) {
        return other != null && ordinal() > other.ordinal();
    }

    public static Optional<PartyRole> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.strip().toLowerCase(Locale.ROOT);
        for (PartyRole role : values()) {
            if (role.id.equals(normalized)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }
}
