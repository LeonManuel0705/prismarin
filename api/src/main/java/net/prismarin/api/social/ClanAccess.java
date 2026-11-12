package net.prismarin.api.social;

import java.util.Locale;
import java.util.Optional;

public enum ClanAccess {

    INVITE_ONLY("invite-only"),
    OPEN("open");

    private final String id;

    ClanAccess(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public ClanAccess next() {
        return this == OPEN ? INVITE_ONLY : OPEN;
    }

    public static Optional<ClanAccess> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.strip().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
        return switch (normalized) {
            case "invite-only", "invite", "invites", "invited", "closed", "private", "einladung", "geschlossen",
                 "privat" -> Optional.of(INVITE_ONLY);
            case "open", "public", "everyone", "all", "offen", "alle", "jeder" -> Optional.of(OPEN);
            default -> Optional.empty();
        };
    }
}
