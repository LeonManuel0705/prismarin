package net.prismarin.lobby.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CooldownTrackerTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private CooldownTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new CooldownTracker(Duration.ofSeconds(3));
    }

    @Test
    void allowsFirstUse() {
        assertTrue(tracker.tryUse(PLAYER, 1_000L));
    }

    @Test
    void blocksUseWithinCooldownWindow() {
        assertTrue(tracker.tryUse(PLAYER, 1_000L));
        assertFalse(tracker.tryUse(PLAYER, 2_500L));
    }

    @Test
    void allowsUseAfterCooldownExpired() {
        assertTrue(tracker.tryUse(PLAYER, 1_000L));
        assertTrue(tracker.tryUse(PLAYER, 4_000L));
    }

    @Test
    void tracksPlayersIndependently() {
        assertTrue(tracker.tryUse(PLAYER, 1_000L));
        assertTrue(tracker.tryUse(OTHER, 1_000L));
    }

    @Test
    void reportsRemainingMillis() {
        assertEquals(0L, tracker.remainingMillis(PLAYER, 1_000L));
        tracker.tryUse(PLAYER, 1_000L);
        assertEquals(1_500L, tracker.remainingMillis(PLAYER, 2_500L));
        assertEquals(0L, tracker.remainingMillis(PLAYER, 5_000L));
    }

    @Test
    void clearResetsCooldown() {
        assertTrue(tracker.tryUse(PLAYER, 1_000L));
        tracker.clear(PLAYER);
        assertTrue(tracker.tryUse(PLAYER, 1_001L));
    }
}
