package net.prismarin.shared.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.prismarin.api.social.Clan;
import net.prismarin.api.social.ClanAccess;
import net.prismarin.api.social.ClanAction;
import net.prismarin.api.social.ClanMember;
import net.prismarin.api.social.ClanRights;
import net.prismarin.api.social.ClanRole;
import org.junit.jupiter.api.Test;

class ClanCodecTest {

    private final UUID id = UUID.randomUUID();
    private final UUID leader = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID invited = UUID.randomUUID();

    @Test
    void aClanSurvivesStorageAndTheWayToTheBackend() {
        Clan clan = new Clan(id, "Nightowls", "OWL", "#ff8800", ClanAccess.OPEN, leader,
                List.of(new ClanMember(leader, "Leon", ClanRole.LEADER, 10L),
                        new ClanMember(member, "Alex", ClanRole.CO_LEADER, 20L)),
                Set.of(invited), Map.of("wars:points", "12", "wars:wins", "3"), 5L);
        String json = ClanCodec.encode(clan).toString();
        assertEquals(Optional.of(clan), ClanCodec.decode(JsonParser.parseString(json)));
        JsonObject stored = ClanCodec.encode(clan, false);
        assertFalse(stored.has("invited"));
        assertEquals("nightowls", stored.get(ClanCodec.NAME_KEY).getAsString());
        assertEquals("owl", stored.get(ClanCodec.TAG_KEY).getAsString());
        assertEquals(Set.of(), ClanCodec.decode(stored).orElseThrow().invited());
    }

    @Test
    void brokenPartsFallBackToSafeValues() {
        String json = "{\"id\":\"" + id + "\",\"name\":\"Owls\",\"tag\":\"OWL\",\"leader\":\"" + leader
                + "\",\"color\":\"&l\",\"access\":\"weird\",\"members\":[{\"uuid\":\"" + leader
                + "\",\"role\":\"member\"},{\"uuid\":\"nope\"},{\"uuid\":\"" + member + "\",\"role\":\"leader\"},"
                + "{\"uuid\":\"" + member + "\"}],\"data\":{\"Bad Key\":\"1\",\"ok\":\"2\",\"nested\":{}}}";
        Clan clan = ClanCodec.decode(JsonParser.parseString(json)).orElseThrow();
        assertEquals("", clan.color());
        assertEquals(ClanAccess.INVITE_ONLY, clan.access());
        assertEquals(List.of(leader, member), clan.memberIds());
        assertEquals(Optional.of(ClanRole.LEADER), clan.role(leader));
        assertEquals(Optional.of(ClanRole.MEMBER), clan.role(member));
        assertEquals(Map.of("ok", "2"), clan.data());
    }

    @Test
    void clansWithoutLeaderOrNameAreRejected() {
        assertTrue(ClanCodec.decode(JsonParser.parseString("{\"id\":\"" + id + "\",\"name\":\"A\",\"tag\":\"A\","
                + "\"leader\":\"" + leader + "\",\"members\":[{\"uuid\":\"" + member + "\"}]}")).isEmpty());
        assertTrue(ClanCodec.decode(JsonParser.parseString("{\"id\":\"" + id + "\",\"tag\":\"A\",\"leader\":\""
                + leader + "\",\"members\":[{\"uuid\":\"" + leader + "\"}]}")).isEmpty());
        assertTrue(ClanCodec.decode(JsonParser.parseString("[]")).isEmpty());
        assertTrue(ClanCodec.decode(null).isEmpty());
    }

    @Test
    void rightsAndDisplayTravelWithTheSync() {
        ClanRights rights = ClanRights.DEFAULT.with(ClanAction.INVITE, ClanRole.MEMBER);
        assertEquals(rights, ClanCodec.decodeRights(ClanCodec.encodeRights(rights)));
        assertEquals(ClanRights.DEFAULT, ClanCodec.decodeRights(null));
        ClanTagFormat display = new ClanTagFormat("<gray>(%name%)", ClanTagFormat.Position.BEFORE, false, true);
        assertEquals(display, ClanCodec.decodeDisplay(ClanCodec.encodeDisplay(display)));
        assertEquals(ClanTagFormat.DEFAULT, ClanCodec.decodeDisplay(JsonParser.parseString("\"x\"")));
    }
}
