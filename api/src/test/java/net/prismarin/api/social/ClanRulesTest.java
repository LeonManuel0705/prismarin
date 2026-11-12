package net.prismarin.api.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClanRulesTest {

    private static final ClanRole MEMBER = ClanRole.MEMBER;
    private static final ClanRole MOD = ClanRole.MODERATOR;
    private static final ClanRole CO = ClanRole.CO_LEADER;
    private static final ClanRole LEADER = ClanRole.LEADER;

    @Test
    void defaultRightsFollowTheRoleLadder() {
        ClanRights rights = ClanRights.DEFAULT;
        assertEquals(MOD, rights.role(ClanAction.INVITE));
        assertEquals(MOD, rights.role(ClanAction.KICK));
        assertEquals(CO, rights.role(ClanAction.PROMOTE));
        assertEquals(CO, rights.role(ClanAction.COLOR));
        assertEquals(CO, rights.role(ClanAction.SETTINGS));
        assertEquals(LEADER, rights.role(ClanAction.RENAME));
        assertEquals(MEMBER, rights.role(ClanAction.CHAT));
        assertTrue(allowed(ClanAction.INVITE, MOD, null));
        assertFalse(allowed(ClanAction.INVITE, MEMBER, null));
        assertTrue(allowed(ClanAction.CHAT, MEMBER, null));
        assertTrue(allowed(ClanAction.COLOR, CO, null));
        assertFalse(allowed(ClanAction.COLOR, MOD, null));
    }

    @Test
    void kickingNeedsTheRightAndAHigherRole() {
        assertTrue(allowed(ClanAction.KICK, MOD, MEMBER));
        assertFalse(allowed(ClanAction.KICK, MOD, MOD));
        assertFalse(allowed(ClanAction.KICK, MOD, CO));
        assertTrue(allowed(ClanAction.KICK, CO, MOD));
        assertTrue(allowed(ClanAction.KICK, LEADER, CO));
        assertFalse(allowed(ClanAction.KICK, MEMBER, MEMBER));
        assertFalse(allowed(ClanAction.KICK, CO, LEADER));
        assertFalse(allowed(ClanAction.KICK, LEADER, null));
    }

    @Test
    void promotingStopsBelowTheOwnRoleAndBelowLeader() {
        assertTrue(allowed(ClanAction.PROMOTE, LEADER, MEMBER));
        assertTrue(allowed(ClanAction.PROMOTE, LEADER, MOD));
        assertFalse(allowed(ClanAction.PROMOTE, LEADER, CO));
        assertTrue(allowed(ClanAction.PROMOTE, CO, MEMBER));
        assertFalse(allowed(ClanAction.PROMOTE, CO, MOD));
        assertFalse(allowed(ClanAction.PROMOTE, MOD, MEMBER));
        assertTrue(allowed(ClanAction.DEMOTE, LEADER, CO));
        assertTrue(allowed(ClanAction.DEMOTE, CO, MOD));
        assertFalse(allowed(ClanAction.DEMOTE, CO, CO));
        assertFalse(allowed(ClanAction.DEMOTE, LEADER, MEMBER));
    }

    @Test
    void onlyTheLeaderTransfersAndDisbands() {
        ClanRights generous = new ClanRights(Map.of(ClanAction.TRANSFER, MEMBER, ClanAction.DISBAND, MEMBER));
        assertEquals(LEADER, generous.role(ClanAction.TRANSFER));
        assertEquals(LEADER, generous.role(ClanAction.DISBAND));
        assertTrue(ClanRules.allowed(ClanAction.TRANSFER, LEADER, CO, generous));
        assertFalse(ClanRules.allowed(ClanAction.TRANSFER, CO, MEMBER, generous));
        assertFalse(ClanRules.allowed(ClanAction.TRANSFER, LEADER, LEADER, generous));
        assertTrue(ClanRules.allowed(ClanAction.DISBAND, LEADER, null, generous));
        assertFalse(ClanRules.allowed(ClanAction.DISBAND, CO, null, generous));
    }

    @Test
    void configuredRightsChangeTheMatrixButColorStaysWithCoLeaders() {
        ClanRights rights = ClanRights.DEFAULT.with(ClanAction.INVITE, MEMBER).with(ClanAction.KICK, LEADER)
                .with(ClanAction.CHAT, MOD).with(ClanAction.COLOR, MEMBER).with(ClanAction.RENAME, MEMBER);
        assertTrue(ClanRules.allowed(ClanAction.INVITE, MEMBER, null, rights));
        assertFalse(ClanRules.allowed(ClanAction.KICK, CO, MEMBER, rights));
        assertTrue(ClanRules.allowed(ClanAction.KICK, LEADER, MEMBER, rights));
        assertFalse(ClanRules.allowed(ClanAction.CHAT, MEMBER, null, rights));
        assertEquals(CO, rights.role(ClanAction.COLOR));
        assertEquals(CO, rights.role(ClanAction.RENAME));
        assertFalse(ClanRules.allowed(ClanAction.COLOR, MOD, null, rights));
        assertFalse(ClanRules.allowed(ClanAction.CHAT, null, null, rights));
        assertFalse(ClanRules.allowed(null, LEADER, null, rights));
    }

    @Test
    void rolesParseFriendlyNamesAndKnowTheirNeighbours() {
        assertEquals(Optional.of(CO), ClanRole.byId("Co_Leader"));
        assertEquals(Optional.of(CO), ClanRole.byId("coleader"));
        assertEquals(Optional.of(MOD), ClanRole.byId("mod"));
        assertEquals(Optional.of(LEADER), ClanRole.byId("owner"));
        assertEquals(Optional.empty(), ClanRole.byId("boss"));
        assertEquals(Optional.of(MOD), MEMBER.above());
        assertEquals(Optional.empty(), LEADER.above());
        assertEquals(Optional.empty(), MEMBER.below());
        assertEquals(Optional.of(ClanAccess.OPEN), ClanAccess.byId("Public"));
        assertEquals(Optional.of(ClanAccess.INVITE_ONLY), ClanAccess.byId("invite"));
        assertEquals(ClanAccess.INVITE_ONLY, ClanAccess.OPEN.next());
    }

    @Test
    void clanSnapshotAnswersRoleQuestionsAndReadsData() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Clan clan = new Clan(UUID.randomUUID(), "Nightowls", "OWL", "gold", ClanAccess.OPEN, leader,
                List.of(new ClanMember(leader, "Leon", LEADER, 1L), new ClanMember(member, "Alex", MEMBER, 2L)),
                Set.of(), Map.of("points", "42", "note", "x"), 1L);
        assertTrue(clan.can(leader, ClanAction.KICK, member, ClanRights.DEFAULT));
        assertFalse(clan.can(member, ClanAction.KICK, leader, ClanRights.DEFAULT));
        assertEquals(42L, clan.number("points", 0L));
        assertEquals(7L, clan.number("note", 7L));
        assertEquals(7L, clan.number("missing", 7L));
        assertEquals(List.of(member), clan.withRole(MEMBER).stream().map(ClanMember::uuid).toList());
        assertTrue(clan.hasColor());
    }

    @Test
    void pagesAndDataKeysStayInBounds() {
        Clan sample = new Clan(UUID.randomUUID(), "A", "A", "", null, UUID.randomUUID(), List.of(), Set.of(), null, 0L);
        ClanPage page = ClanPage.of(List.of(sample, sample, sample), 5, 2);
        assertEquals(1, page.page());
        assertEquals(2, page.pages());
        assertEquals(1, page.clans().size());
        assertFalse(page.hasNext());
        assertTrue(ClanData.validKey("wars:points"));
        assertFalse(ClanData.validKey("Points"));
        assertFalse(ClanData.validKey(""));
        assertTrue(ClanData.validValue(null));
        assertFalse(ClanData.validValue("x".repeat(ClanData.MAX_VALUE_LENGTH + 1)));
    }

    private static boolean allowed(ClanAction action, ClanRole actor, ClanRole target) {
        return ClanRules.allowed(action, actor, target, ClanRights.DEFAULT);
    }
}
