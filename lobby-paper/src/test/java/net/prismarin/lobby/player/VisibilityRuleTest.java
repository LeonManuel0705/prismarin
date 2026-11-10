package net.prismarin.lobby.player;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VisibilityRuleTest {

    @Test
    void everyoneIsVisibleByDefault() {
        assertTrue(VisibilityRule.visible(false, false, false));
    }

    @Test
    void theHiderOnlyAffectsItsOwner() {
        assertFalse(VisibilityRule.visible(true, false, false));
    }

    @Test
    void silentPlayersSeeNobodyAndNobodySeesThem() {
        assertFalse(VisibilityRule.visible(false, true, false));
        assertFalse(VisibilityRule.visible(false, false, true));
        assertFalse(VisibilityRule.visible(false, true, true));
    }

    @Test
    void endingSilenceKeepsTheHiderActive() {
        assertFalse(VisibilityRule.visible(true, false, false));
        assertTrue(VisibilityRule.visible(false, false, false));
    }
}
