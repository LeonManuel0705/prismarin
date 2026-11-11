package net.prismarin.api.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.prismarin.api.level.LevelProgress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlayerProfileProgressTest {

    private record Call(LevelProgress before, LevelProgress after, long xpAdded, String reason) {
    }

    private final List<Call> calls = new ArrayList<>();
    private PlayerProfile profile;

    @BeforeEach
    void setUp() {
        profile = PlayerProfile.create(UUID.randomUUID(), "Leon", "de");
        profile.progressListener((changed, before, after, xpAdded, reason) ->
                calls.add(new Call(before, after, xpAdded, reason)));
    }

    @Test
    void addXpReportsBeforeAndAfter() {
        profile.addXp(600L, "bedwars-win");

        assertEquals(1, calls.size());
        Call call = calls.getFirst();
        assertEquals(LevelProgress.of(0, 0L), call.before());
        assertEquals(LevelProgress.of(1, 100L), call.after());
        assertEquals(600L, call.xpAdded());
        assertEquals("bedwars-win", call.reason());
    }

    @Test
    void addXpWithoutReasonUsesPlugin() {
        profile.addXp(10L);

        assertEquals("plugin", calls.getFirst().reason());
    }

    @Test
    void zeroXpReportsNothing() {
        profile.addXp(0L);

        assertTrue(calls.isEmpty());
    }

    @Test
    void setLevelReportsOnlyRealChanges() {
        profile.setLevel(0);
        profile.setLevel(4, "command");

        assertEquals(1, calls.size());
        assertEquals(0, calls.getFirst().before().level());
        assertEquals(4, calls.getFirst().after().level());
        assertEquals(0L, calls.getFirst().xpAdded());
    }

    @Test
    void setXpAloneReportsNothing() {
        profile.setXp(300L);

        assertTrue(calls.isEmpty());
        assertEquals(LevelProgress.of(0, 300L), profile.progress());
    }

    @Test
    void setProgressSetsBothValues() {
        profile.addXp(200L);
        calls.clear();

        profile.setProgress(3, 0L, "command");

        assertEquals(LevelProgress.of(3, 0L), profile.progress());
        assertEquals(1, calls.size());
        assertEquals(LevelProgress.of(0, 200L), calls.getFirst().before());
    }

    @Test
    void profileWithoutListenerStillWorks() {
        PlayerProfile plain = PlayerProfile.create(UUID.randomUUID(), "Steve", "en");

        plain.addXp(1500L);

        assertEquals(2, plain.getLevel());
        assertEquals(250L, plain.getXp());
    }
}
