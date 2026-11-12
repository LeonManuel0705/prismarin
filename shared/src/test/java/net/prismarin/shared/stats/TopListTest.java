package net.prismarin.shared.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import net.prismarin.api.stats.Leaderboard;
import net.prismarin.api.stats.LeaderboardEntry;
import net.prismarin.api.stats.StatOrder;
import net.prismarin.api.stats.StatPeriod;
import org.junit.jupiter.api.Test;

class TopListTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final UUID D = UUID.fromString("00000000-0000-0000-0000-00000000000d");

    private static final List<TopList.Row> ROWS = List.of(
            new TopList.Row(A, "Anna", 10L, 300L),
            new TopList.Row(B, "Ben", 25L, 100L),
            new TopList.Row(C, "Cem", 10L, 200L),
            new TopList.Row(D, "Dana", 3L, 50L));

    @Test
    void highestFirstWithTheEarlierValueWinningATie() {
        List<TopList.Row> sorted = TopList.sort(ROWS, StatOrder.HIGHEST);

        assertEquals(List.of(B, C, A, D), sorted.stream().map(TopList.Row::player).toList());
    }

    @Test
    void lowestFirstForTimes() {
        List<TopList.Row> sorted = TopList.sort(ROWS, StatOrder.LOWEST);

        assertEquals(List.of(D, C, A, B), sorted.stream().map(TopList.Row::player).toList());
    }

    @Test
    void equalTimesFallBackToTheUuid() {
        List<TopList.Row> sorted = TopList.sort(List.of(new TopList.Row(B, "Ben", 5L, 1L),
                new TopList.Row(A, "Anna", 5L, 1L)), StatOrder.HIGHEST);

        assertEquals(List.of(A, B), sorted.stream().map(TopList.Row::player).toList());
    }

    @Test
    void sharedRanksGiveTiesTheSameNumber() {
        List<TopList.Row> sorted = TopList.sort(ROWS, StatOrder.HIGHEST);

        assertEquals(List.of(1, 2, 2, 4), TopList.ranks(sorted, true));
        assertEquals(List.of(1, 2, 3, 4), TopList.ranks(sorted, false));
    }

    @Test
    void entriesUseTheShownNamesAndTheSize() {
        List<TopList.Row> sorted = TopList.sort(ROWS, StatOrder.HIGHEST);

        List<LeaderboardEntry> entries = TopList.entries(sorted, 2, false, Map.of(B, "NickedBen"));

        assertEquals(List.of(new LeaderboardEntry(1, B, "NickedBen", 25L), new LeaderboardEntry(2, C, "Cem", 10L)),
                entries);
        assertEquals(4, TopList.entries(sorted, 10, false, Map.of()).size());
    }

    @Test
    void ranksOutsideTheListCountBetterValues() {
        long[] values = TopList.values(ROWS);

        assertEquals(1, TopList.rank(values, 30L, StatOrder.HIGHEST));
        assertEquals(2, TopList.rank(values, 10L, StatOrder.HIGHEST));
        assertEquals(4, TopList.rank(values, 3L, StatOrder.HIGHEST));
        assertEquals(5, TopList.rank(values, 1L, StatOrder.HIGHEST));
        assertEquals(1, TopList.rank(values, 1L, StatOrder.LOWEST));
        assertEquals(2, TopList.rank(values, 10L, StatOrder.LOWEST));
    }

    @Test
    void snapshotsKnowTheRankOfListedAndUnlistedPlayers() {
        List<TopList.Row> sorted = TopList.sort(ROWS, StatOrder.HIGHEST);
        Leaderboard board = new Leaderboard("bedwars.wins", StatPeriod.ALL_TIME, "all",
                TopList.entries(sorted, 2, false, Map.of()), 0L);
        StatSnapshot snapshot = new StatSnapshot(board, TopList.values(ROWS), StatOrder.HIGHEST, false);

        assertEquals(OptionalInt.of(2), snapshot.rank(C, 10L));
        assertEquals(OptionalInt.of(3), snapshot.rank(A, 10L));
        assertEquals(OptionalInt.of(4), snapshot.rank(D, 3L));
        assertEquals(OptionalInt.empty(), snapshot.rank(UUID.randomUUID(), null));
        StatSnapshot shared = new StatSnapshot(board, TopList.values(ROWS), StatOrder.HIGHEST, true);
        assertEquals(OptionalInt.of(2), shared.rank(A, 10L));
    }
}
