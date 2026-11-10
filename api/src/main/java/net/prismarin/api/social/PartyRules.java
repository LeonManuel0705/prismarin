package net.prismarin.api.social;

public final class PartyRules {

    private PartyRules() {
        throw new AssertionError();
    }

    public static boolean allowed(PartyAction action, PartyRole actor, PartyRole target, PartySettings settings) {
        if (action == null || actor == null) {
            return false;
        }
        PartySettings current = settings == null ? PartySettings.DEFAULT : settings;
        return switch (action) {
            case INVITE -> actor.atLeast(current.invite());
            case REVOKE, SETTINGS -> actor.atLeast(PartyRole.MODERATOR);
            case KICK -> target != null && actor.atLeast(PartyRole.MODERATOR) && actor.outranks(target);
            case PROMOTE -> actor == PartyRole.LEADER && target == PartyRole.MEMBER;
            case DEMOTE -> actor == PartyRole.LEADER && target == PartyRole.MODERATOR;
            case TRANSFER -> actor == PartyRole.LEADER && target != null && target != PartyRole.LEADER;
            case DISBAND, WARP -> actor == PartyRole.LEADER;
            case CHAT -> actor.atLeast(current.chat());
        };
    }
}
