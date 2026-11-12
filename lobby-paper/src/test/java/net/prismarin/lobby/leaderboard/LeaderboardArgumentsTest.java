package net.prismarin.lobby.leaderboard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import net.prismarin.api.stats.StatPeriod;
import org.junit.jupiter.api.Test;

class LeaderboardArgumentsTest {

    private static final Set<StatPeriod> ALL = Set.of(StatPeriod.ALL_TIME, StatPeriod.WEEKLY, StatPeriod.MONTHLY);

    private static LeaderboardArguments.Result parse(Set<StatPeriod> periods, String... args) {
        return LeaderboardArguments.create(args, periods);
    }

    @Test
    void periodAndSizeAreOptionalInAnyOrder() {
        assertEquals(new LeaderboardArguments.Create("wins", "bedwars.wins", StatPeriod.ALL_TIME, 10),
                parse(ALL, "create", "Wins", "BedWars.Wins"));
        assertEquals(new LeaderboardArguments.Create("wins", "bedwars.wins", StatPeriod.WEEKLY, 5),
                parse(ALL, "create", "wins", "bedwars.wins", "weekly", "5"));
        assertEquals(new LeaderboardArguments.Create("wins", "bedwars.wins", StatPeriod.MONTHLY, 15),
                parse(ALL, "create", "wins", "bedwars.wins", "15", "month"));
    }

    @Test
    void mistakesNameTheirMessage() {
        assertEquals(new LeaderboardArguments.Invalid("lobby.leaderboard.usage", ""), parse(ALL, "create", "x"));
        assertEquals(new LeaderboardArguments.Invalid("lobby.leaderboard.invalid-id", "-x"),
                parse(ALL, "create", "-x", "votes"));
        assertEquals(new LeaderboardArguments.Invalid("lobby.leaderboard.invalid-stat", "bed_wins"),
                parse(ALL, "create", "x", "bed_wins"));
        assertEquals(new LeaderboardArguments.Invalid("lobby.leaderboard.invalid-size", "40"),
                parse(ALL, "create", "x", "votes", "40"));
        assertEquals(new LeaderboardArguments.Invalid("lobby.leaderboard.invalid-period", "daily"),
                parse(ALL, "create", "x", "votes", "daily"));
        assertEquals(new LeaderboardArguments.Invalid("lobby.leaderboard.invalid-period", "weekly"),
                parse(Set.of(StatPeriod.ALL_TIME), "create", "x", "votes", "weekly"));
    }
}
