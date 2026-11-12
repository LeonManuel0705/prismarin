package net.prismarin.api.social;

public final class ClanRules {

    private ClanRules() {
        throw new AssertionError();
    }

    public static boolean allowed(ClanAction action, ClanRole actor, ClanRole target, ClanRights rights) {
        if (action == null || actor == null) {
            return false;
        }
        ClanRights current = rights == null ? ClanRights.DEFAULT : rights;
        boolean right = actor.atLeast(current.role(action));
        return switch (action) {
            case INVITE, COLOR, RENAME, SETTINGS, CHAT -> right;
            case KICK -> right && target != null && actor.outranks(target);
            case PROMOTE -> right && target != null && target.above()
                    .filter(next -> next != ClanRole.LEADER && actor.outranks(next)).isPresent();
            case DEMOTE -> right && target != null && target != ClanRole.MEMBER && actor.outranks(target);
            case TRANSFER -> actor == ClanRole.LEADER && target != null && target != ClanRole.LEADER;
            case DISBAND -> actor == ClanRole.LEADER;
        };
    }
}
