package net.prismarin.shared.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.prismarin.api.stats.LeaderboardEntry;
import net.prismarin.api.stats.PlayerStats;
import org.junit.jupiter.api.Test;

class StatSourcesTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void sourceKeysAreMovedIntoTheirGame() {
        Map<String, Long> values = StatSources.normalize("bedwars", Map.of("wins", 3L, "BedWars.Kills", 7L,
                "bad key", 1L));

        assertEquals(Map.of("bedwars.wins", 3L, "bedwars.kills", 7L), values);
    }

    @Test
    void sourceValuesWinForTheirKeysAndTheRestStays() {
        PlayerStats stored = new PlayerStats(PLAYER, "Leon", Map.of("bedwars.wins", 1L, "bedwars.beds", 4L,
                "votes", 2L), Map.of("bedwars.wins", 1L), Map.of());
        StatSources.Read read = new StatSources.Read("bedwars", false, Map.of("wins", 9L, "kills", 5L),
                Map.of(), Map.of());

        PlayerStats merged = StatSources.merge(stored, List.of(read));

        assertEquals(9L, merged.value("bedwars.wins"));
        assertEquals(5L, merged.value("bedwars.kills"));
        assertEquals(4L, merged.value("bedwars.beds"));
        assertEquals(2L, merged.value("votes"));
        assertEquals("Leon", merged.name());
    }

    @Test
    void onlyChangedValuesOfUnrankedSourcesAreMirrored() {
        PlayerStats stored = new PlayerStats(PLAYER, "Leon", Map.of("arena.wins", 3L, "arena.games", 8L),
                Map.of("arena.wins", 1L), Map.of());
        StatSources.Read unranked = new StatSources.Read("arena", false, Map.of("wins", 4L, "games", 8L),
                Map.of(), Map.of());
        StatSources.Read ranked = new StatSources.Read("bedwars", true, Map.of("wins", 50L), Map.of(), Map.of());

        StatSources.Plan plan = StatSources.changes(stored, List.of(unranked, ranked));

        assertEquals(Map.of("arena.wins", 4L), plan.all());
        assertTrue(plan.weekly().isEmpty());
        assertTrue(StatSources.changes(stored, List.of(new StatSources.Read("arena", false,
                Map.of("wins", 3L), Map.of(), Map.of()))).empty());
    }

    @Test
    void periodValuesOfASourceTravelWithTheirKey() {
        PlayerStats stored = new PlayerStats(PLAYER, "Leon", Map.of("arena.wins", 3L), Map.of("arena.wins", 1L),
                Map.of());
        StatSources.Read read = new StatSources.Read("arena", false, Map.of("wins", 3L), Map.of("wins", 2L),
                Map.of("wins", 3L));

        StatSources.Plan plan = StatSources.changes(stored, List.of(read));

        assertEquals(Map.of("arena.wins", 3L), plan.all());
        assertEquals(Map.of("arena.wins", 2L), plan.weekly());
        assertEquals(Map.of("arena.wins", 3L), plan.monthly());
    }

    @Test
    void sourceTopListsAreCutRankedAndNamed() {
        List<LeaderboardEntry> given = List.of(new LeaderboardEntry(0, PLAYER, "Leon", 9L),
                new LeaderboardEntry(0, OTHER, "", 4L), new LeaderboardEntry(0, UUID.randomUUID(), "Ben", 1L));

        List<LeaderboardEntry> entries = StatSources.entries(given, 2);
        List<LeaderboardEntry> named = StatSources.named(entries, Map.of(PLAYER, "SilentFox"));

        assertEquals(List.of(1, 2), entries.stream().map(LeaderboardEntry::rank).toList());
        assertEquals("SilentFox", named.getFirst().name());
        assertEquals("00000000", named.get(1).name());
    }
}
