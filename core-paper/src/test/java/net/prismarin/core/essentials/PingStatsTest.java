package net.prismarin.core.essentials;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PingStatsTest {

    @Test
    void reportsLowestAveragePeakAndSpread() {
        PingStats stats = new PingStats();
        for (int ping : new int[]{20, 24, 41, 18, -1}) {
            stats.add(ping);
        }
        assertEquals(4, stats.count());
        assertEquals(18, stats.lowest());
        assertEquals(26, stats.average());
        assertEquals(41, stats.peak());
        assertEquals(23, stats.spread());
    }

    @Test
    void anEmptyTestReportsZero() {
        PingStats stats = new PingStats();
        assertEquals(0, stats.average());
        assertEquals(0, stats.spread());
    }
}
