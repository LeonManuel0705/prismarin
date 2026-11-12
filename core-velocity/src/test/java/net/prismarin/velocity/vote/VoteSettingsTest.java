package net.prismarin.velocity.vote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.prismarin.shared.reward.VoteLedger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VoteSettingsTest {

    @TempDir
    Path directory;

    @Test
    void theDefaultsKeepTheExampleSiteHidden() throws IOException {
        Path file = directory.resolve(VoteSettings.FILE_NAME);

        VoteSettings settings = VoteSettings.load(file);

        assertTrue(settings.problems().isEmpty(), settings.problems().toString());
        assertTrue(settings.sites().isEmpty());
        assertTrue(settings.votifier());
        assertTrue(settings.broadcast());
        assertEquals(30L * 86_400_000L, settings.keepMillis());
        assertEquals(Map.of("coins", 100L), settings.rules().reward("any", null).currencies());
        assertEquals(50L, settings.rules().reward("any", null).xp());
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("example:"));
        assertEquals(settings, VoteSettings.load(file));
    }

    @Test
    void sitesWithTheirOwnRewardsAndMilestones() throws IOException {
        Path file = directory.resolve(VoteSettings.FILE_NAME);
        Files.writeString(file, """
                sites:
                  eu:
                    name: "Minecraft-Server.eu"
                    url: "https://minecraft-server.eu/vote/index/abc"
                    service: "MinecraftServerEU"
                    reward:
                      currencies:
                        coins: 250
                  broken:
                    url: "minecraft-server.eu"
                reward:
                  currencies:
                    coins: 100
                streak:
                  grace-days: 1
                  milestones:
                    "3":
                      currencies:
                        gems: 2
                reminder:
                  interval-minutes: 30
                queue:
                  keep-days: 0
                """, StandardCharsets.UTF_8);

        VoteSettings settings = VoteSettings.load(file);

        assertEquals(1, settings.sites().size());
        assertEquals("Minecraft-Server.eu", settings.site("minecraftservereu").orElseThrow().name());
        assertEquals("eu", settings.site("EU").orElseThrow().id());
        assertEquals(1, settings.problems().size(), settings.problems().toString());
        VoteLedger.Counted third = new VoteLedger.Counted(3L, 3, 3, true, 0);
        assertEquals(Map.of("coins", 250L, "gems", 2L),
                settings.rules().reward("MinecraftServerEU", third).currencies());
        assertEquals(Map.of("coins", 100L), settings.rules().reward("other", null).currencies());
        assertEquals(1, settings.graceDays());
        assertEquals(30, settings.reminder().intervalMinutes());
        assertEquals(0L, settings.keepMillis());
        assertFalse(settings.site("").isPresent());
    }
}
