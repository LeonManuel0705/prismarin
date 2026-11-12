package net.prismarin.api.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class StatGameTest {

    @Test
    void unknownKeysGetReadableNames() {
        assertEquals("Final kills", StatKeys.humanize("bedwars.final-kills"));
        assertEquals("Solo wins", StatKeys.humanize("bedwars.solo.wins"));
        assertEquals("Votes", StatKeys.humanize("votes"));
        assertEquals("Bedwars", StatKeys.gameName("bedwars"));
        assertEquals("Sky wars", StatKeys.gameName("sky-wars"));
        assertEquals("Bedwars", StatGame.of("BedWars").name().template(Locale.GERMAN));
        assertEquals("Final kills", StatDefinition.of("bedwars.final-kills").name().template(Locale.ENGLISH));
    }

    @Test
    void keysAreAssignedToTheirGame() {
        assertEquals("bedwars.wins", StatKeys.inGame("bedwars", "wins"));
        assertEquals("bedwars.wins", StatKeys.inGame("bedwars", "BedWars.Wins"));
        assertEquals("bedwars.solo.wins", StatKeys.inGame("bedwars", "solo.wins"));
        assertEquals("votes", StatKeys.inGame("general", "votes"));
        assertTrue(StatKeys.validGame("sky-wars"));
        assertFalse(StatKeys.validGame("sky.wars"));
        assertThrows(IllegalArgumentException.class, () -> StatKeys.gameId("bed wars"));
    }

    @Test
    void numberFormatsAreGuessedFromUnits() {
        assertEquals(StatFormat.MILLIS, StatFormat.guess("parkour.best-millis"));
        assertEquals(StatFormat.MILLIS, StatFormat.guess("race.lap-ms"));
        assertEquals(StatFormat.DURATION, StatFormat.guess("survival.played-seconds"));
        assertEquals(StatFormat.NUMBER, StatFormat.guess("parkour.time"));
        assertEquals(StatFormat.NUMBER, StatFormat.guess("bedwars.kills"));
        assertEquals(StatFormat.NUMBER, StatFormat.guess("not a key"));
    }

    @Test
    void gameMetadataIsCheckedAndNormalized() {
        StatGame game = StatGame.of("bedwars", "Bed Wars").withName("de", "Bettkrieg").withIcon("minecraft:red_bed")
                .withColor("#F87171").withOrder(5).withFeatured("wins", "bedwars.final-kills", "wins");

        assertEquals("Bettkrieg", game.name().template(Locale.GERMAN));
        assertEquals("Bed Wars", game.name().template(Locale.ENGLISH));
        assertEquals("RED_BED", game.icon());
        assertEquals("#f87171", game.color());
        assertEquals(5, game.order());
        assertEquals(List.of("bedwars.wins", "bedwars.final-kills"), game.featured());
        assertEquals(0, game.featuredIndex("bedwars.wins"));
        assertEquals(Integer.MAX_VALUE, game.featuredIndex("bedwars.beds"));
        assertTrue(game.hasIcon() && game.hasColor());
        assertFalse(StatGame.of("skywars").hasColor());
        assertEquals(StatGame.DEFAULT_ORDER, StatGame.of("skywars").order());
        assertThrows(IllegalArgumentException.class, () -> StatGame.of("bedwars").withIcon("red bed"));
        assertThrows(IllegalArgumentException.class, () -> StatGame.of("bedwars").withColor("#12"));
    }

    @Test
    void aSourceCanBeALambda() {
        UUID player = UUID.randomUUID();
        StatSource source = StatSource.of("Arena", uuid -> CompletableFuture.completedFuture(Map.of("wins", 3L)));

        assertEquals("arena", source.game());
        assertEquals(Map.of("wins", 3L), source.values(player).join());
        assertEquals(Map.of(), source.values(player, StatPeriod.WEEKLY).join());
        assertEquals(Map.of("wins", 3L), source.values(player, StatPeriod.ALL_TIME).join());
        assertFalse(source.topLists());
        assertEquals(List.of(), source.top("arena.wins", StatPeriod.ALL_TIME, 10).join());
        assertThrows(IllegalArgumentException.class, () -> StatSource.of("a.b", uuid -> null));
    }
}
