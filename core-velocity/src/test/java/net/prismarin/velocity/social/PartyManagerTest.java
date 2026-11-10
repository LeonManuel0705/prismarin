package net.prismarin.velocity.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.prismarin.api.social.PartyRole;
import net.prismarin.api.social.PartySettings;
import org.junit.jupiter.api.Test;

class PartyManagerTest {

    private static final long EXPIRY = 60_000L;

    private final PartyManager parties = new PartyManager(PartySettings.DEFAULT);
    private final UUID leader = UUID.randomUUID();
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final UUID third = UUID.randomUUID();

    @Test
    void theFirstInviteStartsAParty() {
        PartyManager.Result result = parties.invite(leader, first, 8, 0L, EXPIRY);
        assertTrue(result.ok());
        assertTrue(result.created());
        assertNull(result.before());
        assertEquals(leader, result.party().leader());
        assertEquals(List.of(leader), result.party().members());
        assertEquals(EXPIRY, result.party().invites().get(first));
        assertEquals(PartySettings.DEFAULT, result.party().settings());
        assertFalse(parties.invite(leader, second, 8, 0L, EXPIRY).created());
    }

    @Test
    void refusesInvalidInvites() {
        assertEquals(PartyManager.Status.SELF, parties.invite(leader, leader, 8, 0L, EXPIRY).status());
        parties.invite(leader, first, 8, 0L, EXPIRY);
        assertEquals(PartyManager.Status.ALREADY_INVITED, parties.invite(leader, first, 8, 1L, EXPIRY).status());
        accept(first);
        assertEquals(PartyManager.Status.ALREADY_MEMBER, parties.invite(leader, first, 8, 1L, EXPIRY).status());
        assertEquals(PartyManager.Status.NOT_ALLOWED, parties.invite(first, second, 8, 1L, EXPIRY).status());
        parties.invite(second, third, 8, 1L, EXPIRY);
        assertEquals(PartyManager.Status.TARGET_IN_PARTY, parties.invite(leader, second, 8, 1L, EXPIRY).status());
    }

    @Test
    void membersMayInviteWhenAllowed() {
        PartyManager open = new PartyManager(PartySettings.DEFAULT.withInvite(PartyRole.MEMBER));
        open.invite(leader, first, 8, 0L, EXPIRY);
        open.accept(first, null, uuid -> 8, 1L);
        assertTrue(open.invite(first, second, 8, 2L, EXPIRY).ok());
    }

    @Test
    void acceptingJoinsOnlyWithAnOpenInviteAndRoom() {
        assertEquals(PartyManager.Status.NO_INVITE, parties.accept(first, null, uuid -> 8, 0L).status());
        parties.invite(leader, first, 2, 0L, EXPIRY);
        parties.invite(leader, second, 2, 0L, EXPIRY);
        assertTrue(parties.accept(first, leader, uuid -> 2, 1L).ok());
        assertEquals(PartyManager.Status.FULL, parties.accept(second, null, uuid -> 2, 1L).status());
        assertEquals(PartyManager.Status.FULL, parties.invite(leader, third, 2, 1L, EXPIRY).status());
        assertEquals(PartyManager.Status.IN_PARTY, parties.accept(first, null, uuid -> 8, 1L).status());
    }

    @Test
    void expiredInvitesCannotBeAccepted() {
        parties.invite(leader, first, 8, 0L, EXPIRY);
        assertEquals(PartyManager.Status.NO_INVITE, parties.accept(first, null, uuid -> 8, EXPIRY).status());
    }

    @Test
    void acceptWithoutNameTakesTheNewestInvite() {
        UUID other = UUID.randomUUID();
        parties.invite(leader, first, 8, 0L, EXPIRY);
        parties.invite(other, first, 8, 5L, EXPIRY);
        PartyManager.Result result = parties.accept(first, null, uuid -> 8, 6L);
        assertEquals(other, result.party().leader());
        assertEquals(1, parties.invitesFor(first, 6L).size());
    }

    @Test
    void decliningTheOnlyInviteEndsAnEmptyParty() {
        parties.invite(leader, first, 8, 0L, EXPIRY);
        PartyManager.Result result = parties.deny(first, leader, 1L);
        assertTrue(result.ok());
        assertTrue(result.disbanded());
        assertTrue(parties.partyOf(leader).isEmpty());
    }

