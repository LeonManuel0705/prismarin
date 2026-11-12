package net.prismarin.api.social;

import java.util.Locale;
import java.util.Optional;

public enum ClanAction {
    INVITE,
    KICK,
    PROMOTE,
    DEMOTE,
    TRANSFER,
    DISBAND,
    COLOR,
    RENAME,
    SETTINGS,
    CHAT;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean configurable() {
        return this != TRANSFER && this != DISBAND;
    }

    public static Optional<ClanAction> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.strip().toLowerCase(Locale.ROOT);
        for (ClanAction action : values()) {
            if (action.id().equals(normalized)) {
                return Optional.of(action);
            }
        }
        return Optional.empty();
    }
}
