package net.prismarin.shared.chatfilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ViolationCounterTest {

    private static final long TEN_MINUTES = 600_000L;

    private final TestClock clock = new TestClock(5_000_000L);
    private final ViolationCounter counter = new ViolationCounter(clock);
    private final UUID player = UUID.randomUUID();

    @Test
    void theThresholdTriggersOnceWithTheEvidence() {
        assertTrue(counter.record(player, "words", 3, TEN_MINUTES, "first").isEmpty());
        clock.advance(1_000L);
        assertTrue(counter.record(player, "words", 3, TEN_MINUTES, "second").isEmpty());
        assertEquals(2, counter.count(player, "words"));
        clock.advance(1_000L);
        Optional<ViolationCounter.Trigger> trigger = counter.record(player, "words", 3, TEN_MINUTES, "third");
        assertTrue(trigger.isPresent());
        assertEquals(3, trigger.get().violations());
        assertEquals(List.of("first", "second", "third"), trigger.get().evidence());
        assertEquals(0, counter.count(player, "words"));
        assertTrue(counter.record(player, "words", 3, TEN_MINUTES, "fourth").isEmpty());
    }

    @Test
    void oldHitsFallOutOfTheWindow() {
        counter.record(player, "links", 2, TEN_MINUTES, "a");
        clock.advance(TEN_MINUTES + 1L);
        assertTrue(counter.record(player, "links", 2, TEN_MINUTES, "b").isEmpty());
        assertEquals(1, counter.count(player, "links"));
    }

    @Test
    void rulesAndPlayersCountSeparately() {
        UUID other = UUID.randomUUID();
        counter.record(player, "words", 2, TEN_MINUTES, "x");
        assertTrue(counter.record(player, "links", 2, TEN_MINUTES, "y").isEmpty());
        assertTrue(counter.record(other, "words", 2, TEN_MINUTES, "z").isEmpty());
        assertTrue(counter.record(player, "words", 2, TEN_MINUTES, "x2").isPresent());
        counter.reset(other);
        assertEquals(0, counter.count(other, "words"));
        assertEquals(1, counter.count(player, "links"));
    }

    @Test
    void aSingleViolationCanBeEnough() {
        assertEquals(1, counter.record(player, "flood", 1, TEN_MINUTES, "").orElseThrow().violations());
        assertEquals(List.of(), counter.record(player, "flood", 0, TEN_MINUTES, "").orElseThrow().evidence());
    }

    @Test
    void evidenceKeepsTheLatestLines() {
        for (int hit = 1; hit < 25; hit++) {
            assertTrue(counter.record(player, "repeat", 25, TEN_MINUTES, "line " + hit).isEmpty());
        }
        List<String> evidence = counter.record(player, "repeat", 25, TEN_MINUTES, "line 25").orElseThrow().evidence();
        assertEquals(20, evidence.size());
        assertEquals("line 6", evidence.getFirst());
        assertEquals("line 25", evidence.getLast());
    }
}
