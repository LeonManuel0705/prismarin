package net.prismarin.core.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.prismarin.api.stats.StatDefinition;
import net.prismarin.api.stats.StatFormat;
import net.prismarin.api.stats.StatGame;
import net.prismarin.api.stats.StatOrder;
import net.prismarin.shared.stats.GameConfig;
import net.prismarin.shared.stats.StatNumbers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StatsSettingsTest {

    @TempDir
    Path directory;

    @Test
    void theFirstStartWritesCommentedDefaults() throws IOException {
        Path file = directory.resolve(StatsSettings.FILE_NAME);

        StatsSettings settings = StatsSettings.load(file);

        assertTrue(settings.problems().isEmpty(), settings.problems().toString());
        String text = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(text.startsWith("# Statistics and leaderboards."));
        assertTrue(text.contains("daily.streak:"));
        assertEquals(60, settings.refreshSeconds());
        assertTrue(settings.periods().weekly());
        assertEquals("votes", settings.builtIn().votesKey().orElseThrow());
        StatDefinition votes = settings.definitions().getFirst();
        assertEquals("votes", votes.key());
        assertEquals("Stimmen", votes.name().template(Locale.GERMAN));
        assertTrue(votes.inProfile());
        assertEquals("Allgemein", settings.game("general").orElseThrow().name().template(Locale.GERMAN));
        assertEquals(settings, StatsSettings.load(file));
        assertEquals(List.of("general", "daily", "example"), settings.gameIds());
        StatGame example = settings.game("example").orElseThrow().apply(null);
        assertEquals("DIAMOND_SWORD", example.icon());
        assertEquals(List.of("example.wins"), example.featured());
        assertEquals(List.of("votes"), settings.game("general").orElseThrow().apply(null).featured());
        assertEquals(new StatsSettings.PlaceholderSync(5, 10, StatNumbers.Decimal.AUTO), settings.placeholderSync());
        assertEquals(new StatsSettings.Command(10, 3), settings.command());
        assertEquals(3_000L, settings.sourceTimeoutMillis());
        assertTrue(settings.placeholders().isEmpty());
        assertTrue(text.contains("placeholder-sync-minutes: 5"));
        assertTrue(text.contains("# Stats with a placeholder are read"));
    }

    @Test
    void ownValuesAreRead() throws IOException {
        Path file = directory.resolve(StatsSettings.FILE_NAME);
        Files.writeString(file, """
                periods:
                  time-zone: Europe/Berlin
                  reset-time: "06:00"
                  week-starts: sunday
                  monthly: false
                leaderboards:
                  refresh-seconds: 2
                  shared-ranks: true
                built-in:
                  votes: ""
                stats:
                  parkour.time:
                    name: "stats.names.parkour"
                    format: millis
                    order: lowest
                """, StandardCharsets.UTF_8);

        StatsSettings settings = StatsSettings.load(file);

        assertEquals(ZoneId.of("Europe/Berlin"), settings.periods().clock().zone());
        assertEquals(LocalTime.of(6, 0), settings.periods().clock().reset());
        assertEquals(DayOfWeek.SUNDAY, settings.periods().weekStart());
        assertFalse(settings.periods().monthly());
        assertEquals(5, settings.refreshSeconds());
        assertTrue(settings.sharedRanks());
        assertTrue(settings.builtIn().votesKey().isEmpty());
        StatDefinition time = settings.definitions().getFirst();
        assertEquals(StatFormat.MILLIS, time.format());
        assertEquals(StatOrder.LOWEST, time.order());
        assertEquals("stats.names.parkour", time.name().template(Locale.ENGLISH));
    }

    @Test
    void mistakesAreReportedAndReplaced() throws IOException {
        Path file = directory.resolve(StatsSettings.FILE_NAME);
        Files.writeString(file, """
                periods:
                  time-zone: Mars/Base
                  reset-time: noon
                  week-starts: someday
                stats:
                  Bad Key:
                    format: percent
                  kills:
                    format: percent
                    order: sideways
                """, StandardCharsets.UTF_8);

        StatsSettings settings = StatsSettings.load(file);

        assertEquals(6, settings.problems().size(), settings.problems().toString());
        assertEquals(LocalTime.MIDNIGHT, settings.periods().clock().reset());
        assertEquals(StatFormat.NUMBER, settings.definitions().getFirst().format());
    }

    @Test
    void placeholderStatsAndGameMetadataAreRead() throws IOException {
        Path file = directory.resolve(StatsSettings.FILE_NAME);
        Files.writeString(file, """
                placeholder-sync-minutes: 2
                placeholder-join-delay-seconds: 30
                placeholder-decimal-separator: ","
                sources:
                  timeout-millis: 1000
                command:
                  page-size: 6
                  game-list-above: 5
                games:
                  bedwars:
                    name:
                      en: Bed Wars
                      de: Bettkrieg
                    icon: red_bed
                    color: "#F87171"
                    order: 1
                    featured: [wins, final-kills]
                  skywars:
                    color: "not a colour"
                stats:
                  bedwars.wins:
                    placeholder: "%bedwars_stats_wins%"
                  bedwars.level:
                    placeholder: "Level %bedwars_level%"
                  bedwars.broken:
                    placeholder: "no placeholder here"
                """, StandardCharsets.UTF_8);

        StatsSettings settings = StatsSettings.load(file);

        assertEquals(Map.of("bedwars.wins", "%bedwars_stats_wins%", "bedwars.level", "Level %bedwars_level%"),
                settings.placeholders());
        assertEquals(new StatsSettings.PlaceholderSync(2, 30, StatNumbers.Decimal.COMMA), settings.placeholderSync());
        assertEquals(new StatsSettings.Command(6, 5), settings.command());
        assertEquals(1_000L, settings.sourceTimeoutMillis());
        GameConfig bedwars = settings.game("bedwars").orElseThrow();
        StatGame game = bedwars.apply(StatGame.of("bedwars").withIcon("BED"));
        assertEquals("Bettkrieg", game.name().template(Locale.GERMAN));
        assertEquals("RED_BED", game.icon());
        assertEquals("#f87171", game.color());
        assertEquals(1, game.order());
        assertEquals(List.of("bedwars.wins", "bedwars.final-kills"), game.featured());
        assertEquals("", settings.game("skywars").orElseThrow().apply(null).color());
        assertEquals(2, settings.problems().size(), settings.problems().toString());
        assertTrue(settings.service().games().contains(bedwars));
    }
}
