package net.prismarin.velocity.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import net.prismarin.api.social.PartyAccess;
import net.prismarin.api.social.PartyAction;
import net.prismarin.api.social.PartyRole;
import net.prismarin.api.social.PartySettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PartyRolesTest {

    private static final long EXPIRY = 60_000L;

    private final PartyManager parties = new PartyManager(PartySettings.DEFAULT);
    private final UUID leader = UUID.randomUUID();
    private final UUID moderator = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private final UUID outsider = UUID.randomUUID();

    @BeforeEach
    void startParty() {
        for (UUID player : new UUID[]{moderator, member, other}) {
            assertTrue(parties.invite(leader, player, 8, 0L, EXPIRY).ok());
            assertTrue(parties.accept(player, leader, uuid -> 8, 1L).ok());
        }
        assertTrue(parties.promote(leader, moderator).ok());
    }

    @Test
    void rolesFollowLeaderModeratorAndMember() {
        PartyManager.Party party = party();
        assertEquals(PartyRole.LEADER, party.role(leader));
        assertEquals(PartyRole.MODERATOR, party.role(moderator));
        assertEquals(PartyRole.MEMBER, party.role(member));
        assertNull(party.role(outsider));
    }

    @Test
    void moderatorsInviteKickMembersAndChangeSettings() {
        assertTrue(parties.invite(moderator, outsider, 8, 2L, EXPIRY).ok());
        assertTrue(parties.kick(moderator, outsider).ok());
        assertTrue(parties.kick(moderator, member).ok());
        assertFalse(party().member(member));
        assertTrue(parties.configure(moderator, settings -> settings.withAccess(PartyAccess.OPEN)).ok());
        assertEquals(PartyAccess.OPEN, party().settings().access());
    }

    @Test
    void moderatorsCannotTouchTheLeaderOrOtherModerators() {
        assertTrue(parties.promote(leader, other).ok());
        assertEquals(PartyManager.Status.NOT_ALLOWED, parties.kick(moderator, leader).status());
        assertEquals(PartyManager.Status.NOT_ALLOWED, parties.kick(moderator, other).status());
        assertEquals(PartyManager.Status.NOT_ALLOWED, parties.promote(moderator, member).status());
        assertEquals(PartyManager.Status.NOT_ALLOWED, parties.demote(moderator, other).status());
        assertEquals(PartyManager.Status.NOT_ALLOWED, parties.transfer(moderator, member).status());
        assertEquals(PartyManager.Status.NOT_ALLOWED, parties.disband(moderator).status());
        assertTrue(parties.partyOf(leader).isPresent());
    }

    @Test
    void membersOnlyLookChatAndLeave() {
        assertEquals(PartyManager.Status.NOT_ALLOWED, parties.invite(member, outsider, 8, 2L, EXPIRY).status());
        assertEquals(PartyManager.Status.NOT_ALLOWED, parties.kick(member, other).status());
        assertEquals(PartyManager.Status.NOT_ALLOWED,
                parties.configure(member, settings -> settings.withFollow(false)).status());
        assertEquals(PartyManager.Status.NOT_ALLOWED, parties.disband(member).status());
        assertTrue(party().allowed(member, PartyAction.CHAT, null));
        assertTrue(parties.leave(member).ok());
    }

    @Test
    void theLeaderPromotesDemotesAndTransfers() {
        assertEquals(PartyManager.Status.ALREADY_ROLE, parties.promote(leader, moderator).status());
        assertEquals(PartyManager.Status.ALREADY_ROLE, parties.demote(leader, member).status());
        assertEquals(PartyManager.Status.NOT_MEMBER, parties.promote(leader, outsider).status());
        assertEquals(PartyManager.Status.SELF, parties.transfer(leader, leader).status());
        assertTrue(parties.demote(leader, moderator).ok());
        assertEquals(PartyRole.MEMBER, party().role(moderator));
        PartyManager.Result result = parties.transfer(leader, member);
        assertTrue(result.ok());
        assertEquals(PartyRole.LEADER, result.before().role(leader));
        assertEquals(PartyRole.LEADER, party().role(member));
        assertEquals(PartyRole.MODERATOR, party().role(leader));
        assertFalse(party().moderators().contains(member));
    }

    @Test
    void theLeaderMayKickModerators() {
        assertTrue(parties.kick(leader, moderator).ok());
        assertTrue(parties.partyOf(moderator).isEmpty());
        assertFalse(parties.partyOf(leader).orElseThrow().moderators().contains(moderator));
    }

    @Test
    void moderatorsTakeOverFirst() {
        PartyManager.Result left = parties.leave(leader);
        assertEquals(moderator, left.newLeader());
        assertEquals(PartyRole.LEADER, parties.partyOf(member).orElseThrow().role(moderator));
    }

    @Test
    void handOverPrefersOnlineModeratorsAndKeepsTheOldLeaderAsModerator() {
        PartyManager.Result result = parties.handOver(leader, Set.of(member, moderator));
        assertEquals(moderator, result.newLeader());
        assertEquals(PartyRole.MODERATOR, party().role(leader));
        PartyManager.Result offline = parties.handOver(moderator, Set.of(other));
        assertEquals(other, offline.newLeader());
    }

    @Test
    void theInviteSettingDecidesWhoInvites() {
        assertTrue(parties.configure(leader, settings -> settings.withInvite(PartyRole.LEADER)).ok());
        assertEquals(PartyManager.Status.NOT_ALLOWED, parties.invite(moderator, outsider, 8, 2L, EXPIRY).status());
        assertTrue(parties.configure(moderator, settings -> settings.withInvite(PartyRole.MEMBER)).ok());
        assertTrue(parties.invite(member, outsider, 8, 2L, EXPIRY).ok());
    }

    @Test
    void revokingInvitesNeedsAModerator() {
        assertTrue(parties.invite(leader, outsider, 8, 2L, EXPIRY).ok());
        assertEquals(PartyManager.Status.NOT_ALLOWED, parties.kick(member, outsider).status());
        assertTrue(parties.kick(moderator, outsider).ok());
    }

    @Test
    void unchangedSettingsAreReported() {
        PartyManager.Result result = parties.configure(leader, settings -> settings);
        assertEquals(PartyManager.Status.UNCHANGED, result.status());
        assertEquals(PartyManager.Status.NOT_IN_PARTY,
                parties.configure(outsider, settings -> settings.withFollow(false)).status());
    }

    @Test
    void joiningFollowsTheAccessSetting() {
        UUID stranger = UUID.randomUUID();
        assertEquals(PartyManager.Status.CLOSED,
                parties.join(stranger, member, Set.of(), uuid -> 8, 2L).status());
        assertTrue(parties.configure(leader, settings -> settings.withAccess(PartyAccess.FRIENDS)).ok());
        assertEquals(PartyManager.Status.CLOSED,
                parties.join(stranger, member, Set.of(member), uuid -> 8, 2L).status());
        assertTrue(parties.join(stranger, member, Set.of(leader), uuid -> 8, 2L).ok());
        assertTrue(parties.configure(leader, settings -> settings.withAccess(PartyAccess.OPEN)).ok());
        assertTrue(parties.join(outsider, leader, Set.of(), uuid -> 8, 2L).ok());
        assertEquals(PartyManager.Status.IN_PARTY, parties.join(outsider, leader, Set.of(), uuid -> 8, 2L).status());
        assertEquals(PartyManager.Status.FULL,
                parties.join(UUID.randomUUID(), leader, Set.of(), uuid -> 6, 2L).status());
        assertEquals(PartyManager.Status.NO_PARTY,
                parties.join(UUID.randomUUID(), UUID.randomUUID(), Set.of(), uuid -> 8, 2L).status());
    }

    @Test
    void invitedPlayersJoinEvenWhenClosed() {
        assertTrue(parties.invite(leader, outsider, 8, 2L, EXPIRY).ok());
        PartyManager.Result result = parties.join(outsider, member, Set.of(), uuid -> 8, 3L);
        assertTrue(result.ok());
        assertTrue(result.party().member(outsider));
        assertTrue(result.party().invites().isEmpty());
    }

    private PartyManager.Party party() {
        return parties.partyOf(leader).orElseThrow();
    }
}
