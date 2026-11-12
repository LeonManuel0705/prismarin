package net.prismarin.api.social;

import java.util.Locale;
import java.util.Optional;

public enum ClanRole {

    MEMBER("member"),
    MODERATOR("moderator"),
    CO_LEADER("co-leader"),
    LEADER("leader");

    private final String id;

    ClanRole(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public boolean atLeast(ClanRole minimum) {
        return minimum == null || ordinal() >= minimum.ordinal();
    }

    public boolean outranks(ClanRole other) {
        return other != null && ordinal() > other.ordinal();
    }

    public Optional<ClanRole> above() {
        ClanRole[] roles = values();
        return ordinal() + 1 < roles.length ? Optional.of(roles[ordinal() + 1]) : Optional.empty();
    }

    public Optional<ClanRole> below() {
        return ordinal() > 0 ? Optional.of(values()[ordinal() - 1]) : Optional.empty();
    }

    public static Optional<ClanRole> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.strip().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
        String mapped = switch (normalized) {
            case "members" -> "member";
            case "mod", "mods", "moderators" -> "moderator";
            case "coleader", "co-leaders", "coleaders" -> "co-leader";
            case "leaders", "owner" -> "leader";
            default -> normalized;
        };
        for (ClanRole role : values()) {
            if (role.id.equals(mapped)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }
}
