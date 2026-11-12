package net.prismarin.core.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.prismarin.api.stats.StatFormat;
import net.prismarin.api.stats.StatPeriod;
import net.prismarin.shared.event.SimpleEventBus;
import net.prismarin.shared.stats.DocumentStatsService;
import net.prismarin.shared.stats.StatNumbers;
import net.prismarin.shared.storage.JsonFileDocumentStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlaceholderStatsTest {

    private static final UUID LEON = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @TempDir
    Path directory;

    private record FakePapi(Set<String> expansions, Map<String, String> values) implements PlaceholderStats.Resolver {

        @Override
        public boolean available(String identifier) {
            return expansions.contains(identifier);
        }

        @Override
        public String resolve(UUID player, String text) {
            String result = text;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                result = result.replace(entry.getKey(), entry.getValue());
            }
            return result;
        }
    }

    private static PlaceholderStats stats() {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("bedwars.wins", "%bedwars_stats_wins%");
        placeholders.put("bedwars.kd", "%bedwars_stats_kd%");
        placeholders.put("parkour.best-time", "%parkour_best_time%");
        placeholders.put("skywars.wins", "%skywars_wins%");
        placeholders.put("bedwars.level", "Level %bedwars_level%");
        Map<String, StatFormat> formats = Map.of("bedwars.kd", StatFormat.DECIMAL, "parkour.best-time",
                StatFormat.MILLIS);
        return new PlaceholderStats(placeholders, key -> formats.getOrDefault(key, StatFormat.NUMBER),
                StatNumbers.Decimal.AUTO);
    }

    @Test
    void theIdentifierIsThePartBeforeTheFirstUnderscore() {
        assertEquals(Optional.of("bedwars"), PlaceholderStats.identifier("%BedWars_stats_wins%"));
        assertEquals(Optional.of("player"), PlaceholderStats.identifier("Kills: %player_statistic_kills%"));
        assertEquals(Optional.empty(), PlaceholderStats.identifier("%nounderscore%"));
        assertEquals(Optional.empty(), PlaceholderStats.identifier("bedwars_stats_wins"));
        assertEquals(Optional.empty(), PlaceholderStats.identifier(null));
    }

    @Test
    void valuesAreParsedInTheFormatOfTheStat() {
        FakePapi papi = new FakePapi(Set.of("bedwars", "parkour"), Map.of("%bedwars_stats_wins%", "1,234",
                "%bedwars_stats_kd%", "1.25", "%parkour_best_time%", "1:02.345", "%bedwars_level%", "7"));

        Map<String, Long> values = stats().read(LEON, papi);

        assertEquals(Map.of("bedwars.wins", 1_234L, "bedwars.kd", 125L, "parkour.best-time", 62_345L,
                "bedwars.level", 7L), values);
    }

    @Test
    void missingPluginsAndUnresolvedPlaceholdersGiveNoValue() {
        FakePapi without = new FakePapi(Set.of("skywars"), Map.of("%bedwars_stats_wins%", "12"));
        FakePapi unresolved = new FakePapi(Set.of("bedwars", "parkour", "skywars"),
                Map.of("%parkour_best_time%", "", "%skywars_wins%", "N/A", "%bedwars_stats_kd%", "Loading..."));
        PlaceholderStats.Resolver failing = new PlaceholderStats.Resolver() {
            @Override
            public boolean available(String identifier) {
                return true;
            }

            @Override
            public String resolve(UUID player, String text) {
                throw new IllegalStateException("expansion crashed");
            }
        };

        assertTrue(stats().read(LEON, without).isEmpty());
        assertTrue(stats().read(LEON, unresolved).isEmpty());
        assertTrue(stats().read(LEON, failing).isEmpty());
        assertTrue(stats().read(LEON, null).isEmpty());
        assertTrue(new PlaceholderStats(Map.of(), key -> StatFormat.NUMBER, null).empty());
    }

    @Test
    void aServerWithoutThePluginNeverOverwritesTheValue() {
        JsonFileDocumentStore store = new JsonFileDocumentStore(directory);
        DocumentStatsService bedwarsServer = service(store);
        DocumentStatsService lobby = service(store);
        FakePapi withPlugin = new FakePapi(Set.of("bedwars"), Map.of("%bedwars_stats_wins%", "12"));
        FakePapi withoutPlugin = new FakePapi(Set.of(), Map.of());

        bedwarsServer.mirror(LEON, stats().read(LEON, withPlugin)).join();
        lobby.mirror(LEON, stats().read(LEON, withoutPlugin)).join();

        assertEquals(12L, lobby.stats(LEON).join().value("bedwars.wins"));
        assertEquals(List.of(LEON), lobby.top("bedwars.wins", StatPeriod.ALL_TIME, 3).join().entries().stream()
                .map(entry -> entry.player()).toList());

        FakePapi later = new FakePapi(Set.of("bedwars"), Map.of("%bedwars_stats_wins%", "15"));
        bedwarsServer.mirror(LEON, stats().read(LEON, later)).join();

        assertEquals(15L, lobby.stats(LEON).join().value("bedwars.wins"));
        assertEquals(3L, lobby.stats(LEON).join().value("bedwars.wins", StatPeriod.WEEKLY));
    }

    private static DocumentStatsService service(JsonFileDocumentStore store) {
        return new DocumentStatsService(store, Runnable::run, new SimpleEventBus(),
                Clock.fixed(Instant.parse("2026-11-12T12:00:00Z"), ZoneOffset.UTC),
                DocumentStatsService.Settings.defaults(), DocumentStatsService.Names.none());
    }
}
