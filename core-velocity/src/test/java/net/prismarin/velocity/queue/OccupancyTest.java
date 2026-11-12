package net.prismarin.velocity.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import net.prismarin.api.server.GameState;
import net.prismarin.api.server.ServerStatus;
import org.junit.jupiter.api.Test;

class OccupancyTest {

    private static Optional<ServerStatus> status(boolean online, int players, int max, GameState state) {
        return Optional.of(new ServerStatus("bw-1", online, players, max, 5L, "1.21.11", true, 20D, 10D, 0L, 0L, 0L,
                Instant.EPOCH, state, ""));
    }

    @Test
    void serverIsFullAtItsOwnMaximum() {
        QueueBook.Slot slot = Occupancy.slot("bw-1", 12, status(true, 12, 12, GameState.WAITING), true,
                QueueRules.DEFAULT, 12).orElseThrow();
        assertEquals(0, slot.free());
        assertEquals(12, slot.players());
        assertEquals(1, slot.rank());
        assertEquals(4, Occupancy.slot("bw-1", 8, status(true, 8, 12, GameState.STARTING), true, QueueRules.DEFAULT,
                8).orElseThrow().free());
    }

    @Test
    void configuredMaximumKeepsTheServerBelowItsOwnLimit() {
        QueueRules twelve = new QueueRules(true, 12, true, false, true);
        assertEquals(2, Occupancy.slot("bw-1", 10, status(true, 10, 100, GameState.WAITING), true, twelve, 10)
                .orElseThrow().free());
        assertEquals(12, twelve.capacity(100));
        assertEquals(8, twelve.capacity(8));
        assertEquals(12, twelve.capacity(0));
        assertEquals(20, QueueRules.DEFAULT.capacity(20));
        assertEquals(0, QueueRules.DEFAULT.capacity(-1));
    }

    @Test
    void runningRoundTakesNobodyWhileTheGameStateCounts() {
        for (GameState state : new GameState[]{GameState.RUNNING, GameState.ENDING, GameState.RESTARTING}) {
            assertTrue(Occupancy.slot("bw-1", 2, status(true, 2, 12, state), true, QueueRules.DEFAULT, 2).isEmpty());
        }
        QueueRules ignoreState = new QueueRules(true, 0, false, false, true);
        assertEquals(10, Occupancy.slot("bw-1", 2, status(true, 2, 12, GameState.RUNNING), true, ignoreState, 2)
                .orElseThrow().free());
        assertEquals(0, Occupancy.rank(GameState.STARTING));
        assertEquals(1, Occupancy.rank(GameState.WAITING));
        assertEquals(2, Occupancy.rank(GameState.UNKNOWN));
    }

    @Test
    void offlineServersOfferNoSlot() {
        assertTrue(Occupancy.slot("bw-1", 0, status(false, 0, 0, GameState.UNKNOWN), true, QueueRules.DEFAULT, -1)
                .isEmpty());
        assertTrue(Occupancy.slot("bw-1", 0, Optional.empty(), true, QueueRules.DEFAULT, -1).isEmpty());
        assertEquals(QueueBook.Slot.UNLIMITED, Occupancy.slot("bw-1", 3, Optional.empty(), true, QueueRules.DEFAULT,
                -1).orElseThrow().free());
    }

    @Test
    void beforeTheFirstPingOnlyTheConfiguredMaximumCounts() {
        assertTrue(Occupancy.slot("bw-1", 30, Optional.empty(), false, QueueRules.DEFAULT, -1).orElseThrow()
                .unlimited());
        assertEquals(2, Occupancy.slot("bw-1", 10, Optional.empty(), false, new QueueRules(true, 12, true, false,
                true), -1).orElseThrow().free());
    }

    @Test
    void playersOfOtherProxiesCountAndLocalLeavesFreeSlotsAtOnce() {
        assertEquals(13, Occupancy.players(5, 12, 4));
        assertEquals(11, Occupancy.players(4, 12, 5));
        assertEquals(5, Occupancy.players(5, 3, -1));
        assertEquals(12, Occupancy.players(5, 12, -1));
        assertEquals(5, Occupancy.players(5, 3, 7));
        assertEquals(QueueBook.Slot.UNLIMITED, Occupancy.free(0, 40));
        assertEquals(0, Occupancy.free(10, 12));
    }
}
