package net.prismarin.core.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import net.prismarin.shared.stats.StatNumbers;
import org.junit.jupiter.api.Test;

class StatSyncScheduleTest {

    private static final UUID ANNA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BEN = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void aJoinSyncsAfterTheDelayAndThenEveryInterval() {
        StatSyncSchedule schedule = StatSyncSchedule.of(new StatsSettings.PlaceholderSync(5, 10,
                StatNumbers.Decimal.AUTO));
        schedule.joined(ANNA, 0L);

        assertEquals(List.of(), schedule.due(9_999L));
        assertEquals(List.of(ANNA), schedule.due(10_000L));
        assertEquals(List.of(), schedule.due(10_001L));
        assertEquals(OptionalLong.of(310_000L), schedule.next(ANNA));
        assertEquals(List.of(), schedule.due(309_999L));
        assertEquals(List.of(ANNA), schedule.due(310_000L));
    }

    @Test
    void playersAreSpreadByTheirJoinTime() {
        StatSyncSchedule schedule = new StatSyncSchedule(60_000L, 0L);
        schedule.joined(ANNA, 0L);
        schedule.joined(BEN, 30_000L);

        assertEquals(List.of(ANNA), schedule.due(0L));
        assertEquals(List.of(BEN), schedule.due(30_000L));
        assertEquals(List.of(ANNA), schedule.due(60_000L));
    }

    @Test
    void withoutAnIntervalOnlyTheJoinSyncRuns() {
        StatSyncSchedule schedule = StatSyncSchedule.of(new StatsSettings.PlaceholderSync(0, 3,
                StatNumbers.Decimal.AUTO));
        schedule.joined(ANNA, 1_000L);

        assertEquals(List.of(ANNA), schedule.due(4_000L));
        assertTrue(schedule.next(ANNA).isEmpty());
        assertEquals(List.of(), schedule.due(Long.MAX_VALUE - 1));
        assertEquals(0L, schedule.intervalMillis());
    }

    @Test
    void leavingStopsTheSync() {
        StatSyncSchedule schedule = new StatSyncSchedule(60_000L, 0L);
        schedule.joined(ANNA, 0L);
        schedule.left(ANNA);

        assertEquals(List.of(), schedule.due(120_000L));
        assertEquals(0, schedule.size());
    }

    @Test
    void settingsAreClamped() {
        StatsSettings.PlaceholderSync sync = new StatsSettings.PlaceholderSync(-4, 9_999, null);

        assertEquals(0, sync.minutes());
        assertEquals(300, sync.joinDelaySeconds());
        assertEquals(StatNumbers.Decimal.AUTO, sync.decimal());
    }
}
