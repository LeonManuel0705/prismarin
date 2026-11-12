package net.prismarin.shared.joinme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;
import net.prismarin.api.joinme.JoinMe;
import net.prismarin.api.joinme.JoinMeAudience;
import net.prismarin.api.joinme.JoinMeCreatedEvent;
import net.prismarin.api.joinme.JoinMeExpiredEvent;
import net.prismarin.api.joinme.JoinMeRequest;
import net.prismarin.api.joinme.JoinMeResult;
import net.prismarin.api.joinme.JoinMeSpot;
import net.prismarin.api.server.GameState;
import net.prismarin.shared.network.NetworkCalls;
import net.prismarin.shared.network.NetworkEvents;
import org.junit.jupiter.api.Test;

class JoinMeCodecTest {

    private final UUID creator = UUID.randomUUID();
    private final JoinMeAudience audience = JoinMeAudience.groups("lobby", "premium")
            .only(JoinMeAudience.Circle.FRIENDS, JoinMeAudience.Circle.CLAN);
    private final JoinMe joinMe = new JoinMe("k3f9a2", creator, "Lea", "Shadow", "bedwars-2", "bedwars", "arena-1",
            GameState.STARTING, "bedwars", Map.of("map", "Castle", "mode", "4x2"), audience, 8, 3, 1_000L, 61_000L);

    private static JsonObject wire(JsonObject object) {
        return JsonParser.parseString(object.toString()).getAsJsonObject();
    }

    @Test
    void aJoinMeSurvivesTheWire() {
        assertEquals(Optional.of(joinMe), JoinMeCodec.joinMe(wire(JoinMeCodec.encode(joinMe))));
    }

    @Test
    void brokenJoinMesAreDropped() {
        JsonObject bad = JoinMeCodec.encode(joinMe);
        bad.addProperty("id", "NO SPACES");
        assertEquals(Optional.empty(), JoinMeCodec.joinMe(bad));
        JsonObject noCreator = JoinMeCodec.encode(joinMe);
        noCreator.addProperty("creator", "nobody");
        assertEquals(Optional.empty(), JoinMeCodec.joinMe(noCreator));
        assertEquals(Optional.empty(), JoinMeCodec.joinMe(null));
        JsonObject unknownState = JoinMeCodec.encode(joinMe);
        unknownState.addProperty("state", "dancing");
        assertEquals(GameState.UNKNOWN, JoinMeCodec.joinMe(unknownState).orElseThrow().state());
    }

    @Test
    void placeholdersAreCleanedLimitedAndClipped() {
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("Map", "Castle\nof\tDoom");
        raw.put("bad key", "x");
        raw.put("long", "x".repeat(500));
        for (int index = 0; index < 30; index++) {
            raw.put("extra" + index, "v");
        }
        Map<String, String> clean = JoinMeCodec.cleanPlaceholders(raw);
        assertEquals("Castle of Doom", clean.get("map"));
        assertFalse(clean.containsKey("bad key"));
        assertEquals(JoinMeCodec.MAX_VALUE, clean.get("long").length());
        assertEquals(JoinMeCodec.MAX_PLACEHOLDERS, clean.size());
    }

    @Test
    void draftsCarryEveryChoiceOfTheGameServer() {
        JoinMeSpot spot = new JoinMeSpot(creator, "bedwars-2", "bedwars", "arena-1", GameState.WAITING);
        JoinMeDraft draft = JoinMeDraft.of(spot, JoinMeRequest.of(creator).withMaxJoins(4).ignoringCooldown())
                .withShownName("Shadow").withHandler("bedwars", true).withPlaceholders(Map.of("map", "Castle"));
        JoinMeDraft decoded = JoinMeCodec.draft(wire(JoinMeCodec.encode(draft))).orElseThrow();
        assertEquals(draft, decoded);
        assertTrue(decoded.handled());
        assertTrue(decoded.worldFallback());
        assertEquals(4, decoded.maxJoins());
        assertEquals(null, decoded.audience());
        JoinMeDraft configured = JoinMeDraft.of(spot, JoinMeRequest.of(creator));
        assertEquals(JoinMeRequest.CONFIGURED, JoinMeCodec.draft(wire(JoinMeCodec.encode(configured))).orElseThrow()
                .maxJoins());
        JoinMeDraft finished = configured.withTerms(90_000L, 0, audience);
        assertEquals(audience, JoinMeCodec.draft(wire(JoinMeCodec.encode(finished))).orElseThrow().audience());
    }

