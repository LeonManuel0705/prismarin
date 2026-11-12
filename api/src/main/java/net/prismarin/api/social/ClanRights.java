package net.prismarin.api.social;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public record ClanRights(Map<ClanAction, ClanRole> minimum) {

    public static final ClanRights DEFAULT = new ClanRights(Map.of());

    public ClanRights {
        EnumMap<ClanAction, ClanRole> filled = new EnumMap<>(ClanAction.class);
        for (ClanAction action : ClanAction.values()) {
            ClanRole configured = minimum == null ? null : minimum.get(action);
            ClanRole chosen = configured == null ? fallback(action) : configured;
            filled.put(action, chosen.atLeast(lowest(action)) ? chosen : lowest(action));
        }
        minimum = Collections.unmodifiableMap(filled);
    }

    public ClanRole role(ClanAction action) {
        return minimum.get(action);
    }

    public ClanRights with(ClanAction action, ClanRole role) {
        EnumMap<ClanAction, ClanRole> next = new EnumMap<>(minimum);
        next.put(action, role);
        return new ClanRights(next);
    }

    public static ClanRole fallback(ClanAction action) {
        return switch (action) {
            case INVITE, KICK -> ClanRole.MODERATOR;
            case PROMOTE, DEMOTE, COLOR, SETTINGS -> ClanRole.CO_LEADER;
            case TRANSFER, DISBAND, RENAME -> ClanRole.LEADER;
            case CHAT -> ClanRole.MEMBER;
        };
    }

    public static ClanRole lowest(ClanAction action) {
        return switch (action) {
            case COLOR, RENAME -> ClanRole.CO_LEADER;
            case TRANSFER, DISBAND -> ClanRole.LEADER;
            default -> ClanRole.MEMBER;
        };
    }
}
