package net.prismarin.core.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RemainingTimeTest {

    @Test
    void partsRoundUpToTheNextMinute() {
        assertEquals(new RemainingTime.Parts(5L, 3L), RemainingTime.of((5 * 60 + 2) * 60_000L + 1L));
        assertEquals(new RemainingTime.Parts(0L, 1L), RemainingTime.of(5_000L));
        assertEquals(new RemainingTime.Parts(0L, 1L), RemainingTime.of(-10L));
        assertEquals(new RemainingTime.Parts(24L, 0L), RemainingTime.of(24 * 3_600_000L));
    }
}
