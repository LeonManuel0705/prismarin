package net.prismarin.api.social;

import java.util.Locale;
import java.util.Optional;

public enum PartyAccess {

    INVITE_ONLY("invite-only"),
    FRIENDS("friends"),
    OPEN("open");

    private final String id;

    PartyAccess(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<PartyAccess> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.strip().toLowerCase(Locale.ROOT).replace('_', '-');
        for (PartyAccess access : values()) {
            if (access.id.equals(normalized)) {
                return Optional.of(access);
            }
        }
        return Optional.empty();
    }
}
