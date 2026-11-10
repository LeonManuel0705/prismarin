package net.prismarin.shared.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.prismarin.api.event.PrismarinEvent;
import net.prismarin.api.social.Party;
import net.prismarin.api.social.PartyAccess;
import net.prismarin.api.social.PartyCreatedEvent;
import net.prismarin.api.social.PartyDisbandedEvent;
import net.prismarin.api.social.PartyInviteEvent;
import net.prismarin.api.social.PartyJoinedEvent;
import net.prismarin.api.social.PartyLeftEvent;
import net.prismarin.api.social.PartyMember;
import net.prismarin.api.social.PartyRole;
import net.prismarin.api.social.PartyRoleChangedEvent;
import net.prismarin.api.social.PartySettings;
import net.prismarin.api.social.PartySettingsChangedEvent;
import org.junit.jupiter.api.Test;

class PartyChangesTest {

    private final UUID id = UUID.randomUUID();
    private final UUID leader = UUID.randomUUID();
    private final UUID alex = UUID.randomUUID();
    private final UUID sam = UUID.randomUUID();

    @Test
    void theFirstInviteCreatesAPartyAndSendsAnInvite() {
        Party created = party(leader, List.of(member(leader, PartyRole.LEADER)), Set.of(alex), PartySettings.DEFAULT);
        List<PrismarinEvent> events = PartyChanges.diff(null, created, false, null, leader);
        assertEquals(List.of(new PartyCreatedEvent(created), new PartyInviteEvent(created, leader, alex)), events);
    }

    @Test
    void joiningAndLeavingAreReportedWithTheReason() {
        Party before = party(leader, List.of(member(leader, PartyRole.LEADER), member(alex, PartyRole.MEMBER)),
                Set.of(sam), PartySettings.DEFAULT);
        Party joined = party(leader, List.of(member(leader, PartyRole.LEADER), member(alex, PartyRole.MEMBER),
                member(sam, PartyRole.MEMBER)), Set.of(), PartySettings.DEFAULT);
        assertEquals(List.of(new PartyJoinedEvent(joined, sam)), PartyChanges.diff(before, joined, false, null, sam));
        Party kicked = party(leader, List.of(member(leader, PartyRole.LEADER), member(sam, PartyRole.MEMBER)),
                Set.of(), PartySettings.DEFAULT);
        assertEquals(List.of(new PartyLeftEvent(kicked, alex, PartyLeftEvent.Reason.KICKED)),
                PartyChanges.diff(joined, kicked, false, PartyLeftEvent.Reason.KICKED, leader));
        assertEquals(List.of(new PartyLeftEvent(kicked, alex, PartyLeftEvent.Reason.LEFT)),
                PartyChanges.diff(joined, kicked, false, null, alex));
    }

    @Test
    void roleChangesCoverPromotionsAndTransfers() {
        Party before = party(leader, List.of(member(leader, PartyRole.LEADER), member(alex, PartyRole.MEMBER)),
                Set.of(), PartySettings.DEFAULT);
        Party after = party(alex, List.of(member(leader, PartyRole.MODERATOR), member(alex, PartyRole.LEADER)),
                Set.of(), PartySettings.DEFAULT);
        assertEquals(List.of(
                        new PartyRoleChangedEvent(after, leader, PartyRole.LEADER, PartyRole.MODERATOR),
                        new PartyRoleChangedEvent(after, alex, PartyRole.MEMBER, PartyRole.LEADER)),
                PartyChanges.diff(before, after, false, null, leader));
    }

    @Test
    void settingChangesKeepThePreviousSettings() {
        PartySettings open = PartySettings.DEFAULT.withAccess(PartyAccess.OPEN);
        Party before = party(leader, List.of(member(leader, PartyRole.LEADER), member(alex, PartyRole.MEMBER)),
                Set.of(), PartySettings.DEFAULT);
        Party after = party(leader, before.members(), Set.of(), open);
        assertEquals(List.of(new PartySettingsChangedEvent(after, PartySettings.DEFAULT)),
                PartyChanges.diff(before, after, false, null, leader));
    }

    @Test
    void theLastLeaveEndsTheParty() {
        Party before = party(leader, List.of(member(leader, PartyRole.LEADER), member(alex, PartyRole.MEMBER)),
                Set.of(), PartySettings.DEFAULT);
        Party last = party(leader, List.of(member(leader, PartyRole.LEADER)), Set.of(), PartySettings.DEFAULT);
        assertEquals(List.of(new PartyLeftEvent(last, alex, PartyLeftEvent.Reason.DISCONNECTED),
                        new PartyDisbandedEvent(last)),
                PartyChanges.diff(before, last, true, PartyLeftEvent.Reason.DISCONNECTED, alex));
    }

    @Test
    void disbandingOnlyReportsTheEnd() {
        Party party = party(leader, List.of(member(leader, PartyRole.LEADER), member(alex, PartyRole.MEMBER)),
                Set.of(), PartySettings.DEFAULT);
        assertEquals(List.of(new PartyDisbandedEvent(party)), PartyChanges.diff(party, party, true, null, leader));
        assertTrue(PartyChanges.diff(null, null, false, null, null).isEmpty());
        assertTrue(PartyChanges.diff(party, party, false, null, null).isEmpty());
    }

    private Party party(UUID owner, List<PartyMember> members, Set<UUID> invited, PartySettings settings) {
        return new Party(id, owner, members, invited, settings);
    }

    private static PartyMember member(UUID uuid, PartyRole role) {
        return new PartyMember(uuid, "p" + uuid.toString().substring(0, 4), role);
    }
}
