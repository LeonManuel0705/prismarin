package net.prismarin.velocity.joinme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import net.prismarin.api.joinme.JoinMeAudience;
import net.prismarin.api.skin.ChatFace;
import net.prismarin.shared.skin.SkinFaceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JoinMeSettingsTest {

    @TempDir
    Path directory;

    @Test
    void theDefaultsAreWrittenWithCommentsAndReadBackTheSame() throws IOException {
        Path file = directory.resolve(JoinMeSettings.FILE_NAME);

        JoinMeSettings settings = JoinMeSettings.load(file);

        assertTrue(settings.problems().isEmpty(), settings.problems().toString());
        assertEquals(120L, settings.create().lifetimeSeconds());
        assertEquals(0, settings.create().maxJoins());
        assertFalse(settings.create().groupAllowed("lobby"));
        assertTrue(settings.create().groupAllowed("bedwars"));
        assertTrue(settings.create().joinableStateOnly());
        assertTrue(settings.create().endWhenCreatorLeaves());
        assertEquals(Map.of("default", 300L, "premium", 180L, "youtuber", 60L), settings.cooldowns().values());
        assertEquals(Map.of("default", new JoinMeSettings.Cost("coins", 0L)), settings.costs().values());
        assertTrue(settings.costs().values().get("default").free());
        assertEquals(Map.of("youtuber", 300L), settings.lifetimes().values());
        assertEquals(Map.of(), settings.maxJoins().values());
        assertTrue(settings.join().sameServer());
        assertEquals(300_000L, settings.join().arrivalTimeoutMillis());
        assertEquals(JoinMeAudience.groups("lobby"), settings.audience().audience());
        assertTrue(settings.audience().skipSameWorld());
        assertEquals("entity.experience_orb.pickup", settings.broadcast().sound());
        assertFalse(settings.broadcast().title());
        assertTrue(settings.face().enabled());
        assertEquals(ChatFace.SYMBOL, settings.face().symbol());
        assertFalse(settings.face().textWithoutSkin());
        assertEquals(Map.of(2, "joinme.face.title", 4, "joinme.face.details", 5, "joinme.face.state",
                7, "joinme.face.button"), settings.face().rows());
        assertEquals(SkinFaceService.Settings.DEFAULT, settings.face().cache());
        String text = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(text.contains("# Seconds between two /joinme per player."), text);
        assertTrue(text.contains("# Draws the creator's skin face"), text);
        assertTrue(text.contains("blocked-groups:"), text);
        assertEquals(settings, JoinMeSettings.load(file));
    }

    @Test
    void ownValuesAndTiersAreRead() throws IOException {
        Path file = directory.resolve(JoinMeSettings.FILE_NAME);
        Files.writeString(file, """
                create:
                  lifetime-seconds: 600
                  max-joins: 10
                  allowed-groups: [BedWars, SkyWars]
                  blocked-groups: []
                cooldowns:
                  default: 900
                  vip: 30
                costs:
                  default:
                    currency: gems
                    amount: 5
                max-joins-tiers:
                  vip: 0
                join:
                  same-server: false
                audience:
                  scope: network
                  only: [friends, party]
                broadcast:
                  title: true
                  sound: ""
                face:
                  symbol: "#"
                  without-skin: text
                  blank-line-before: false
                  gap: "   "
                  rows:
                    1: "joinme.face.title"
                    9: "<gold>below the face</gold>"
                  cache-size: 5
                  disk-cache: false
                """, StandardCharsets.UTF_8);

        JoinMeSettings settings = JoinMeSettings.load(file);

        assertTrue(settings.problems().isEmpty(), settings.problems().toString());
        assertEquals(600L, settings.create().lifetimeSeconds());
        assertEquals(10, settings.create().maxJoins());
        assertTrue(settings.create().groupAllowed("bedwars"));
        assertFalse(settings.create().groupAllowed("lobby"));
        assertEquals(30L, settings.cooldowns().values().get("vip"));
        assertEquals(new JoinMeSettings.Cost("gems", 5L), settings.costs().values().get("default"));
        assertEquals(Map.of("vip", 0), settings.maxJoins().values());
        assertFalse(settings.join().sameServer());
        assertEquals(JoinMeAudience.Scope.NETWORK, settings.audience().audience().scope());
        assertEquals(Set.of(JoinMeAudience.Circle.FRIENDS, JoinMeAudience.Circle.PARTY),
                settings.audience().audience().circles());
        assertTrue(settings.broadcast().title());
        assertEquals("", settings.broadcast().sound());
        assertEquals("#", settings.face().symbol());
        assertTrue(settings.face().textWithoutSkin());
        assertFalse(settings.face().blankBefore());
        assertTrue(settings.face().blankAfter());
        assertEquals("   ", settings.face().gap());
        assertEquals(Map.of(1, "joinme.face.title", 9, "<gold>below the face</gold>"), settings.face().rows());
        assertEquals(new SkinFaceService.Settings(5, false, 3_000L), settings.face().cache());
    }

    @Test
    void brokenValuesAreReportedAndReplaced() throws IOException {
        Path file = directory.resolve(JoinMeSettings.FILE_NAME);
        Files.writeString(file, """
                cooldowns:
                  "bad tier": 10
                costs:
                  default:
                    currency: "Coins!"
                    amount: 5
                audience:
                  scope: everywhere
                  groups: []
                  only: [guild]
                broadcast:
                  sound: "Not A Sound"
                face:
                  symbol: "three"
                  without-skin: nothing
                  rows:
                    20: "joinme.face.title"
                    top: "joinme.face.title"
                """, StandardCharsets.UTF_8);

        JoinMeSettings settings = JoinMeSettings.load(file);

        assertEquals(10, settings.problems().size(), settings.problems().toString());
        assertEquals(ChatFace.SYMBOL, settings.face().symbol());
        assertFalse(settings.face().textWithoutSkin());
        assertTrue(settings.face().rows().isEmpty());
        assertEquals(JoinMeAudience.Scope.NETWORK, settings.audience().audience().scope());
        assertTrue(settings.audience().audience().circles().isEmpty());
        assertEquals("", settings.broadcast().sound());
        assertTrue(settings.costs().values().isEmpty());
        assertFalse(settings.cooldowns().values().containsKey("bad tier"));
    }
}