    @Test
    void theLeadMovesOnWhenTheLeaderLeaves() {
        parties.invite(leader, first, 8, 0L, EXPIRY);
        parties.invite(leader, second, 8, 0L, EXPIRY);
        accept(first);
        accept(second);
        PartyManager.Result result = parties.leave(leader);
        assertEquals(first, result.newLeader());
        assertFalse(result.disbanded());
        assertEquals(first, parties.partyOf(second).orElseThrow().leader());
        assertEquals(PartyManager.Status.NOT_IN_PARTY, parties.leave(leader).status());
    }

    @Test
    void thePartyEndsWhenOnlyOnePlayerIsLeft() {
        parties.invite(leader, first, 8, 0L, EXPIRY);
        accept(first);
        PartyManager.Result result = parties.leave(first);
        assertTrue(result.disbanded());
        assertEquals(List.of(leader), result.formerMembers());
        assertTrue(parties.partyOf(leader).isEmpty());
    }

    @Test
    void membersCannotKickPromoteOrTransfer() {
        parties.invite(leader, first, 8, 0L, EXPIRY);
        parties.invite(leader, second, 8, 0L, EXPIRY);
        accept(first);
        accept(second);
        assertEquals(PartyManager.Status.NOT_ALLOWED, parties.kick(first, second).status());
        assertEquals(PartyManager.Status.SELF, parties.kick(leader, leader).status());
        assertEquals(PartyManager.Status.NOT_MEMBER, parties.kick(leader, third).status());
        assertTrue(parties.kick(leader, second).ok());
        assertTrue(parties.partyOf(second).isEmpty());
        assertEquals(PartyManager.Status.NOT_ALLOWED, parties.promote(first, leader).status());
        assertEquals(PartyManager.Status.NOT_ALLOWED, parties.transfer(first, leader).status());
        PartyManager.Result transferred = parties.transfer(leader, first);
        assertEquals(first, transferred.newLeader());
        assertEquals(first, parties.partyOf(leader).orElseThrow().leader());
    }

    @Test
    void kickingAnInviteeTakesTheInviteBack() {
        parties.invite(leader, first, 8, 0L, EXPIRY);
        parties.invite(leader, second, 8, 0L, EXPIRY);
        assertTrue(parties.kick(leader, first).ok());
        assertTrue(parties.invitesFor(first, 1L).isEmpty());
        assertEquals(1, parties.invitesFor(second, 1L).size());
    }

    @Test
    void disbandingReturnsEveryFormerMember() {
        parties.invite(leader, first, 8, 0L, EXPIRY);
        accept(first);
        assertEquals(PartyManager.Status.NOT_ALLOWED, parties.disband(first).status());
        PartyManager.Result result = parties.disband(leader);
        assertTrue(result.disbanded());
        assertNull(result.after());
        assertEquals(Set.of(leader, first), Set.copyOf(result.formerMembers()));
        assertEquals(0, parties.count());
    }

    @Test
    void expiringTheLastInviteEndsAnEmptyParty() {
        PartyManager.Result invited = parties.invite(leader, first, 8, 0L, EXPIRY);
        UUID id = invited.party().id();
        assertFalse(parties.expire(id, first, EXPIRY + 1).ok());
        PartyManager.Result expired = parties.expire(id, first, EXPIRY);
        assertTrue(expired.ok());
        assertTrue(expired.disbanded());
        assertTrue(parties.party(id).isEmpty());
    }

    @Test
    void handOverPicksAnOnlineMember() {
        parties.invite(leader, first, 8, 0L, EXPIRY);
        parties.invite(leader, second, 8, 0L, EXPIRY);
        accept(first);
        accept(second);
        assertEquals(second, parties.handOver(leader, Set.of(second)).newLeader());
        assertFalse(parties.handOver(leader, Set.of(first)).ok());
        assertNull(parties.leave(third).party());
    }

    private void accept(UUID player) {
        assertTrue(parties.accept(player, leader, uuid -> 8, 1L).ok());
    }
}
