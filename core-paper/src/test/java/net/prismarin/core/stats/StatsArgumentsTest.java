package net.prismarin.core.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class StatsArgumentsTest {

    private static final Predicate<String> GAMES = Set.of("bedwars", "skywars", "general")::contains;
    private static final Predicate<String> NOBODY = name -> false;

    private static StatsArguments.Result parse(String... args) {
        return StatsArguments.parse(args, GAMES, NOBODY);
    }

    @Test
    void playersGamesAndPagesAreRead() {
        assertEquals(new StatsArguments.Show(null, null, 1), parse());
        assertEquals(new StatsArguments.Show("Leon", null, 1), parse("Leon"));
        assertEquals(new StatsArguments.Show("Leon", "bedwars", 1), parse("Leon", "BedWars"));
        assertEquals(new StatsArguments.Show("Leon", null, 2), parse("Leon", "2"));
        assertEquals(new StatsArguments.Show("Leon", "bedwars", 3), parse("Leon", "bedwars", "3"));
        assertEquals(new StatsArguments.Show("Leon", "unknown", 1), parse("Leon", "unknown"));
        assertTrue(((StatsArguments.Show) parse()).self());
    }

    @Test
    void aGameAloneMeansTheOwnStats() {
        assertEquals(new StatsArguments.Show(null, "bedwars", 1), parse("bedwars"));
        assertEquals(new StatsArguments.Show(null, "skywars", 2), parse("SkyWars", "2"));
        assertEquals(new StatsArguments.Show("bedwars", null, 1),
                StatsArguments.parse(new String[]{"bedwars"}, GAMES, "bedwars"::equalsIgnoreCase));
    }

    @Test
    void numberedGamesStayGames() {
        Predicate<String> games = Set.of("2")::contains;
        assertEquals(new StatsArguments.Show("Leon", "2", 1),
                StatsArguments.parse(new String[]{"Leon", "2"}, games, NOBODY));
    }

    @Test
    void wrongInputShowsTheUsage() {
        assertInstanceOf(StatsArguments.Usage.class, parse("Leon", "bedwars", "x"));
        assertInstanceOf(StatsArguments.Usage.class, parse("Leon", "bedwars", "0"));
        assertInstanceOf(StatsArguments.Usage.class, parse("Leon", "bedwars", "1", "2"));
        assertInstanceOf(StatsArguments.Usage.class, parse("bedwars", "next"));
    }

    @Test
    void linksPointBackToTheSameView() {
        assertEquals("/stats Leon", StatsArguments.command("Leon", null, 1));
        assertEquals("/stats Leon 2", StatsArguments.command("Leon", null, 2));
        assertEquals("/stats Leon bedwars", StatsArguments.command("Leon", "bedwars", 1));
        assertEquals("/stats Leon bedwars 3", StatsArguments.command("Leon", "bedwars", 3));
    }
}
