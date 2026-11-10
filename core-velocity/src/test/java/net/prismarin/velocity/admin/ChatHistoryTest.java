package net.prismarin.velocity.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatHistoryTest {

    private static final long MINUTE = 60_000L;

    private final MovingClock clock = new MovingClock();
    private final ChatHistory history = new ChatHistory(clock, 15 * MINUTE);
    private final UUID reported = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @Test
    void returnsTheConversationAroundTheReportedPlayer() {
        history.record("lobby", other, "Anna", "hi");
        clock.advance(MINUTE);
        history.record("lobby", reported, "Troll", "bad words");
        history.record("bedwars-1", other, "Anna", "elsewhere");
        List<ChatHistory.Line> lines = history.recent("LOBBY", reported, false, 10 * MINUTE, 40);
        assertEquals(List.of("hi", "bad words"), lines.stream().map(ChatHistory.Line::message).toList());
    }

    @Test
    void onlyKeepsTheReportedPlayersLinesWhenAsked() {
        history.record("lobby", other, "Anna", "hi");
        history.record("lobby", reported, "Troll", "bad words");
        assertEquals(1, history.recent("lobby", reported, true, 10 * MINUTE, 40).size());
    }

    @Test
    void attachesNothingWhenTheReportedPlayerWasSilent() {
        history.record("lobby", other, "Anna", "hi");
        assertTrue(history.recent("lobby", reported, false, 10 * MINUTE, 40).isEmpty());
    }

    @Test
    void dropsLinesOutsideTheWindowAndCapsTheCount() {
        history.record("lobby", reported, "Troll", "old");
        clock.advance(20 * MINUTE);
        for (int i = 0; i < 5; i++) {
            history.record("lobby", reported, "Troll", "line " + i);
        }
        List<ChatHistory.Line> lines = history.recent("lobby", reported, false, 10 * MINUTE, 3);
        assertEquals(List.of("line 2", "line 3", "line 4"), lines.stream().map(ChatHistory.Line::message).toList());
    }

    @Test
    void findsTheServerWhereAnOfflinePlayerLastSpoke() {
        history.record("bedwars-1", reported, "Troll", "first");
        clock.advance(MINUTE);
        history.record("lobby", other, "Anna", "hello");
        history.record("lobby", reported, "Troll", "latest");
        ChatHistory.Evidence evidence = history.lastSeen(reported, false, 10 * MINUTE, 40);
        assertEquals("lobby", evidence.server());
        assertEquals(List.of("hello", "latest"), evidence.lines().stream().map(ChatHistory.Line::message).toList());
        assertTrue(history.lastSeen(UUID.randomUUID(), false, 10 * MINUTE, 40).lines().isEmpty());
    }

    @Test
    void formatsLinesWithTimeAndName() {
        ChatHistory.Line line = new ChatHistory.Line(0L, "lobby", reported, "Troll", "hey");
        assertEquals("00:00:00 Troll: hey", ChatHistory.format(line, ZoneOffset.UTC));
    }

    private static final class MovingClock extends Clock {

        private long millis = 1_000_000L;

        void advance(long amount) {
            millis += amount;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public long millis() {
            return millis;
        }
    }
}
