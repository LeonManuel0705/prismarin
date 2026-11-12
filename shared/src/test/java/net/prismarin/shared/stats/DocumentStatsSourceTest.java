package net.prismarin.shared.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.prismarin.api.admin.NoticeText;
import net.prismarin.api.event.Subscription;
import net.prismarin.api.stats.Leaderboard;
import net.prismarin.api.stats.LeaderboardEntry;
import net.prismarin.api.stats.PlayerStats;
import net.prismarin.api.stats.StatChangedEvent;
import net.prismarin.api.stats.StatDefinition;
import net.prismarin.api.stats.StatFormat;
import net.prismarin.api.stats.StatGame;
import net.prismarin.api.stats.StatOrder;
import net.prismarin.api.stats.StatPeriod;
import net.prismarin.api.stats.StatSource;
import net.prismarin.shared.event.SimpleEventBus;
import net.prismarin.shared.storage.JsonFileDocumentStore;
import net.prismarin.shared.time.DayClock;
import net.prismarin.shared.time.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentStatsSourceTest {

    private static final UUID ANNA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BEN = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID CEM = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    @TempDir
    Path directory;

    private final MutableClock clock = MutableClock.at("2026-11-12T12:00:00Z");
    private final SimpleEventBus events = new SimpleEventBus();
    private final Map<UUID, Map<String, Long>> arena = new ConcurrentHashMap<>();
    private final List<String> problems = new ArrayList<>();
    private JsonFileDocumentStore store;

    @BeforeEach
    void setUp() {
        store = new JsonFileDocumentStore(directory);
    }

    private DocumentStatsService service(List<GameConfig> games) {
        StatPeriods periods = new StatPeriods(new DayClock(ZoneId.of("Europe/Berlin"), LocalTime.MIDNIGHT),
                DayOfWeek.MONDAY, true, true);
        DocumentStatsService.Settings settings = new DocumentStatsService.Settings(periods, 60_000L, 100, false,
                true, List.of(StatDefinition.of("bedwars.wins", "Wins")), games, 150L);
        DocumentStatsService service = new DocumentStatsService(store, Runnable::run, events, clock, settings,
                new DocumentStatsService.Names() {
                    @Override
                    public Optional<String> known(UUID player) {
                        return Optional.of(player.equals(ANNA) ? "Anna" : player.equals(BEN) ? "Ben" : "Cem");
                    }

                    @Override
                    public CompletableFuture<Map<UUID, String>> shown(List<UUID> players) {
                        return CompletableFuture.completedFuture(players.contains(BEN) ? Map.of(BEN, "SilentFox")
                                : Map.of());
                    }
                });
        service.reportProblems((game, error) -> problems.add(game));
        return service;
    }

    private StatSource arenaSource() {
        return StatSource.of("arena", player -> CompletableFuture.completedFuture(arena.getOrDefault(player,
                Map.of())));
    }

    @Test
    void gamesComeFromConfigPluginsDefinitionsAndSources() {
        DocumentStatsService stats = service(List.of(GameConfig.named("bedwars", NoticeText.of("Bed Wars")),
                new GameConfig("general", null, null, null, 0, null)));
        Subscription registered = stats.defineGame(StatGame.of("bedwars", "Bedwars").withIcon("RED_BED")
                .withColor("#F87171").withOrder(5).withFeatured("final-kills"));
        stats.defineGame(StatGame.of("skywars").withOrder(1));
        stats.registerSource(arenaSource());

        StatGame bedwars = stats.game("bedwars");
        assertEquals("Bed Wars", bedwars.name().template(Locale.ENGLISH));
        assertEquals("RED_BED", bedwars.icon());
        assertEquals("#f87171", bedwars.color());
        assertEquals(List.of("bedwars.final-kills"), bedwars.featured());
        assertEquals("Arena", stats.game("arena").name().template(Locale.GERMAN));
        assertEquals(List.of("general", "skywars", "bedwars", "arena"),
                stats.games().stream().map(StatGame::id).toList());
        assertTrue(stats.source("arena").isPresent());
        assertTrue(stats.source("nope.nope").isEmpty());

        registered.unsubscribe();

        assertEquals("", stats.game("bedwars").icon());
        assertEquals("Bed Wars", stats.game("bedwars").name().template(Locale.ENGLISH));
    }

    @Test
    void unknownKeysGetANameAndAGuessedFormat() {
        DocumentStatsService stats = service(List.of());

        StatDefinition unknown = stats.definition("parkour.best-millis");

        assertEquals("Best millis", unknown.name().template(Locale.ENGLISH));
        assertEquals(StatFormat.MILLIS, unknown.format());
        assertEquals("1:02.345", stats.format("parkour.best-millis", 62_345L, Locale.ENGLISH));
        assertEquals("1,234", stats.format("unknown.thing", 1_234L, Locale.ENGLISH));
    }

    @Test
    void pullSourcesAreMergedAndMirroredForLeaderboards() {
        DocumentStatsService stats = service(List.of());
        List<StatChangedEvent> changes = new ArrayList<>();
        events.subscribe(StatChangedEvent.class, changes::add);
        stats.increment(ANNA, "bedwars.wins", 2L).join();
        stats.registerSource(arenaSource());
        arena.put(ANNA, Map.of("wins", 40L, "arena.games", 90L));
        arena.put(BEN, Map.of("wins", 55L));

        PlayerStats anna = stats.stats(ANNA).join();
        stats.stats(BEN).join();

        assertEquals(40L, anna.value("arena.wins"));
        assertEquals(90L, anna.value("arena.games"));
        assertEquals(2L, anna.value("bedwars.wins"));
        assertEquals(0L, anna.value("arena.wins", StatPeriod.WEEKLY));
        assertEquals(4, changes.size());
        DocumentStatsService elsewhere = service(List.of());
        assertEquals(40L, elsewhere.stats(ANNA).join().value("arena.wins"));
        Leaderboard top = elsewhere.top("arena.wins", StatPeriod.ALL_TIME, 10).join();
        assertEquals(List.of(BEN, ANNA), top.entries().stream().map(LeaderboardEntry::player).toList());
        assertEquals("SilentFox", top.entries().getFirst().name());

        arena.put(ANNA, Map.of("wins", 43L, "games", 95L));
        PlayerStats later = stats.stats(ANNA).join();

        assertEquals(43L, later.value("arena.wins"));
        assertEquals(3L, later.value("arena.wins", StatPeriod.WEEKLY));
        assertEquals(5L, elsewhere.stats(ANNA).join().value("arena.games", StatPeriod.MONTHLY));
    }

    @Test
    void anUnchangedSourceWritesNothing() {
        DocumentStatsService stats = service(List.of());
        stats.registerSource(arenaSource());
        arena.put(ANNA, Map.of("wins", 4L));
        stats.stats(ANNA).join();
        List<StatChangedEvent> changes = new ArrayList<>();
        events.subscribe(StatChangedEvent.class, changes::add);

        stats.stats(ANNA).join();
        stats.track(ANNA).join();

        assertTrue(changes.isEmpty());
        assertEquals(4L, stats.cached(ANNA).orElseThrow().value("arena.wins"));
    }

    @Test
    void sourcesWithOwnTopListsAreAskedAndNotMirrored() {
        DocumentStatsService stats = service(List.of());
        AtomicInteger asked = new AtomicInteger();
        StatSource bedwars = new StatSource() {
            @Override
            public String game() {
                return "bedwars";
            }

            @Override
            public CompletableFuture<Map<String, Long>> values(UUID player) {
                return CompletableFuture.completedFuture(player.equals(ANNA) ? Map.of("wins", 12L) : Map.of());
            }

            @Override
            public boolean topLists() {
                return true;
            }

            @Override
            public CompletableFuture<List<LeaderboardEntry>> top(String key, StatPeriod period, int size) {
                asked.incrementAndGet();
                return CompletableFuture.completedFuture(List.of(new LeaderboardEntry(1, BEN, "Ben", 30L),
                        new LeaderboardEntry(2, ANNA, "Anna", 12L)));
            }
        };
        stats.registerSource(bedwars);

        assertEquals(12L, stats.stats(ANNA).join().value("bedwars.wins"));
        Leaderboard top = stats.top("bedwars.wins", StatPeriod.ALL_TIME, 10).join();

        assertEquals(List.of(new LeaderboardEntry(1, BEN, "SilentFox", 30L), new LeaderboardEntry(2, ANNA, "Anna",
                12L)), top.entries());
        assertEquals(OptionalInt.of(2), stats.rank(ANNA, "bedwars.wins", StatPeriod.ALL_TIME).join());
        assertEquals(OptionalInt.empty(), stats.rank(CEM, "bedwars.wins", StatPeriod.ALL_TIME).join());
        assertEquals(1, asked.get());
        assertTrue(stats.top("bedwars.wins", StatPeriod.WEEKLY, 10).join().entries().isEmpty());
        assertEquals(0L, service(List.of()).stats(ANNA).join().value("bedwars.wins"));
        assertEquals(Map.of("bedwars.wins", OptionalInt.of(2)),
                stats.ranks(stats.stats(ANNA).join(), List.of("bedwars.wins"), StatPeriod.ALL_TIME).join());
    }

    @Test
    void failingOrSlowSourcesFallBackToTheStoredValues() {
        DocumentStatsService stats = service(List.of());
        stats.increment(ANNA, "arena.wins", 7L).join();
        Subscription failing = stats.registerSource(StatSource.of("arena", player ->
                CompletableFuture.failedFuture(new IllegalStateException("database down"))));

        assertEquals(7L, stats.stats(ANNA).join().value("arena.wins"));
        assertEquals(List.of("arena"), problems);

        failing.unsubscribe();
        stats.registerSource(StatSource.of("arena", player -> new CompletableFuture<>()));
        stats.registerSource(new StatSource() {
            @Override
            public String game() {
                return "broken";
            }

            @Override
            public CompletableFuture<Map<String, Long>> values(UUID player) {
                throw new IllegalStateException("not ready");
            }

            @Override
            public Set<StatPeriod> periods() {
                return null;
            }
        });

        assertEquals(7L, stats.stats(ANNA).join().value("arena.wins"));
        assertTrue(problems.containsAll(List.of("arena", "broken")));
    }

    @Test
    void anUnregisteredSourceLeavesItsMirroredValues() {
        DocumentStatsService stats = service(List.of());
        Subscription source = stats.registerSource(arenaSource());
        arena.put(ANNA, Map.of("wins", 4L));
        stats.stats(ANNA).join();

        source.unsubscribe();
        arena.put(ANNA, Map.of("wins", 9L));

        assertTrue(stats.source("arena").isEmpty());
        assertFalse(stats.hasSources());
        assertEquals(4L, stats.stats(ANNA).join().value("arena.wins"));
    }

    @Test
    void periodValuesOfASourceAreUsed() {
        DocumentStatsService stats = service(List.of());
        stats.registerSource(new StatSource() {
            @Override
            public String game() {
                return "arena";
            }

            @Override
            public CompletableFuture<Map<String, Long>> values(UUID player) {
                return CompletableFuture.completedFuture(Map.of("wins", 40L));
            }

            @Override
            public Set<StatPeriod> periods() {
                return Set.of(StatPeriod.ALL_TIME, StatPeriod.WEEKLY);
            }

            @Override
            public CompletableFuture<Map<String, Long>> values(UUID player, StatPeriod period) {
                return period == StatPeriod.WEEKLY ? CompletableFuture.completedFuture(Map.of("wins", 6L))
                        : values(player);
            }
        });

        PlayerStats anna = stats.stats(ANNA).join();

        assertEquals(6L, anna.value("arena.wins", StatPeriod.WEEKLY));
        assertEquals(0L, anna.value("arena.wins", StatPeriod.MONTHLY));
        assertEquals(6L, service(List.of()).top("arena.wins", StatPeriod.WEEKLY, 5).join().entries().getFirst()
                .value());
    }

    @Test
    void registeredGamesAndDefinitionsReachTheOtherServers() {
        DocumentStatsService bedwarsServer = service(List.of());
        bedwarsServer.defineGame(StatGame.of("bedwars", "Bed Wars").withIcon("RED_BED").withColor("#F87171")
                .withOrder(3).withFeatured("kd"));
        bedwarsServer.define(new StatDefinition("bedwars.kd", NoticeText.of("K/D"), StatFormat.DECIMAL,
                StatOrder.HIGHEST, true, false));
        DocumentStatsService lobby = service(List.of(GameConfig.named("bedwars", NoticeText.of("Bettkrieg"))));

        assertEquals("", lobby.game("bedwars").icon());
        assertEquals(StatFormat.NUMBER, lobby.definition("bedwars.kd").format());

        StatCatalog.Loaded loaded = lobby.loadCatalog().join();

        assertEquals(1, loaded.games().size());
        StatGame bedwars = lobby.game("bedwars");
        assertEquals("RED_BED", bedwars.icon());
        assertEquals(List.of("bedwars.kd"), bedwars.featured());
        assertEquals("Bettkrieg", bedwars.name().template(Locale.ENGLISH));
        assertEquals(StatFormat.DECIMAL, lobby.definition("bedwars.kd").format());
        assertEquals("1.25", lobby.format("bedwars.kd", 125L, Locale.ENGLISH));
        assertTrue(lobby.known("bedwars.kd"));
        assertTrue(lobby.definitions().stream().anyMatch(definition -> definition.key().equals("bedwars.kd")));
        assertTrue(lobby.games().stream().anyMatch(game -> game.id().equals("bedwars")));
        assertEquals(StatCatalog.Loaded.EMPTY.games(), StatCatalog.decode(List.of("{broken", "{\"kind\":\"game\","
                + "\"id\":\"bad id\"}")).games());
    }
}
