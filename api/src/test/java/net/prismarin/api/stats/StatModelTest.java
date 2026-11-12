package net.prismarin.api.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StatModelTest {

    @Test
    void keysAreLowerCaseWithDotsAndDashes() {
        assertEquals("bedwars.wins", StatKeys.key(" BedWars.Wins "));
        assertTrue(StatKeys.valid("bedwars.final-kills"));
        assertFalse(StatKeys.valid("bedwars_wins"));
        assertFalse(StatKeys.valid(".wins"));
        assertFalse(StatKeys.valid("x".repeat(65)));
        assertThrows(IllegalArgumentException.class, () -> StatKeys.key("bed wars"));
    }

    @Test
    void theGameIsThePartBeforeTheFirstDot() {
        assertEquals("bedwars", StatKeys.game("bedwars.solo.wins"));
        assertEquals(StatKeys.GENERAL, StatKeys.game("votes"));
        assertEquals("Final kills", StatKeys.humanize("bedwars.final-kills"));
    }

    @Test
    void formatsShowNumbersTimesAndDecimals() {
        assertEquals("1,234,567", StatFormat.NUMBER.format(1_234_567L, Locale.ENGLISH));
        assertEquals("1.234.567", StatFormat.NUMBER.format(1_234_567L, Locale.GERMAN));
        assertEquals("1234567", StatFormat.PLAIN.format(1_234_567L, Locale.GERMAN));
        assertEquals("1:02:03", StatFormat.DURATION.format(3_723L, null));
        assertEquals("2:05", StatFormat.DURATION.format(125L, null));
        assertEquals("-0:05", StatFormat.DURATION.format(-5L, null));
        assertEquals("1:02.345", StatFormat.MILLIS.format(62_345L, null));
        assertEquals("1.25", StatFormat.DECIMAL.format(125L, Locale.ENGLISH));
        assertEquals("1,25", StatFormat.DECIMAL.format(125L, Locale.GERMAN));
        assertEquals(Optional.of(StatFormat.DURATION), StatFormat.byId("time"));
        assertEquals(Optional.empty(), StatFormat.byId("percent"));
    }

    @Test
    void ordersCompareTheRightWay() {
        assertTrue(StatOrder.HIGHEST.better(5L, 3L));
        assertTrue(StatOrder.LOWEST.better(3L, 5L));
        assertTrue(StatOrder.HIGHEST.compare(5L, 3L) < 0);
        assertTrue(StatOrder.LOWEST.compare(5L, 3L) > 0);
        assertEquals(Optional.of(StatOrder.LOWEST), StatOrder.byId("asc"));
        assertEquals(Optional.of(StatPeriod.WEEKLY), StatPeriod.byId("week"));
        assertEquals(Optional.of(StatPeriod.ALL_TIME), StatPeriod.byId("all-time"));
        assertEquals(Optional.empty(), StatPeriod.byId("daily"));
    }

    @Test
    void playerStatsAnswerForEveryPeriod() {
        UUID player = UUID.randomUUID();
        PlayerStats stats = new PlayerStats(player, "Leon", Map.of("bedwars.wins", 9L), Map.of("bedwars.wins", 2L),
                Map.of());

        assertEquals(9L, stats.value("bedwars.wins"));
        assertEquals(2L, stats.value("BedWars.Wins", StatPeriod.WEEKLY));
        assertEquals(0L, stats.value("bedwars.wins", StatPeriod.MONTHLY));
        assertTrue(stats.has("bedwars.wins"));
        assertTrue(PlayerStats.empty(player, null).isEmpty());
    }

    @Test
    void leaderboardsHandOutEntriesByPosition() {
        UUID first = UUID.randomUUID();
        Leaderboard board = new Leaderboard("bedwars.wins", StatPeriod.ALL_TIME, "all",
                List.of(new LeaderboardEntry(1, first, "Leon", 9L), new LeaderboardEntry(2, UUID.randomUUID(), "Ben",
                        4L)), 1L);

        assertEquals("Leon", board.entry(1).orElseThrow().name());
        assertTrue(board.entry(3).isEmpty());
        assertTrue(board.entry(0).isEmpty());
        assertEquals(1, board.limit(1).entries().size());
        assertEquals(2, board.limit(5).entries().size());
    }

    @Test
    void definitionsFillTheirDefaults() {
        StatDefinition definition = new StatDefinition("BedWars.Final-Kills", null, null, null, true, false);

        assertEquals("bedwars.final-kills", definition.key());
        assertEquals("Final kills", definition.name().template(Locale.ENGLISH));
        assertEquals(StatFormat.NUMBER, definition.format());
        assertEquals(StatOrder.HIGHEST, definition.order());
        assertEquals("bedwars", definition.game());
        assertTrue(definition.withProfile(true).inProfile());
    }
}
