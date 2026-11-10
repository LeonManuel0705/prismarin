package net.prismarin.shared.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.prismarin.api.social.Party;
import net.prismarin.api.social.PartyAccess;
import net.prismarin.api.social.PartyMember;
import net.prismarin.api.social.PartyRole;
import net.prismarin.api.social.PartySettings;
import org.junit.jupiter.api.Test;

class PartyCodecTest {

    private final UUID id = UUID.randomUUID();
    private final UUID leader = UUID.randomUUID();
    private final UUID moderator = UUID.randomUUID();
    private final UUID invited = UUID.randomUUID();

    @Test
    void aPartySurvivesTheWayToTheBackend() {
        Party party = new Party(id, leader, List.of(new PartyMember(leader, "Leon", PartyRole.LEADER),
                new PartyMember(moderator, "Alex", PartyRole.MODERATOR)), Set.of(invited),
                new PartySettings(PartyRole.MEMBER, PartyAccess.FRIENDS, false, PartyRole.MODERATOR));
        String json = PartyCodec.encode(party).toString();
        assertEquals(Optional.of(party), PartyCodec.decode(JsonParser.parseString(json)));
    }

    @Test
    void missingPartsFallBackToSafeValues() {
        Party party = PartyCodec.decode(JsonParser.parseString("{\"id\":\"" + id + "\",\"leader\":\"" + leader
                + "\",\"members\":[{\"uuid\":\"" + leader + "\"},{\"uuid\":\"nope\"},{\"uuid\":\"" + moderator
                + "\",\"role\":\"boss\"}],\"invited\":[\"x\",\"" + invited + "\"]}")).orElseThrow();
        assertEquals(PartySettings.DEFAULT, party.settings());
        assertEquals(List.of(leader, moderator), party.memberIds());
        assertEquals(Optional.of(PartyRole.LEADER), party.role(leader));
        assertEquals(Optional.of(PartyRole.MEMBER), party.role(moderator));
        assertEquals(Set.of(invited), party.invited());
    }

    @Test
    void brokenInputGivesNoParty() {
        assertTrue(PartyCodec.decode(null).isEmpty());
        assertTrue(PartyCodec.decode(JsonParser.parseString("[]")).isEmpty());
        assertTrue(PartyCodec.decode(JsonParser.parseString("{\"id\":\"x\",\"leader\":\"y\"}")).isEmpty());
        assertEquals(PartySettings.DEFAULT.withFollow(false), PartyCodec.decodeSettings(
                JsonParser.parseString("{\"follow\":false,\"invite\":7}"), PartySettings.DEFAULT));
    }
}
