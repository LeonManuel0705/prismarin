package net.prismarin.shared.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StreaksTest {

    @Test
    void theFirstClaimStartsAtOne() {
        Streaks.Step step = Streaks.next(Streaks.NEVER, 0, 100L, 0);

        assertEquals(new Streaks.Step(1, true, 0, 0), step);
    }

    @Test
    void theNextDayContinuesTheStreak() {
        assertEquals(new Streaks.Step(5, true, 0, 0), Streaks.next(100L, 4, 101L, 0));
    }

    @Test
    void theSameDayCountsNothing() {
        Streaks.Step step = Streaks.next(100L, 4, 100L, 0);

        assertFalse(step.counted());
        assertEquals(4, step.streak());
    }

    @Test
    void aClockThatWentBackCountsNothing() {
        assertFalse(Streaks.next(100L, 4, 99L, 0).counted());
    }

    @Test
    void missingADayWithoutGraceStartsOver() {
        Streaks.Step step = Streaks.next(100L, 9, 102L, 0);

        assertEquals(1, step.streak());
        assertTrue(step.broken());
        assertEquals(9, step.lost());
        assertEquals(1, step.missed());
    }

    @Test
    void graceDaysKeepTheStreak() {
        assertEquals(new Streaks.Step(10, true, 0, 1), Streaks.next(100L, 9, 102L, 1));
        assertEquals(new Streaks.Step(10, true, 0, 2), Streaks.next(100L, 9, 103L, 2));
        assertTrue(Streaks.next(100L, 9, 104L, 2).broken());
    }

    @Test
    void theCurrentStreakDropsToZeroOnceItCannotContinue() {
        assertEquals(0, Streaks.current(Streaks.NEVER, 0, 100L, 0));
        assertEquals(4, Streaks.current(100L, 4, 100L, 0));
        assertEquals(4, Streaks.current(100L, 4, 101L, 0));
        assertEquals(0, Streaks.current(100L, 4, 102L, 0));
        assertEquals(4, Streaks.current(100L, 4, 102L, 1));
    }
}
