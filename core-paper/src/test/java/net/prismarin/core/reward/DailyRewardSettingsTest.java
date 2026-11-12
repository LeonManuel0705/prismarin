package net.prismarin.core.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import net.prismarin.api.reward.Reward;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DailyRewardSettingsTest {

    @TempDir
    Path directory;

    @Test
    void theDefaultsGiveAWeekWithABiggerSeventhDay() throws IOException {
        Path file = directory.resolve(DailyRewardSettings.FILE_NAME);

        DailyRewardSettings settings = DailyRewardSettings.load(file);

        assertTrue(settings.problems().isEmpty(), settings.problems().toString());
        assertEquals(7, settings.schedule().cycle());
        assertEquals(Map.of("coins", 50L), settings.schedule().reward(1).currencies());
        Reward seventh = settings.schedule().reward(7);
        assertEquals(Map.of("coins", 300L, "gems", 5L), seventh.currencies());
        assertEquals(175L, seventh.xp());
        assertTrue(seventh.messages().getFirst().template(Locale.GERMAN).contains("Woche"));
        assertEquals(Map.of("coins", 75L, "gems", 25L), settings.schedule().reward(30).currencies());
        assertTrue(settings.remindOnJoin());
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("# Daily rewards with a login streak."));
        assertEquals(settings, DailyRewardSettings.load(file));
    }

    @Test
    void ownSchedulesAndClocksAreRead() throws IOException {
        Path file = directory.resolve(DailyRewardSettings.FILE_NAME);
        Files.writeString(file, """
                time-zone: America/New_York
                reset-time: "05:30"
                grace-days: 2
                reminder:
                  on-join: false
                cycle: 3
                days:
                  "2":
                    xp: 10
                  "5":
                    xp: 99
                  zero:
                    xp: 1
                default:
                  currencies:
                    coins: 5
                milestones:
                  "10":
                    levels: 1
                """, StandardCharsets.UTF_8);

        DailyRewardSettings settings = DailyRewardSettings.load(file);

        assertEquals(ZoneId.of("America/New_York"), settings.clock().zone());
        assertEquals(LocalTime.of(5, 30), settings.clock().reset());
        assertEquals(2, settings.graceDays());
        assertFalse(settings.remindOnJoin());
        assertEquals(3, settings.schedule().cycle());
        assertEquals(10L, settings.schedule().reward(2).xp());
        assertEquals(Map.of("coins", 5L), settings.schedule().reward(3).currencies());
        assertEquals(1, settings.schedule().reward(10).levels());
        assertEquals(2, settings.problems().size(), settings.problems().toString());
    }
}
