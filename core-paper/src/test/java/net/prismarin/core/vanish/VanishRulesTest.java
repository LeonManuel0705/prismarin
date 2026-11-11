package net.prismarin.core.vanish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class VanishRulesTest {

    @Test
    void visiblePlayersAreSeenByEveryone() {
        assertTrue(VanishRules.canSee(false, null, false, 1));
    }

    @Test
    void playersAlwaysSeeThemselves() {
        assertTrue(VanishRules.canSee(true, 5, false, 1));
    }

    @Test
    void vanishedPlayersNeedTheSeePermission() {
        assertFalse(VanishRules.canSee(false, 1, false, 10));
        assertTrue(VanishRules.canSee(false, 1, true, 1));
    }

    @Test
    void viewersNeedAtLeastTheTargetLevel() {
        assertFalse(VanishRules.canSee(false, 3, true, 2));
        assertTrue(VanishRules.canSee(false, 3, true, 3));
        assertTrue(VanishRules.canSee(false, 3, true, 4));
    }

    @Test
    void levelIsTheHighestGrantedLevelAndAtLeastOne() {
        assertEquals(1, VanishRules.level(List.of()));
        assertEquals(1, VanishRules.level(List.of("prismarin.vanish.use")));
        assertEquals(4, VanishRules.level(List.of("prismarin.vanish.level.2", "prismarin.vanish.level.4",
                "prismarin.vanish.level.3")));
    }

    @Test
    void malformedLevelsAreIgnored() {
        assertEquals(2, VanishRules.level(java.util.Arrays.asList("prismarin.vanish.level.x",
                "prismarin.vanish.level.2", null, "prismarin.vanish.level.")));
    }
}
