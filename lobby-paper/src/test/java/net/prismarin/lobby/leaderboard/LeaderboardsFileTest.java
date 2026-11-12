package net.prismarin.lobby.leaderboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.prismarin.api.stats.StatPeriod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LeaderboardsFileTest {

    @TempDir
    Path directory;

    @Test
    void placedBoardsSurviveARestart() throws IOException {
        Path file = directory.resolve(LeaderboardsFile.FILE_NAME);
        LeaderboardsFile boards = LeaderboardsFile.load(file);
        assertTrue(boards.boards().isEmpty());

        boards.put(new LeaderboardBoard("Wins", "bedwars.wins", StatPeriod.WEEKLY, 10, "world", 1.234, 64.5, -3.0,
                90.0f, "", ""));
        boards.save();
        LeaderboardsFile reloaded = LeaderboardsFile.load(file);

        LeaderboardBoard board = reloaded.board("WINS").orElseThrow();
        assertEquals("wins", board.id());
        assertEquals(StatPeriod.WEEKLY, board.period());
        assertEquals(1.23, board.x());
        assertEquals(90.0f, board.yaw());
        assertEquals(LeaderboardsFile.DEFAULT_REFRESH_SECONDS, reloaded.refreshSeconds());
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).startsWith("# Leaderboards in the lobby"));
        assertTrue(reloaded.remove("wins"));
        assertFalse(reloaded.remove("wins"));
    }

    @Test
    void brokenEntriesAreSkippedWithAWarning() throws IOException {
        Path file = directory.resolve(LeaderboardsFile.FILE_NAME);
        Files.writeString(file, """
                refresh-seconds: 1
                display:
                  billboard: fixed
                  background: "#FF102030"
                  scale: 50
                boards:
                  good:
                    stat: votes
                    period: monthly
                    size: 99
                    world: lobby
                  bad:
                    stat: "no key"
                    world: lobby
                  "Has Space":
                    stat: votes
                    world: lobby
                """, StandardCharsets.UTF_8);

        LeaderboardsFile boards = LeaderboardsFile.load(file);

        assertEquals(List.of("good"), boards.boards().stream().map(LeaderboardBoard::id).toList());
        assertEquals(LeaderboardBoard.MAX_SIZE, boards.board("good").orElseThrow().size());
        assertEquals(2, boards.warnings().size());
        assertEquals(5, boards.refreshSeconds());
        assertEquals("fixed", boards.display().billboard());
        assertEquals(10.0d, boards.display().scale());
        assertEquals(Optional.of(0xFF102030), boards.display().argb());
    }

    @Test
    void backgroundsParseAsArgb() {
        assertEquals(Optional.of(0xFF112233), display("#112233").argb());
        assertEquals(Optional.of(0x40112233), display("#40112233").argb());
        assertEquals(Optional.of(0), display("none").argb());
        assertEquals(Optional.empty(), display("default").argb());
        assertEquals(Optional.empty(), display("red").argb());
    }

    @Test
    void idsAreChecked() {
        assertTrue(LeaderboardBoard.validId("top-wins-2"));
        assertFalse(LeaderboardBoard.validId("-wins"));
        assertFalse(LeaderboardBoard.validId("with space"));
        assertThrows(IllegalArgumentException.class, () -> new LeaderboardBoard("x y", "votes", null, 5, "w", 0, 0, 0,
                0.0f, null, null));
    }

    private static LeaderboardsFile.Display display(String background) {
        return new LeaderboardsFile.Display("center", background, true, 250, 1.0);
    }
}
