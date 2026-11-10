package net.prismarin.core.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.prismarin.api.event.PrismarinEvent;
import net.prismarin.api.social.Party;
import net.prismarin.api.social.PartyDisbandedEvent;
import net.prismarin.api.social.PartyJoinedEvent;
import net.prismarin.api.social.PartyLeftEvent;
import net.prismarin.api.social.PartyMember;
import net.prismarin.api.social.PartyRole;
import net.prismarin.api.social.PartySettings;
import net.prismarin.shared.event.SimpleEventBus;
import net.prismarin.shared.social.PartyCodec;
import org.junit.jupiter.api.Test;

class PartyMirrorTest {

    private final SimpleEventBus bus = new SimpleEventBus();
    private final List<PrismarinEvent> seen = new ArrayList<>();
    private final PartyMirror mirror = new PartyMirror(bus);
    private final UUID id = UUID.randomUUID();
    private final UUID leader = UUID.randomUUID();
    private final UUID local = UUID.randomUUID();
    private final UUID remote = UUID.randomUUID();
    private final Predicate<UUID> here = uuid -> uuid.equals(local);

    PartyMirrorTest() {
        bus.subscribe(PrismarinEvent.class, seen::add);
    }

    @Test
    void anArrivalSyncFillsTheMirrorWithoutEvents() {
        Party party = party(leader, local, remote);
        mirror.receive(message("sync", null, party, false, null), here);
        assertEquals(Optional.of(party), mirror.partyOf(local));
        assertEquals(Optional.of(party), mirror.partyOf(remote));
        assertEquals(Optional.of(party), mirror.party(id));
        assertEquals(Optional.of(PartyRole.MODERATOR), mirror.partyOf(remote).flatMap(found -> found.role(remote)));
        assertTrue(seen.isEmpty());
    }

    @Test
    void changesUpdateTheMirrorAndFireEvents() {
        Party before = party(leader, local);
        Party after = party(leader, local, remote);
        mirror.receive(message("sync", null, before, false, null), here);
        mirror.receive(message("change", before, after, false, null), here);
        assertEquals(Optional.of(after), mirror.partyOf(remote));
        assertEquals(List.of(new PartyJoinedEvent(after, remote)), seen);
        Party kicked = party(leader, local);
        mirror.receive(message("change", after, kicked, false, PartyLeftEvent.Reason.KICKED), here);
        assertEquals(Optional.empty(), mirror.partyOf(remote));
        assertEquals(new PartyLeftEvent(kicked, remote, PartyLeftEvent.Reason.KICKED), seen.getLast());
    }

    @Test
    void partiesWithoutLocalMembersAreDropped() {
        Party party = party(leader, local, remote);
        mirror.receive(message("sync", null, party, false, null), here);
        Party withoutLocal = party(leader, remote);
        mirror.receive(message("change", party, withoutLocal, false, null), here);
        assertEquals(Optional.empty(), mirror.partyOf(remote));
        assertEquals(0, mirror.size());
    }

    @Test
    void aDisbandedPartyIsGone() {
        Party party = party(leader, local);
        mirror.receive(message("sync", null, party, false, null), here);
        mirror.receive(message("change", party, party, true, null), here);
        assertEquals(Optional.empty(), mirror.partyOf(local));
        assertEquals(List.of(new PartyDisbandedEvent(party)), seen);
    }

    @Test
    void theLastLocalMemberLeavingTheServerForgetsTheParty() {
        UUID second = UUID.randomUUID();
        Party party = party(leader, local, second);
        Set<UUID> online = new HashSet<>(Set.of(local, second));
        mirror.receive(message("sync", null, party, false, null), online::contains);
        mirror.forget(local, online::contains);
        assertEquals(Optional.of(party), mirror.partyOf(local));
        online.remove(local);
        mirror.forget(second, online::contains);
        assertEquals(Optional.empty(), mirror.partyOf(second));
        assertEquals(Optional.empty(), mirror.partyOf(leader));
    }

    @Test
    void brokenMessagesChangeNothing() {
        JsonObject broken = new JsonObject();
        broken.addProperty("type", PartyMirror.TYPE);
        broken.addProperty("after", "nope");
        mirror.receive(broken, here);
        assertEquals(0, mirror.size());
        assertTrue(seen.isEmpty());
    }

    private Party party(UUID owner, UUID... others) {
        List<PartyMember> members = new ArrayList<>();
        members.add(new PartyMember(owner, "Leader", PartyRole.LEADER));
        for (UUID other : others) {
            members.add(new PartyMember(other, "P" + other.toString().substring(0, 4),
                    other.equals(remote) ? PartyRole.MODERATOR : PartyRole.MEMBER));
        }
        return new Party(id, owner, members, Set.of(), PartySettings.DEFAULT);
    }

    private static JsonObject message(String cause, Party before, Party after, boolean dissolved,
                                      PartyLeftEvent.Reason reason) {
        JsonObject message = new JsonObject();
        message.addProperty("type", PartyMirror.TYPE);
        message.addProperty("cause", cause);
        if (before != null) {
            message.add("before", PartyCodec.encode(before));
        }
        if (after != null) {
            message.add("after", PartyCodec.encode(after));
        }
        message.addProperty("dissolved", dissolved);
        if (reason != null) {
            message.addProperty("reason", reason.id());
        }
        return message;
    }
}
