package net.prismarin.shared.cosmetics;

public final class ViewRules {

    private ViewRules() {
        throw new AssertionError();
    }

    public static boolean wearerShows(boolean vanished, boolean spectator, boolean invisible, boolean dead) {
        return !vanished && !spectator && !invisible && !dead;
    }

    public static boolean viewerSees(boolean self, boolean canSeeWearer, boolean viewerHidesOthers,
                                     double distanceSquared, double range, boolean filtersAllow) {
        if (self) {
            return true;
        }
        if (!canSeeWearer || viewerHidesOthers || !filtersAllow) {
            return false;
        }
        return distanceSquared <= range * range;
    }
}
