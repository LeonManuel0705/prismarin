package net.prismarin.shared.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.prismarin.api.reward.Reward;
import org.junit.jupiter.api.Test;

class DailyScheduleTest {

    private final DailySchedule schedule = new DailySchedule(7,
            Map.of(1, Reward.currency("coins", 50L), 7, Reward.currency("coins", 300L)),
            Reward.currency("coins", 20L), Map.of(14, Reward.currency("gems", 10L)));

    @Test
    void theCycleRepeats() {
        assertEquals(1, schedule.day(1));
        assertEquals(7, schedule.day(7));
        assertEquals(1, schedule.day(8));
        assertEquals(7, schedule.day(14));
        assertEquals(1, schedule.day(0));
    }

    @Test
    void missingDaysGiveTheDefault() {
        assertEquals(Map.of("coins", 50L), schedule.reward(1).currencies());
        assertEquals(Map.of("coins", 20L), schedule.reward(3).currencies());
        assertEquals(Map.of("coins", 50L), schedule.reward(8).currencies());
    }

    @Test
    void milestonesComeOnTopOfTheDay() {
        assertTrue(schedule.milestone(14));
        assertFalse(schedule.milestone(7));
        assertEquals(Map.of("coins", 300L, "gems", 10L), schedule.reward(14).currencies());
        assertEquals(Map.of("coins", 300L), schedule.reward(21).currencies());
    }
}
