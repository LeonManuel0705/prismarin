package net.prismarin.shared.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.prismarin.api.reward.DailyStatus;
import net.prismarin.shared.storage.DocumentStore;
import net.prismarin.shared.storage.JsonFileDocumentStore;
import net.prismarin.shared.storage.SqlDatabase;
import net.prismarin.shared.storage.SqlDocumentStore;
import net.prismarin.shared.time.DayClock;
import net.prismarin.shared.time.MutableClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DailyLedgerTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final DayClock BERLIN = new DayClock(ZoneId.of("Europe/Berlin"), LocalTime.MIDNIGHT);

    @TempDir
    Path directory;

    private final MutableClock clock = MutableClock.at("2026-11-12T10:00:00Z");

    private DailyLedger ledger(DocumentStore store, int grace) {
        return new DailyLedger(store, clock, BERLIN, grace);
    }

    @Test
    void aPlayerClaimsOncePerDay() {
        DailyLedger ledger = ledger(new JsonFileDocumentStore(directory), 0);

        DailyLedger.Outcome first = ledger.claim(PLAYER, "Leon");
        DailyLedger.Outcome again = ledger.claim(PLAYER, "Leon");

        assertTrue(first.claimed());
        assertEquals(1, first.after().streak());
        assertFalse(again.claimed());
        assertFalse(again.status().claimable());
        assertEquals(1L, ledger.state(PLAYER).claims());
    }

    @Test
    void consecutiveDaysBuildTheStreak() {
        DailyLedger ledger = ledger(new JsonFileDocumentStore(directory), 0);
        for (int day = 0; day < 3; day++) {
            assertTrue(ledger.claim(PLAYER, "Leon").claimed());
            clock.advance(Duration.ofDays(1));
        }

        DailyLedger.Outcome fourth = ledger.claim(PLAYER, "Leon");

        assertEquals(4, fourth.after().streak());
        assertEquals(4, fourth.after().best());
        assertEquals(4L, fourth.after().claims());
    }

    @Test
    void aMissedDayStartsOverWithoutGrace() {
        DailyLedger ledger = ledger(new JsonFileDocumentStore(directory), 0);
        ledger.claim(PLAYER, "Leon");
        clock.advance(Duration.ofDays(1));
        ledger.claim(PLAYER, "Leon");
        clock.advance(Duration.ofDays(2));

        assertEquals(0, ledger.status(PLAYER, ledger.state(PLAYER)).streak());
        DailyLedger.Outcome broken = ledger.claim(PLAYER, "Leon");

        assertEquals(1, broken.after().streak());
        assertEquals(2, broken.step().lost());
        assertEquals(1, broken.step().missed());
        assertEquals(2, broken.after().best());
    }

    @Test
    void graceDaysKeepTheStreak() {
        DailyLedger ledger = ledger(new JsonFileDocumentStore(directory), 1);
        ledger.claim(PLAYER, "Leon");
        clock.advance(Duration.ofDays(2));

        DailyLedger.Outcome kept = ledger.claim(PLAYER, "Leon");

        assertEquals(2, kept.after().streak());
        assertFalse(kept.step().broken());
    }

    @Test
    void theDayEndsAtMidnightInTheConfiguredZone() {
        DailyLedger ledger = ledger(new JsonFileDocumentStore(directory), 0);
        clock.set("2026-11-12T22:30:00Z");
        assertTrue(ledger.claim(PLAYER, "Leon").claimed());

        clock.set("2026-11-12T23:30:00Z");
        DailyLedger.Outcome nextDay = ledger.claim(PLAYER, "Leon");

        assertTrue(nextDay.claimed());
        assertEquals(2, nextDay.after().streak());
    }

    @Test
    void theStatusTellsWhenTheNextRewardIsReady() {
        DailyLedger ledger = ledger(new JsonFileDocumentStore(directory), 0);
        DailyStatus fresh = ledger.status(PLAYER, ledger.state(PLAYER));
        assertTrue(fresh.claimable());
        assertEquals(1, fresh.nextStreak());

        DailyStatus claimed = ledger.claim(PLAYER, "Leon").status();

        assertFalse(claimed.claimable());
        assertEquals(1, claimed.streak());
        assertEquals(2, claimed.nextStreak());
        assertEquals(Instant.parse("2026-11-12T23:00:00Z").toEpochMilli(), claimed.nextReset());
    }

    @Test
    void twoServersNeverPayTheSameDayTwice() {
        SqlDatabase lobbyDatabase = SqlDatabase.sqlite(directory.resolve("shared.db"));
        SqlDatabase gameDatabase = SqlDatabase.sqlite(directory.resolve("shared.db"));
        try {
            DailyLedger lobby = ledger(new SqlDocumentStore(lobbyDatabase), 0);
            DailyLedger game = ledger(new SqlDocumentStore(gameDatabase), 0);
            AtomicInteger paid = new AtomicInteger();
            ExecutorService executor = Executors.newFixedThreadPool(8);
            try {
                CompletableFuture<?>[] claims = new CompletableFuture<?>[16];
                for (int index = 0; index < claims.length; index++) {
                    DailyLedger server = index % 2 == 0 ? lobby : game;
                    claims[index] = CompletableFuture.runAsync(() -> {
                        if (server.claim(PLAYER, "Leon").claimed()) {
                            paid.incrementAndGet();
                        }
                    }, executor);
                }
                CompletableFuture.allOf(claims).join();
            } finally {
                executor.shutdown();
            }

            assertEquals(1, paid.get());
            assertEquals(1L, game.state(PLAYER).claims());
        } finally {
            lobbyDatabase.close();
            gameDatabase.close();
        }
    }

    @Test
    void parallelClaimsOnOneStoreAlsoPayOnce() {
        DailyLedger ledger = ledger(new JsonFileDocumentStore(directory), 0);
        AtomicInteger paid = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            CompletableFuture<?>[] claims = new CompletableFuture<?>[20];
            for (int index = 0; index < claims.length; index++) {
                claims[index] = CompletableFuture.runAsync(() -> {
                    if (ledger.claim(PLAYER, "Leon").claimed()) {
                        paid.incrementAndGet();
                    }
                }, executor);
            }
            CompletableFuture.allOf(claims).join();
        } finally {
            executor.shutdown();
        }

        assertEquals(1, paid.get());
    }
}
