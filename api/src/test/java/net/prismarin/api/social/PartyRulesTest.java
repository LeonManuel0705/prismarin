package net.prismarin.api.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PartyRulesTest {

    private static final PartySettings DEFAULT = PartySettings.DEFAULT;

    @Test
    void theLeaderMayDoEverythingToOthers() {
        for (PartyAction action : List.of(PartyAction.INVITE, PartyAction.REVOKE, PartyAction.DISBAND,
                PartyAction.WARP, PartyAction.SETTINGS, PartyAction.CHAT)) {
            assertTrue(PartyRules.allowed(action, PartyRole.LEADER, null, DEFAULT), action.name());
        }
        assertTrue(PartyRules.allowed(PartyAction.KICK, PartyRole.LEADER, PartyRole.MODERATOR, DEFAULT));
        assertTrue(PartyRules.allowed(PartyAction.KICK, PartyRole.LEADER, PartyRole.MEMBER, DEFAULT));
        assertTrue(PartyRules.allowed(PartyAction.PROMOTE, PartyRole.LEADER, PartyRole.MEMBER, DEFAULT));
        assertTrue(PartyRules.allowed(PartyAction.DEMOTE, PartyRole.LEADER, PartyRole.MODERATOR, DEFAULT));
        assertTrue(PartyRules.allowed(PartyAction.TRANSFER, PartyRole.LEADER, PartyRole.MEMBER, DEFAULT));
        assertTrue(PartyRules.allowed(PartyAction.TRANSFER, PartyRole.LEADER, PartyRole.MODERATOR, DEFAULT));
        assertFalse(PartyRules.allowed(PartyAction.PROMOTE, PartyRole.LEADER, PartyRole.MODERATOR, DEFAULT));
        assertFalse(PartyRules.allowed(PartyAction.DEMOTE, PartyRole.LEADER, PartyRole.MEMBER, DEFAULT));
        assertFalse(PartyRules.allowed(PartyAction.KICK, PartyRole.LEADER, PartyRole.LEADER, DEFAULT));
        assertFalse(PartyRules.allowed(PartyAction.TRANSFER, PartyRole.LEADER, null, DEFAULT));
    }

    @Test
    void moderatorsInviteKickMembersAndChangeSettings() {
        Set<PartyAction> allowed = EnumSet.noneOf(PartyAction.class);
        for (PartyAction action : PartyAction.values()) {
            if (PartyRules.allowed(action, PartyRole.MODERATOR, PartyRole.MEMBER, DEFAULT)) {
                allowed.add(action);
            }
        }
        assertEquals(EnumSet.of(PartyAction.INVITE, PartyAction.REVOKE, PartyAction.KICK, PartyAction.SETTINGS,
                PartyAction.CHAT), allowed);
        assertFalse(PartyRules.allowed(PartyAction.KICK, PartyRole.MODERATOR, PartyRole.MODERATOR, DEFAULT));
        assertFalse(PartyRules.allowed(PartyAction.KICK, PartyRole.MODERATOR, PartyRole.LEADER, DEFAULT));
        assertFalse(PartyRules.allowed(PartyAction.KICK, PartyRole.MODERATOR, null, DEFAULT));
    }

    @Test
    void membersOnlyChatByDefault() {
        Set<PartyAction> allowed = EnumSet.noneOf(PartyAction.class);
        for (PartyAction action : PartyAction.values()) {
            for (PartyRole target : PartyRole.values()) {
                if (PartyRules.allowed(action, PartyRole.MEMBER, target, DEFAULT)) {
                    allowed.add(action);
                }
            }
        }
        assertEquals(EnumSet.of(PartyAction.CHAT), allowed);
    }

    @Test
    void settingsMoveTheInviteAndChatLine() {
        PartySettings everyone = DEFAULT.withInvite(PartyRole.MEMBER);
        assertTrue(PartyRules.allowed(PartyAction.INVITE, PartyRole.MEMBER, null, everyone));
        PartySettings leaderOnly = DEFAULT.withInvite(PartyRole.LEADER).withChat(PartyRole.MODERATOR);
        assertFalse(PartyRules.allowed(PartyAction.INVITE, PartyRole.MODERATOR, null, leaderOnly));
        assertTrue(PartyRules.allowed(PartyAction.INVITE, PartyRole.LEADER, null, leaderOnly));
        assertFalse(PartyRules.allowed(PartyAction.CHAT, PartyRole.MEMBER, null, leaderOnly));
        assertTrue(PartyRules.allowed(PartyAction.CHAT, PartyRole.MODERATOR, null, leaderOnly));
        assertTrue(PartyRules.allowed(PartyAction.INVITE, PartyRole.MODERATOR, null, null));
    }

    @Test
    void outsidersMayDoNothing() {
        for (PartyAction action : PartyAction.values()) {
            assertFalse(PartyRules.allowed(action, null, PartyRole.MEMBER, DEFAULT), action.name());
            assertFalse(PartyRules.allowed(null, PartyRole.LEADER, PartyRole.MEMBER, DEFAULT));
        }
    }

    @Test
    void aPartySnapshotAnswersRoleQuestions() {
        UUID leader = UUID.randomUUID();
        UUID moderator = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID invited = UUID.randomUUID();
        Party party = new Party(UUID.randomUUID(), leader, List.of(new PartyMember(leader, "Leon", PartyRole.LEADER),
                new PartyMember(moderator, "Alex", PartyRole.MODERATOR),
                new PartyMember(member, "Sam", PartyRole.MEMBER)), Set.of(invited), null);
        assertEquals(DEFAULT, party.settings());
        assertEquals(3, party.size());
        assertTrue(party.isLeader(leader));
        assertTrue(party.isInvited(invited));
        assertFalse(party.contains(invited));
        assertEquals(Optional.of(PartyRole.MODERATOR), party.role(moderator));
        assertEquals(List.of(moderator), party.withRole(PartyRole.MODERATOR).stream().map(PartyMember::uuid).toList());
        assertTrue(party.can(moderator, PartyAction.KICK, member));
        assertFalse(party.can(moderator, PartyAction.KICK, leader));
        assertFalse(party.can(member, PartyAction.INVITE));
        assertFalse(party.can(invited, PartyAction.CHAT));
        assertEquals(List.of(leader, moderator, member), party.memberIds());
    }

    @Test
    void rolesAndResultsHaveStableIds() {
        assertEquals(Optional.of(PartyRole.MODERATOR), PartyRole.byId(" Moderator "));
        assertEquals(Optional.empty(), PartyRole.byId("boss"));
        assertTrue(PartyRole.LEADER.outranks(PartyRole.MODERATOR));
        assertFalse(PartyRole.MODERATOR.outranks(PartyRole.MODERATOR));
        assertEquals(Optional.of(PartyAccess.INVITE_ONLY), PartyAccess.byId("invite_only"));
        assertEquals("not-allowed", SocialResult.NOT_ALLOWED.id());
        for (SocialResult result : SocialResult.values()) {
            assertEquals(Optional.of(result), SocialResult.byId(result.id()));
        }
        assertTrue(SocialResult.SUCCESS.success());
        assertFalse(SocialResult.UNAVAILABLE.success());
    }
}
