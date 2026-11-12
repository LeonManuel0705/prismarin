package net.prismarin.shared.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.prismarin.api.event.Subscription;
import net.prismarin.api.stats.Leaderboard;
import net.prismarin.api.stats.LeaderboardEntry;
import net.prismarin.api.stats.StatChangedEvent;
import net.prismarin.api.stats.StatDefinition;
import net.prismarin.api.stats.StatFormat;
import net.prismarin.api.stats.StatOrder;
import net.prismarin.api.stats.StatPeriod;
import net.prismarin.api.stats.StatRecordEvent;
import net.prismarin.shared.event.SimpleEventBus;
import net.prismarin.shared.storage.JsonFileDocumentStore;
import net.prismarin.shared.time.DayClock;
import net.prismarin.shared.time.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentStatsServiceTest {

    private static final UUID ANNA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BEN = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID CEM = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    @TempDir
    Path directory;

    private final MutableClock clock = MutableClock.at("2026-11-12T12:00:00Z");
    private final SimpleEventBus events = new SimpleEventBus();
    private JsonFileDocumentStore store;

    @BeforeEach
    void setUp() {
        store = new JsonFileDocumentStore(directory);
    }

    private DocumentStatsService service(boolean shared, List<StatDefinition> definitions) {
        StatPeriods periods = new StatPeriods(new DayClock(ZoneId.of("Europe/Berlin"), LocalTime.MIDNIGHT),
                DayOfWeek.MONDAY, true, true);
        DocumentStatsService.Settings settings = new DocumentStatsService.Settings(periods, 60_000L, 100, shared,
                true, definitions);
        DocumentStatsService.Names names = new DocumentStatsService.Names() {
            @Override
            public Optional<String> known(UUID player) {
                return Optional.of(player.equals(ANNA) ? "Anna" : player.equals(BEN) ? "Ben" : "Cem");
            }

            @Override
            public CompletableFuture<Map<UUID, String>> shown(List<UUID> players) {
                return CompletableFuture.completedFuture(players.contains(BEN) ? Map.of(BEN, "SilentFox") : Map.of());
            }
        };
        return new DocumentStatsService(store, Runnable::run, events, clock, settings, names);
    }

    @Test
    void topListsAreSortedAndUseTheShownNames() {
        DocumentStatsService stats = service(false, List.of());
        stats.increment(ANNA, "bedwars.wins", 3L).join();
        stats.increment(BEN, "bedwars.wins", 5L).join();
        clock.advance(Duration.ofSeconds(1));
        stats.increment(CEM, "bedwars.wins", 3L).join();

        Leaderboard top = stats.top("bedwars.wins", StatPeriod.ALL_TIME, 10).join();

        assertEquals(List.of(new LeaderboardEntry(1, BEN, "SilentFox", 5L), new LeaderboardEntry(2, ANNA, "Anna", 3L),
                new LeaderboardEntry(3, CEM, "Cem", 3L)), top.entries());
        assertEquals(1, stats.top("bedwars.wins", StatPeriod.ALL_TIME, 1).join().entries().size());
    }

    @Test
    void topListsAreCachedUntilTheRefresh() {
        DocumentStatsService stats = service(false, List.of());
        stats.increment(ANNA, "bedwars.kills", 1L).join();
        assertEquals(1, stats.top("bedwars.kills", StatPeriod.ALL_TIME, 10).join().entries().size());

        stats.increment(BEN, "bedwars.kills", 4L).join();
        assertEquals(1, stats.top("bedwars.kills", StatPeriod.ALL_TIME, 10).join().entries().size());

        clock.advance(Duration.ofMinutes(2));
        assertEquals(2, stats.top("bedwars.kills", StatPeriod.ALL_TIME, 10).join().entries().size());
    }

    @Test
    void sharedRanksCanBeSwitchedOn() {
        DocumentStatsService stats = service(true, List.of());
        stats.increment(ANNA, "bedwars.wins", 3L).join();
        stats.increment(CEM, "bedwars.wins", 3L).join();

        List<Integer> ranks = stats.top("bedwars.wins", StatPeriod.ALL_TIME, 10).join().entries().stream()
                .map(LeaderboardEntry::rank).toList();

        assertEquals(List.of(1, 1), ranks);
    }

    @Test
    void theWeeklyListStartsEmptyInANewWeek() {
        DocumentStatsService stats = service(false, List.of());
        stats.increment(ANNA, "bedwars.wins", 4L).join();
        assertEquals(4L, stats.top("bedwars.wins", StatPeriod.WEEKLY, 5).join().entries().getFirst().value());

        clock.set("2026-11-15T23:00:00Z");

        assertTrue(stats.top("bedwars.wins", StatPeriod.WEEKLY, 5).join().entries().isEmpty());
        assertEquals(4L, stats.top("bedwars.wins", StatPeriod.ALL_TIME, 5).join().entries().getFirst().value());
        assertEquals("2026-11-16", stats.periodId(StatPeriod.WEEKLY));
        assertEquals(0L, stats.value(ANNA, "bedwars.wins", StatPeriod.WEEKLY).join());
        assertEquals(4L, stats.value(ANNA, "bedwars.wins", StatPeriod.MONTHLY).join());
    }

    @Test
    void lowestFirstStatsRankTheFastestTime() {
        StatDefinition time = new StatDefinition("parkour.time", null, StatFormat.MILLIS, StatOrder.LOWEST, true,
                false);
        DocumentStatsService stats = service(false, List.of(time));
        stats.record(ANNA, "parkour.time", 61_000L).join();
        stats.record(BEN, "parkour.time", 59_500L).join();
        stats.record(BEN, "parkour.time", 70_000L).join();

        Leaderboard top = stats.top("parkour.time", StatPeriod.ALL_TIME, 3).join();

        assertEquals(List.of(BEN, ANNA), top.entries().stream().map(LeaderboardEntry::player).toList());
        assertEquals(59_500L, top.entries().getFirst().value());
        assertEquals("0:59.500", stats.format("parkour.time", 59_500L, null));
    }

    @Test
    void ranksAreKnownForEveryPlayer() {
        DocumentStatsService stats = service(false, List.of());
        stats.increment(ANNA, "bedwars.wins", 9L).join();
        stats.increment(BEN, "bedwars.wins", 2L).join();

        assertEquals(OptionalInt.of(2), stats.rank(BEN, "bedwars.wins", StatPeriod.ALL_TIME).join());
        assertEquals(OptionalInt.empty(), stats.rank(CEM, "bedwars.wins", StatPeriod.ALL_TIME).join());
    }

    @Test
    void cachedValuesFollowTrackedPlayers() {
        DocumentStatsService stats = service(false, List.of());
        stats.increment(ANNA, "bedwars.wins", 2L).join();
        assertTrue(stats.cached(ANNA).isEmpty());

        stats.track(ANNA).join();
        stats.increment(ANNA, "bedwars.wins", 1L).join();
        assertEquals(3L, stats.cached(ANNA).orElseThrow().value("bedwars.wins"));
        assertTrue(stats.cachedTop("bedwars.wins", StatPeriod.ALL_TIME).isPresent());
        assertEquals(OptionalInt.of(1), stats.cachedRank(ANNA, "bedwars.wins", StatPeriod.ALL_TIME));

        stats.forget(ANNA);
        assertTrue(stats.cached(ANNA).isEmpty());
    }

    @Test
    void changesAndRecordsFireEvents() {
        DocumentStatsService stats = service(false, List.of());
        List<StatChangedEvent> changes = new ArrayList<>();
        List<StatRecordEvent> records = new ArrayList<>();
        events.subscribe(StatChangedEvent.class, changes::add);
        events.subscribe(StatRecordEvent.class, records::add);

        stats.increment(ANNA, "bedwars.wins", 1L).join();
        stats.increment(ANNA, "bedwars.wins", 0L).join();
        stats.record(ANNA, "bedwars.best-streak", 4L).join();
        stats.record(ANNA, "bedwars.best-streak", 3L).join();

        assertEquals(2, changes.size());
        assertEquals(List.of(new StatRecordEvent(ANNA, "bedwars.best-streak", 0L, 4L)), records);
    }

    @Test
    void configuredDefinitionsWinOverPluginDefaults() {
        StatDefinition configured = StatDefinition.of("bedwars.wins", "Siege");
        DocumentStatsService stats = service(false, List.of(configured));
        Subscription plugin = stats.define(StatDefinition.of("bedwars.wins", "Wins"));
        Subscription kills = stats.define(StatDefinition.of("bedwars.kills", "Kills"));

        assertEquals(configured, stats.definition("bedwars.wins"));
        assertEquals("Kills", stats.definition("bedwars.kills").name().template(null));
        assertEquals(List.of("bedwars.wins", "bedwars.kills"),
                stats.definitions().stream().map(StatDefinition::key).toList());
        assertTrue(stats.known("bedwars.kills"));
        kills.unsubscribe();
        plugin.unsubscribe();
        assertFalse(stats.known("bedwars.kills"));
        assertTrue(stats.definition("unknown.stat").inStats());
    }

    @Test
    void invalidKeysAreRefused() {
        DocumentStatsService stats = service(false, List.of());

        assertThrows(IllegalArgumentException.class, () -> stats.increment(ANNA, "Bed Wars", 1L));
        assertThrows(IllegalArgumentException.class, () -> stats.increment(ANNA, "bedwars_wins", 1L));
    }

    @Test
    void parallelIncrementsAreNeverLost() {
        StatPeriods periods = StatPeriods.defaults();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            DocumentStatsService stats = new DocumentStatsService(store, executor, events, clock,
                    new DocumentStatsService.Settings(periods, 60_000L, 100, false, true, List.of()),
                    DocumentStatsService.Names.none());
            CompletableFuture<?>[] all = new CompletableFuture<?>[200];
            for (int index = 0; index < all.length; index++) {
                all[index] = stats.increment(ANNA, "bedwars.kills", 1L);
            }
            CompletableFuture.allOf(all).join();

            assertEquals(200L, stats.stats(ANNA).join().value("bedwars.kills"));
            assertEquals(200L, stats.stats(ANNA).join().value("bedwars.kills", StatPeriod.WEEKLY));
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void switchedOffPeriodsHaveNoList() {
        StatPeriods periods = new StatPeriods(DayClock.utc(), DayOfWeek.MONDAY, false, true);
        DocumentStatsService stats = new DocumentStatsService(store, Runnable::run, events, clock,
                new DocumentStatsService.Settings(periods, 60_000L, 100, false, true, List.of()),
                DocumentStatsService.Names.none());
        stats.increment(ANNA, "bedwars.wins", 1L).join();

        assertTrue(stats.top("bedwars.wins", StatPeriod.WEEKLY, 10).join().entries().isEmpty());
        assertTrue(stats.cachedTop("bedwars.wins", StatPeriod.WEEKLY).isEmpty());
        assertEquals(0L, stats.stats(ANNA).join().value("bedwars.wins", StatPeriod.WEEKLY));
    }
}
