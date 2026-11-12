package net.prismarin.shared.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import net.prismarin.api.stats.PlayerStats;
import net.prismarin.api.stats.StatChange;
import net.prismarin.api.stats.StatOrder;
import net.prismarin.api.stats.StatPeriod;
import org.junit.jupiter.api.Test;

class StatUpdatesTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String WEEK = "2026-11-09";
    private static final String MONTH = "2026-11";

    private static StatUpdates.Applied apply(String json, StatChange.Type type, long amount, StatOrder order,
                                             String week) {
        return StatUpdates.apply(json, PLAYER, "Leon", "bedwars.wins", type, amount, order, week, MONTH, 1_000L);
    }

    @Test
    void incrementsCountInEveryPeriod() {
        StatUpdates.Applied first = apply(null, StatChange.Type.INCREMENT, 2L, StatOrder.HIGHEST, WEEK);
        StatUpdates.Applied second = apply(first.json(), StatChange.Type.INCREMENT, 3L, StatOrder.HIGHEST, WEEK);

        assertEquals(new StatChange(PLAYER, "bedwars.wins", StatChange.Type.INCREMENT, 2L, 5L, 5L, 5L),
                second.change());
        PlayerStats stats = second.stats();
        assertEquals(5L, stats.value("bedwars.wins"));
        assertEquals(5L, stats.value("bedwars.wins", StatPeriod.WEEKLY));
        assertEquals("Leon", stats.name());
    }

    @Test
    void aNewWeekStartsTheWeeklyTotalAgain() {
        StatUpdates.Applied lastWeek = apply(null, StatChange.Type.INCREMENT, 7L, StatOrder.HIGHEST, "2026-11-02");
        StatUpdates.Applied thisWeek = apply(lastWeek.json(), StatChange.Type.INCREMENT, 1L, StatOrder.HIGHEST, WEEK);

        assertEquals(8L, thisWeek.change().value());
        assertEquals(1L, thisWeek.change().weekly());
        assertEquals(8L, thisWeek.change().monthly());
        assertEquals(0L, StatUpdates.read(thisWeek.json(), PLAYER, "2026-11-16", MONTH)
                .value("bedwars.wins", StatPeriod.WEEKLY));
        assertEquals(8L, StatUpdates.read(thisWeek.json(), PLAYER, "2026-11-16", "2026-12")
                .value("bedwars.wins"));
    }

    @Test
    void setMovesThePeriodsByTheDifference() {
        StatUpdates.Applied start = apply(null, StatChange.Type.SET, 10L, StatOrder.HIGHEST, WEEK);
        StatUpdates.Applied raised = apply(start.json(), StatChange.Type.SET, 14L, StatOrder.HIGHEST, WEEK);

        assertEquals(10L, raised.change().previous());
        assertEquals(14L, raised.change().value());
        assertEquals(14L, raised.change().weekly());
        StatUpdates.Applied nextWeek = apply(raised.json(), StatChange.Type.SET, 20L, StatOrder.HIGHEST,
                "2026-11-16");
        assertEquals(6L, nextWeek.change().weekly());
    }

    @Test
    void recordsKeepTheBetterValue() {
        StatUpdates.Applied first = apply(null, StatChange.Type.RECORD, 90L, StatOrder.LOWEST, WEEK);
        StatUpdates.Applied slower = apply(first.json(), StatChange.Type.RECORD, 95L, StatOrder.LOWEST, WEEK);
        StatUpdates.Applied faster = apply(slower.json(), StatChange.Type.RECORD, 80L, StatOrder.LOWEST, WEEK);

        assertTrue(first.improved());
        assertFalse(slower.improved());
        assertEquals(90L, slower.change().value());
        assertTrue(faster.improved());
        assertEquals(80L, faster.change().value());
        assertEquals(90L, faster.change().previous());
        assertEquals(80L, faster.change().weekly());
    }

    @Test
    void highestRecordsKeepTheHighestValue() {
        StatUpdates.Applied best = apply(null, StatChange.Type.RECORD, 12L, StatOrder.HIGHEST, WEEK);
        StatUpdates.Applied lower = apply(best.json(), StatChange.Type.RECORD, 4L, StatOrder.HIGHEST, WEEK);

        assertEquals(12L, lower.change().value());
        assertFalse(lower.change().changed());
    }

    @Test
    void incrementsSaturateInsteadOfOverflowing() {
        StatUpdates.Applied huge = apply(null, StatChange.Type.SET, Long.MAX_VALUE - 1L, StatOrder.HIGHEST, null);
        StatUpdates.Applied more = apply(huge.json(), StatChange.Type.INCREMENT, 10L, StatOrder.HIGHEST, null);

        assertEquals(Long.MAX_VALUE, more.change().value());
    }

    @Test
    void brokenDocumentsStartFresh() {
        StatUpdates.Applied applied = apply("not json", StatChange.Type.INCREMENT, 1L, StatOrder.HIGHEST, WEEK);

        assertEquals(1L, applied.change().value());
    }

    @Test
    void aMirrorStartsWithoutCreditingTheWholeValueToThisWeek() {
        StatUpdates.Mirrored first = StatUpdates.mirror(null, PLAYER, "Leon", Map.of("arena.wins", 40L), Map.of(),
                Map.of(), WEEK, MONTH, 1_000L);

        assertTrue(first.changed());
        assertEquals(40L, first.stats().value("arena.wins"));
        assertEquals(0L, first.stats().value("arena.wins", StatPeriod.WEEKLY));
        assertEquals(1, first.changes().size());
        assertEquals(StatChange.Type.SET, first.changes().getFirst().type());

        StatUpdates.Mirrored second = StatUpdates.mirror(first.json(), PLAYER, "Leon", Map.of("arena.wins", 43L),
                Map.of(), Map.of(), WEEK, MONTH, 2_000L);

        assertEquals(43L, second.stats().value("arena.wins"));
        assertEquals(3L, second.stats().value("arena.wins", StatPeriod.WEEKLY));
        assertEquals(3L, second.stats().value("arena.wins", StatPeriod.MONTHLY));
        assertEquals(new StatChange(PLAYER, "arena.wins", StatChange.Type.SET, 40L, 43L, 3L, 3L),
                second.changes().getFirst());
    }

    @Test
    void anUnchangedMirrorWritesNothing() {
        StatUpdates.Mirrored first = StatUpdates.mirror(null, PLAYER, "Leon", Map.of("arena.wins", 4L), Map.of(),
                Map.of(), WEEK, MONTH, 1_000L);
        StatUpdates.Mirrored again = StatUpdates.mirror(first.json(), PLAYER, "Leon", Map.of("arena.wins", 4L),
                Map.of(), Map.of(), WEEK, MONTH, 2_000L);

        assertFalse(again.changed());
        assertNull(again.json());
        assertTrue(again.changes().isEmpty());
        assertEquals(4L, again.stats().value("arena.wins"));
    }

    @Test
    void periodValuesOfASourceAreTakenAsTheyAre() {
        StatUpdates.Mirrored mirrored = StatUpdates.mirror(null, PLAYER, "Leon", Map.of("arena.wins", 40L),
                Map.of("arena.wins", 6L), Map.of("arena.wins", 11L, "arena.kills", 2L), WEEK, MONTH, 1_000L);

        assertEquals(6L, mirrored.stats().value("arena.wins", StatPeriod.WEEKLY));
        assertEquals(11L, mirrored.stats().value("arena.wins", StatPeriod.MONTHLY));
        assertEquals(2L, mirrored.stats().value("arena.kills", StatPeriod.MONTHLY));
        assertEquals(0L, mirrored.stats().value("arena.kills"));
    }

    @Test
    void aMirrorKeepsKeysItDoesNotMention() {
        StatUpdates.Applied pushed = apply(null, StatChange.Type.INCREMENT, 5L, StatOrder.HIGHEST, WEEK);
        StatUpdates.Mirrored mirrored = StatUpdates.mirror(pushed.json(), PLAYER, "Leon", Map.of("arena.wins", 1L),
                Map.of(), Map.of(), WEEK, MONTH, 2_000L);

        assertEquals(5L, mirrored.stats().value("bedwars.wins"));
        assertEquals(5L, mirrored.stats().value("bedwars.wins", StatPeriod.WEEKLY));
        assertEquals(1L, mirrored.stats().value("arena.wins"));
    }
}