    @Test
    void termsAndResultsSurviveTheWire() {
        JoinMeTerms terms = new JoinMeTerms(JoinMeResult.Status.SUCCESS, 0L, "coins", 50L, 120_000L, 5, audience,
                false);
        assertEquals(terms, JoinMeCodec.terms(wire(JoinMeCodec.encode(terms))));
        assertEquals(JoinMeTerms.cooldown(42_000L), JoinMeCodec.terms(wire(JoinMeCodec.encode(
                JoinMeTerms.cooldown(42_000L)))));
        assertEquals(JoinMeResult.Status.FAILED, JoinMeCodec.terms(null).status());
        JoinMeResult success = JoinMeResult.success(joinMe);
        assertEquals(success, JoinMeCodec.result(wire(JoinMeCodec.encode(success))));
        JoinMeResult cooldown = JoinMeResult.cooldown(9_000L);
        assertEquals(cooldown, JoinMeCodec.result(wire(JoinMeCodec.encode(cooldown))));
        assertTrue(terms.paid());
        assertFalse(JoinMeTerms.refused(JoinMeResult.Status.VANISHED).allowed());
    }

    @Test
    void audiencesSurviveTheWire() {
        assertEquals(Optional.of(audience), JoinMeCodec.audience(wire(JoinMeCodec.encode(audience))));
        assertEquals(Optional.of(JoinMeAudience.NETWORK), JoinMeCodec.audience(wire(JoinMeCodec.encode(
                JoinMeAudience.NETWORK))));
        JsonObject odd = JoinMeCodec.encode(audience);
        odd.getAsJsonArray("circles").add("guild");
        assertEquals(Set.of(JoinMeAudience.Circle.FRIENDS, JoinMeAudience.Circle.CLAN),
                JoinMeCodec.audience(odd).orElseThrow().circles());
    }

    @Test
    void createdAndExpiredEventsAreRelayedToGameServers() {
        JsonObject created = NetworkEvents.encode(new JoinMeCreatedEvent(joinMe)).orElseThrow();
        assertTrue(NetworkCalls.fits(created));
        Optional<PrismarinEvent> decoded = NetworkEvents.decode(wire(created));
        assertEquals(Optional.of(new JoinMeCreatedEvent(joinMe)), decoded);
        JsonObject expired = NetworkEvents.encode(new JoinMeExpiredEvent(joinMe,
                JoinMeExpiredEvent.Reason.USED_UP)).orElseThrow();
        assertEquals(Optional.of(new JoinMeExpiredEvent(joinMe, JoinMeExpiredEvent.Reason.USED_UP)),
                NetworkEvents.decode(wire(expired)));
        expired.addProperty("reason", "unknown");
        assertEquals(JoinMeExpiredEvent.Reason.EXPIRED, ((JoinMeExpiredEvent) NetworkEvents.decode(expired)
                .orElseThrow()).reason());
    }

    @Test
    void theFaceLookTravelsWithTheDraft() {
        JoinMeSpot spot = new JoinMeSpot(creator, "bedwars-2", "bedwars", "arena-1", GameState.WAITING);
        JoinMeLook look = new JoinMeLook("ZXlKMFpYaDBkWEpsY3lJNmUzMTk=", Map.of(4, "<gold>Castle</gold>", 7, ""));
        JoinMeDraft draft = JoinMeDraft.of(spot, JoinMeRequest.of(creator)).withLook(look);
        JoinMeDraft decoded = JoinMeCodec.draft(wire(JoinMeCodec.encode(draft))).orElseThrow();
        assertEquals(look, decoded.look());
        assertTrue(decoded.look().hasTexture());
        assertEquals(JoinMeLook.NONE, JoinMeCodec.look(null));
        Map<Integer, String> rows = new LinkedHashMap<>();
        rows.put(0, "too high");
        rows.put(17, "too low");
        rows.put(3, "x".repeat(400));
        JoinMeLook cleaned = new JoinMeLook("not base64 !", rows);
        assertFalse(cleaned.hasTexture());
        assertEquals(Set.of(3), cleaned.lines().keySet());
        assertEquals(JoinMeLook.MAX_LINE, cleaned.lines().get(3).length());
    }

    @Test
    void idsAreShortLowerCaseTokens() {
        assertTrue(JoinMeCodec.validId("k3f9a2"));
        assertFalse(JoinMeCodec.validId("K3F9A2"));
        assertFalse(JoinMeCodec.validId("abc"));
        assertFalse(JoinMeCodec.validId("../etc"));
        assertFalse(JoinMeCodec.validId(null));
    }
}
