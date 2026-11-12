package net.prismarin.shared.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.prismarin.api.reward.Reward;
import net.prismarin.api.reward.Vote;
import net.prismarin.api.reward.VoteStats;
import net.prismarin.shared.storage.JsonFileDocumentStore;
import net.prismarin.shared.time.DayClock;
import net.prismarin.shared.time.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VoteLedgerTest {

    private static final UUID LEON = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final long DAY = Duration.ofDays(1).toMillis();

    @TempDir
    Path directory;

    private final MutableClock clock = MutableClock.at("2026-11-12T10:00:00Z");
    private VoteLedger ledger;

    @BeforeEach
    void setUp() {
        ledger = new VoteLedger(new JsonFileDocumentStore(directory), clock, DayClock.utc(), 0, true);
    }

    private Vote vote(String id, UUID player, String name, long at) {
        return new Vote(id, player, name, "example.com", "1.2.3.4", at, 1, Reward.currency("coins", 100L));
    }

    @Test
    void votesOnOneDayCountOnceForTheStreak() {
        long now = clock.millis();
        VoteLedger.Counted first = ledger.count(LEON, "Leon", now);
        VoteLedger.Counted second = ledger.count(LEON, "Leon", now + 1_000L);
        VoteLedger.Counted tomorrow = ledger.count(LEON, "Leon", now + DAY);

        assertTrue(first.newDay());
        assertFalse(second.newDay());
        assertEquals(2L, second.total());
        assertEquals(1, second.streak());
        assertEquals(2, tomorrow.streak());
        assertEquals(3L, tomorrow.total());
    }

    @Test
    void aGapEndsTheVoteStreak() {
        long now = clock.millis();
        ledger.count(LEON, "Leon", now);
        ledger.count(LEON, "Leon", now + DAY);
        VoteLedger.Counted later = ledger.count(LEON, "Leon", now + 4 * DAY);

        assertEquals(1, later.streak());
        assertEquals(2, later.lostStreak());
        assertEquals(2, later.best());
    }

    @Test
    void queuedVotesAreGivenOutExactlyOnce() {
        ledger.queue(vote("a", LEON, "Leon", clock.millis()));
        ledger.queue(vote("b", LEON, "Leon", clock.millis() + 1L));

        List<Vote> claimed = ledger.claim(LEON, "Leon", 0L, 100, 100_000);
        List<Vote> again = ledger.claim(LEON, "Leon", 0L, 100, 100_000);

        assertEquals(List.of("a", "b"), claimed.stream().map(Vote::id).toList());
        assertEquals(Reward.currency("coins", 100L), claimed.getFirst().reward());
        assertTrue(again.isEmpty());
    }

    @Test
    void votesForAnUnknownNameWaitForThatName() {
        ledger.queue(vote("offline", null, "NewPlayer", clock.millis()));

        assertTrue(ledger.claim(LEON, "Leon", 0L, 100, 100_000).isEmpty());
        List<Vote> claimed = ledger.claim(OTHER, "newplayer", 0L, 100, 100_000);

        assertEquals(1, claimed.size());
        assertEquals(OTHER, claimed.getFirst().player());
    }

    @Test
    void votesOfAnotherAccountWithTheSameNameStayQueued() {
        ledger.queue(vote("theirs", OTHER, "Leon", clock.millis()));

        assertTrue(ledger.claim(LEON, "Leon", 0L, 100, 100_000).isEmpty());
        assertEquals(1, ledger.claim(OTHER, "Leon", 0L, 100, 100_000).size());
    }

    @Test
    void expiredVotesAreDroppedWithoutReward() {
        ledger.queue(vote("old", LEON, "Leon", clock.millis() - 40 * DAY));
        ledger.queue(vote("new", LEON, "Leon", clock.millis()));

        List<Vote> claimed = ledger.claim(LEON, "Leon", 30 * DAY, 100, 100_000);

        assertEquals(List.of("new"), claimed.stream().map(Vote::id).toList());
        assertTrue(ledger.pending(LEON, "Leon").isEmpty());
    }

    @Test
    void claimsStopAtTheLimitAndTheSizeBudget() {
        for (int index = 0; index < 5; index++) {
            ledger.queue(vote("v" + index, LEON, "Leon", clock.millis() + index));
        }

        assertEquals(2, ledger.claim(LEON, "Leon", 0L, 2, 100_000).size());
        assertEquals(1, ledger.claim(LEON, "Leon", 0L, 10, 10).size());
        assertEquals(2, ledger.pending(LEON, "Leon").size());
        assertEquals(List.of("v3", "v4"), ledger.claim(LEON, "Leon", 0L, 10, 100_000).stream()
                .map(Vote::id).toList());
    }

    @Test
    void parallelClaimsHandEveryVoteOutOnce() {
        for (int index = 0; index < 10; index++) {
            ledger.queue(vote("v" + index, LEON, "Leon", clock.millis() + index));
        }
        AtomicInteger given = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(6);
        try {
            CompletableFuture<?>[] claims = new CompletableFuture<?>[6];
            for (int index = 0; index < claims.length; index++) {
                claims[index] = CompletableFuture.runAsync(() ->
                        given.addAndGet(ledger.claim(LEON, "Leon", 0L, 100, 100_000).size()), executor);
            }
            CompletableFuture.allOf(claims).join();
        } finally {
            executor.shutdown();
        }

        assertEquals(10, given.get());
    }

    @Test
    void statsCountTotalStreakAndPendingVotes() {
        ledger.count(LEON, "Leon", clock.millis());
        ledger.queue(vote("a", LEON, "Leon", clock.millis()));
        ledger.queue(vote("b", null, "leon", clock.millis()));

        VoteStats stats = ledger.stats(LEON);

        assertEquals(1L, stats.total());
        assertEquals(1, stats.streak());
        assertEquals(2, stats.pending());
        assertEquals(clock.millis(), stats.lastVote());
        clock.advance(Duration.ofDays(3));
        assertEquals(0, ledger.stats(LEON).streak());
    }

    @Test
    void pruningRemovesOldVotes() {
        ledger.queue(vote("old", LEON, "Leon", clock.millis() - 40 * DAY));
        ledger.queue(vote("new", LEON, "Leon", clock.millis()));

        assertEquals(1, ledger.prune(30 * DAY));
        assertEquals(0, ledger.prune(0L));
        assertEquals(1, ledger.pending(LEON, "Leon").size());
    }
}
