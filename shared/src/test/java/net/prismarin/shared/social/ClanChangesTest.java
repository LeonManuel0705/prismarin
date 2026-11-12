package net.prismarin.shared.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;
import net.prismarin.api.social.Clan;
import net.prismarin.api.social.ClanAccess;
import net.prismarin.api.social.ClanAccessChangedEvent;
import net.prismarin.api.social.ClanChatEvent;
import net.prismarin.api.social.ClanColorChangedEvent;
import net.prismarin.api.social.ClanCreatedEvent;
import net.prismarin.api.social.ClanDataChangedEvent;
import net.prismarin.api.social.ClanDisbandedEvent;
import net.prismarin.api.social.ClanInviteEvent;
import net.prismarin.api.social.ClanMember;
import net.prismarin.api.social.ClanMemberJoinedEvent;
import net.prismarin.api.social.ClanMemberLeftEvent;
import net.prismarin.api.social.ClanRenamedEvent;
import net.prismarin.api.social.ClanRole;
import net.prismarin.api.social.ClanRoleChangedEvent;
import net.prismarin.shared.network.NetworkEvents;
import org.junit.jupiter.api.Test;

class ClanChangesTest {

    private final UUID id = UUID.randomUUID();
    private final UUID leader = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID newcomer = UUID.randomUUID();

    private Clan clan(String name, String color, ClanAccess access, Map<String, String> data, Set<UUID> invited,
                      ClanMember... members) {
        return new Clan(id, name, "OWL", color, access, leader, List.of(members), invited, data, 1L);
    }

    private ClanMember leader() {
        return new ClanMember(leader, "Leon", ClanRole.LEADER, 1L);
    }

    @Test
    void creatingAClanOnlyFiresCreated() {
        Clan created = clan("Owls", "", ClanAccess.INVITE_ONLY, Map.of(), Set.of(), leader());
        List<PrismarinEvent> events = ClanChanges.diff(null, created, false, null, leader);
        assertEquals(List.of(new ClanCreatedEvent(created)), events);
    }

    @Test
    void membershipRolesAndInvitesBecomeEvents() {
        Clan before = clan("Owls", "", ClanAccess.INVITE_ONLY, Map.of(), Set.of(), leader(),
                new ClanMember(member, "Alex", ClanRole.MEMBER, 2L));
        Clan after = clan("Owls", "", ClanAccess.INVITE_ONLY, Map.of(), Set.of(UUID.fromString(
                "00000000-0000-0000-0000-000000000009")), leader(),
                new ClanMember(newcomer, "Sam", ClanRole.MEMBER, 3L));
        List<PrismarinEvent> events = ClanChanges.diff(before, after, false, ClanMemberLeftEvent.Reason.KICKED, leader);
        assertTrue(events.contains(new ClanMemberJoinedEvent(after, newcomer)));
        assertTrue(events.contains(new ClanMemberLeftEvent(after, member, ClanMemberLeftEvent.Reason.KICKED)));
        assertTrue(events.contains(new ClanInviteEvent(after, leader,
                UUID.fromString("00000000-0000-0000-0000-000000000009"))));
        Clan promoted = clan("Owls", "", ClanAccess.INVITE_ONLY, Map.of(), Set.of(), leader(),
                new ClanMember(member, "Alex", ClanRole.CO_LEADER, 2L));
        assertEquals(List.of(new ClanRoleChangedEvent(promoted, member, ClanRole.MEMBER, ClanRole.CO_LEADER)),
                ClanChanges.diff(before, promoted, false, null, leader));
    }

    @Test
    void colourAccessNameAndDataChangesAreReported() {
        Clan before = clan("Owls", "", ClanAccess.INVITE_ONLY, Map.of("points", "1", "old", "x"), Set.of(), leader());
        Clan after = clan("Nightowls", "gold", ClanAccess.OPEN, Map.of("points", "2", "new", "y"), Set.of(),
                leader());
        List<PrismarinEvent> events = ClanChanges.diff(before, after, false, null, leader);
        assertTrue(events.contains(new ClanColorChangedEvent(after, "")));
        assertTrue(events.contains(new ClanAccessChangedEvent(after, ClanAccess.INVITE_ONLY)));
        assertTrue(events.contains(new ClanRenamedEvent(after, "Owls", "OWL")));
        assertTrue(events.contains(new ClanDataChangedEvent(after, "points", "1", "2")));
        assertTrue(events.contains(new ClanDataChangedEvent(after, "old", "x", null)));
        assertTrue(events.contains(new ClanDataChangedEvent(after, "new", null, "y")));
        assertEquals(6, events.size());
    }

    @Test
    void disbandingOnlyFiresDisbanded() {
        Clan current = clan("Owls", "", ClanAccess.INVITE_ONLY, Map.of(), Set.of(), leader(),
                new ClanMember(member, "Alex", ClanRole.MEMBER, 2L));
        assertEquals(List.of(new ClanDisbandedEvent(current)), ClanChanges.diff(current, current, true, null, leader));
        assertEquals(List.of(), ClanChanges.diff(null, null, false, null, null));
    }

    @Test
    void everyClanEventReachesTheBackendsUnchanged() {
        Clan current = clan("Owls", "gold", ClanAccess.OPEN, Map.of("points", "4"), Set.of(), leader(),
                new ClanMember(member, "Alex", ClanRole.MODERATOR, 2L));
        List<PrismarinEvent> events = List.of(new ClanCreatedEvent(current), new ClanDisbandedEvent(current),
                new ClanRenamedEvent(current, "Old", "OLD"), new ClanMemberJoinedEvent(current, member),
                new ClanMemberLeftEvent(current, member, ClanMemberLeftEvent.Reason.REMOVED),
                new ClanRoleChangedEvent(current, member, ClanRole.MEMBER, ClanRole.MODERATOR),
                new ClanColorChangedEvent(current, ""), new ClanAccessChangedEvent(current, ClanAccess.INVITE_ONLY),
                new ClanDataChangedEvent(current, "points", null, "4"), new ClanInviteEvent(current, leader, newcomer));
        for (PrismarinEvent event : events) {
            JsonObject message = NetworkEvents.encode(event).orElseThrow();
            assertEquals(Optional.of(event), NetworkEvents.decode(message), event.getClass().getSimpleName());
        }
        ClanChatEvent chat = new ClanChatEvent(current, member, "gg");
        ClanChatEvent decoded = (ClanChatEvent) NetworkEvents.decode(NetworkEvents.encode(chat).orElseThrow())
                .orElseThrow();
        assertEquals(chat.clan(), decoded.clan());
        assertEquals("gg", decoded.message());
        assertEquals(member, decoded.sender());
    }
}
