package net.prismarin.shared.cosmetics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ViewRulesTest {

    private static final double RANGE = 24.0;

    @Test
    void hiddenWearersShowNothing() {
        assertTrue(ViewRules.wearerShows(false, false, false, false));
        assertFalse(ViewRules.wearerShows(true, false, false, false));
        assertFalse(ViewRules.wearerShows(false, true, false, false));
        assertFalse(ViewRules.wearerShows(false, false, true, false));
        assertFalse(ViewRules.wearerShows(false, false, false, true));
    }

    @Test
    void theWearerAlwaysSeesHisOwnCosmetics() {
        assertTrue(ViewRules.viewerSees(true, false, true, 10_000.0, RANGE, false));
    }

    @Test
    void viewersWhoCannotSeeTheWearerSeeNoParticles() {
        assertTrue(ViewRules.viewerSees(false, true, false, 4.0, RANGE, true));
        assertFalse(ViewRules.viewerSees(false, false, false, 4.0, RANGE, true));
    }

    @Test
    void viewersWhoHideCosmeticsSeeNoParticlesOfOthers() {
        assertFalse(ViewRules.viewerSees(false, true, true, 4.0, RANGE, true));
    }

    @Test
    void farAwayViewersAndPluginFiltersSeeNothing() {
        assertTrue(ViewRules.viewerSees(false, true, false, RANGE * RANGE, RANGE, true));
        assertFalse(ViewRules.viewerSees(false, true, false, RANGE * RANGE + 1.0, RANGE, true));
        assertFalse(ViewRules.viewerSees(false, true, false, 4.0, RANGE, false));
    }
}
