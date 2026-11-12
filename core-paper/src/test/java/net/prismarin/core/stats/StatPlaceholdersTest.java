package net.prismarin.core.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.stats.StatDefinition;
import net.prismarin.api.stats.StatFormat;
import net.prismarin.api.stats.StatOrder;
import net.prismarin.api.stats.StatSource;
import net.prismarin.shared.event.SimpleEventBus;
import net.prismarin.shared.stats.DocumentStatsService;
import net.prismarin.shared.stats.StatPeriods;
import net.prismarin.shared.storage.JsonFileDocumentStore;
import net.prismarin.shared.time.DayClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StatPlaceholdersTest {

    private static final UUID LEON = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BEN = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @TempDir
    Path directory;

    private DocumentStatsService stats;
    private StatPlaceholders placeholders;

    @BeforeEach
    void setUp() {
        StatDefinition time = new StatDefinition("parkour.time", null, StatFormat.MILLIS, StatOrder.LOWEST, true,
                false);
        stats = new DocumentStatsService(new JsonFileDocumentStore(directory), Runnable::run, new SimpleEventBus(),
                Clock.systemUTC(), new DocumentStatsService.Settings(new StatPeriods(DayClock.utc(), DayOfWeek.MONDAY,
                true, false), 60_000L, 100, false, true, List.of(time)), new DocumentStatsService.Names() {
                    @Override
                    public Optional<String> known(UUID player) {
                        return Optional.of(player.equals(LEON) ? "Leon" : "Ben");
                    }

                    @Override
                    public CompletableFuture<Map<UUID, String>> shown(List<UUID> players) {
                        return CompletableFuture.completedFuture(Map.of());
                    }
                });
        placeholders = new StatPlaceholders(stats, player -> Locale.GERMAN);
        stats.track(LEON).join();
        stats.increment(LEON, "bedwars.wins", 1_500L).join();
        stats.increment(BEN, "bedwars.wins", 2_000L).join();
        stats.record(LEON, "parkour.time", 83_250L).join();
    }

    @Test
    void valuesAreFormattedForThePlayer() {
        assertEquals("1.500", placeholders.resolve(LEON, "stat_bedwars.wins"));
        assertEquals("1500", placeholders.resolve(LEON, "stat_bedwars.wins_raw"));
        assertEquals("1.500", placeholders.resolve(LEON, "stat_bedwars.wins_weekly"));
        assertEquals("1:23.250", placeholders.resolve(LEON, "stat_parkour.time"));
        assertEquals("0", placeholders.resolve(LEON, "stat_unknown.stat"));
    }

    @Test
    void ranksAndTopListsComeFromTheCache() {
        placeholders.resolve(LEON, "top_bedwars.wins_1_name");

        assertEquals("Ben", placeholders.resolve(LEON, "top_bedwars.wins_1_name"));
        assertEquals("2.000", placeholders.resolve(null, "top_bedwars.wins_1_value"));
        assertEquals("1500", placeholders.resolve(null, "top_bedwars.wins_2_raw"));
        assertEquals(BEN.toString(), placeholders.resolve(null, "top_bedwars.wins_1_uuid"));
        assertEquals("", placeholders.resolve(null, "top_bedwars.wins_3_name"));
        assertEquals("2", placeholders.resolve(LEON, "stat_bedwars.wins_rank"));
    }

    @Test
    void switchedOffPeriodsAndOtherPlaceholders() {
        assertEquals("", placeholders.resolve(LEON, "stat_bedwars.wins_monthly"));
        assertEquals("", placeholders.resolve(LEON, "top_bedwars.wins_monthly_1_name"));
        assertNull(placeholders.resolve(LEON, "balance"));
        assertEquals("", placeholders.resolve(null, "stat_bedwars.wins"));
    }

    @Test
    void pullSourcesShowUpInThePlaceholders() {
        stats.registerSource(StatSource.of("arena", player -> CompletableFuture.completedFuture(
                player.equals(LEON) ? Map.of("wins", 4_200L) : Map.of("wins", 9_000L))));
        stats.track(LEON).join();
        stats.stats(BEN).join();

        assertEquals("4.200", placeholders.resolve(LEON, "stat_arena.wins"));
        placeholders.resolve(LEON, "top_arena.wins_1_name");
        assertEquals("Ben", placeholders.resolve(LEON, "top_arena.wins_1_name"));
        assertEquals("2", placeholders.resolve(LEON, "stat_arena.wins_rank"));
    }
}
