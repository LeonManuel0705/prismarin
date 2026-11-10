package net.prismarin.lobby.player;

public final class VisibilityRule {

    private VisibilityRule() {
        throw new AssertionError();
    }

    public static boolean visible(boolean viewerHidesOthers, boolean viewerSilent, boolean targetSilent) {
        return !viewerHidesOthers && !viewerSilent && !targetSilent;
    }
}
