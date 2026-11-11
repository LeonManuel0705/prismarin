package net.prismarin.api.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class LevelProgressTest {

    @Test
    void requirementFollowsTheProfileFormula() {
        assertEquals(500L, LevelProgress.requirement(0));
        assertEquals(750L, LevelProgress.requirement(1));
        assertEquals(3000L, LevelProgress.requirement(10));
    }

    @Test
    void ofUsesTheRequirementOfTheLevel() {
        LevelProgress progress = LevelProgress.of(2, 300L);

        assertEquals(1000L, progress.required());
        assertEquals(700L, progress.remaining());
        assertEquals(0.3D, progress.fraction(), 1e-9);
    }

    @Test
    void fractionAndRemainingStayInRange() {
        assertEquals(1D, new LevelProgress(0, 900L, 500L).fraction());
        assertEquals(0L, new LevelProgress(0, 900L, 500L).remaining());
        assertEquals(1D, new LevelProgress(0, 10L, 0L).fraction());
        assertEquals(0D, new LevelProgress(0, -5L, 500L).fraction());
    }

    @Test
    void changeReportsLevelDifference() {
        UUID player = UUID.randomUUID();
        LevelChange up = new LevelChange(player, LevelProgress.of(1, 700L), LevelProgress.of(3, 10L), 1310L, "win");
        LevelChange same = new LevelChange(player, LevelProgress.of(1, 0L), LevelProgress.of(1, 50L), 50L, null);

        assertTrue(up.levelChanged());
        assertTrue(up.levelledUp());
        assertEquals(2, up.levelsGained());
        assertFalse(same.levelChanged());
        assertEquals(LevelService.REASON_PLUGIN, same.reason());
    }

    @Test
    void eventsNormaliseTheReason() {
        UUID player = UUID.randomUUID();

        assertEquals("plugin", new LevelChangedEvent(player, 1, 2, " ").reason());
        assertEquals("bedwars", new LevelChangedEvent(player, 1, 2, " bedwars ").reason());
        assertTrue(new LevelChangedEvent(player, 1, 2, "x").levelledUp());
        assertEquals(-3, new LevelChangedEvent(player, 5, 2, "x").difference());
        assertTrue(new XpGainedEvent(player, 600L, null, LevelProgress.of(0, 0L), LevelProgress.of(1, 100L))
                .levelledUp());
    }
}
