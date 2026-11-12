package net.prismarin.core.joinme;

import net.prismarin.api.joinme.JoinMeResult;

public final class JoinMeTexts {

    private JoinMeTexts() {
        throw new AssertionError();
    }

    public static String created(JoinMeResult.Status status) {
        return switch (status) {
            case SUCCESS -> "joinme.created";
            case DISABLED -> "joinme.disabled";
            case NO_PERMISSION -> "general.no-permission";
            case COOLDOWN -> "joinme.cooldown";
            case GROUP_BLOCKED -> "joinme.group-blocked";
            case GAME_STATE -> "joinme.game-state";
            case VANISHED -> "joinme.vanished";
            case REFUSED -> "joinme.refused";
            case NOTHING_TO_JOIN -> "joinme.nothing-to-join";
            case NOT_ENOUGH_MONEY -> "joinme.not-enough-money";
            case CANCELLED -> "joinme.cancelled";
            case UNAVAILABLE -> "joinme.unavailable";
            default -> "joinme.failed";
        };
    }

    public static String previewed(JoinMeResult.Status status) {
        return switch (status) {
            case SUCCESS -> "joinme.preview-sent";
            case DISABLED -> "joinme.disabled";
            case NO_PERMISSION -> "general.no-permission";
            case UNAVAILABLE -> "joinme.unavailable";
            default -> "joinme.failed";
        };
    }

    public static String joined(JoinMeResult.Status status) {
        return switch (status) {
            case SUCCESS -> "joinme.joining";
            case DISABLED -> "joinme.disabled";
            case NO_PERMISSION -> "general.no-permission";
            case NOT_FOUND -> "joinme.join-not-found";
            case EXPIRED -> "joinme.join-expired";
            case USED_UP -> "joinme.join-used-up";
            case OWN -> "joinme.join-own";
            case ALREADY_THERE -> "joinme.join-already-there";
            case OTHER_SERVER -> "joinme.join-other-server";
            case SERVER_OFFLINE -> "joinme.join-server-offline";
            case GAME_STATE -> "joinme.join-game-state";
            case CANCELLED -> "joinme.join-cancelled";
            case UNAVAILABLE -> "joinme.unavailable";
            default -> "joinme.failed";
        };
    }
}
