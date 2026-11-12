package net.prismarin.velocity.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToIntFunction;
import net.prismarin.api.social.Clan;
import net.prismarin.api.social.ClanAccess;
import net.prismarin.api.social.ClanAction;
import net.prismarin.api.social.ClanRights;
import net.prismarin.api.social.ClanRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClanManagerTest {

    private static final long EXPIRY = 60_000L;
    private static final ToIntFunction<UUID> ROOMY = leader -> 20;

    private final ClanManager clans = new ClanManager(ClanRights.DEFAULT);
    private final UUID leader = UUID.randomUUID();
    private final UUID coLeader = UUID.randomUUID();
    private final UUID moderator = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID outsider = UUID.randomUUID();
    private UUID clanId;

    @BeforeEach
    void foundClan() {
        ClanManager.Result created = clans.create(leader, "Leon", "Nightowls", "OWL", ClanAccess.INVITE_ONLY, 1L);
        assertTrue(created.ok());
        assertTrue(created.created());
        clanId = created.clan().id();
        for (UUID player : List.of(coLeader, moderator, member)) {
            assertTrue(clans.invite(leader, player, ROOMY, 2L, EXPIRY).ok());
            assertTrue(clans.accept(player, "p", null, ROOMY, 3L).ok());
        }
        assertTrue(clans.promote(leader, coLeader).ok());
        assertTrue(clans.promote(leader, coLeader).ok());
        assertTrue(clans.promote(leader, moderator).ok());
    }

    private Clan clan() {
        return clans.clan(clanId).orElseThrow();
    }

    @Test
    void rolesClimbTheLadder() {
        assertEquals(Optional.of(ClanRole.LEADER), clan().role(leader));
        assertEquals(Optional.of(ClanRole.CO_LEADER), clan().role(coLeader));
        assertEquals(Optional.of(ClanRole.MODERATOR), clan().role(moderator));
        assertEquals(Optional.of(ClanRole.MEMBER), clan().role(member));
        assertEquals(ClanManager.Status.ALREADY_ROLE, clans.promote(leader, coLeader).status());
        assertEquals(ClanManager.Status.ALREADY_ROLE, clans.demote(leader, member).status());
    }

    @Test
    void namesAndTagsAreUniqueAcrossBothFields() {
        assertEquals(ClanManager.Status.IN_CLAN, clans.create(leader, "Leon", "Other", "OTH", ClanAccess.OPEN, 4L)
                .status());
        assertEquals(ClanManager.Status.NAME_TAKEN, clans.create(outsider, "Sam", "nightowls", "NEW",
                ClanAccess.OPEN, 4L).status());
        assertEquals(ClanManager.Status.NAME_TAKEN, clans.create(outsider, "Sam", "owl", "NEW",
                ClanAccess.OPEN, 4L).status());
        assertEquals(ClanManager.Status.TAG_TAKEN, clans.create(outsider, "Sam", "Larks", "owl",
                ClanAccess.OPEN, 4L).status());
        assertEquals(Optional.of(clanId), clans.find("OWL").map(Clan::id));
        assertEquals(Optional.of(clanId), clans.find(" nightowls ").map(Clan::id));
    }

    @Test
    void invitesExpireAndCanBeDeniedOrRevoked() {
        assertTrue(clans.invite(moderator, outsider, ROOMY, 10L, EXPIRY).ok());
        assertEquals(ClanManager.Status.ALREADY_INVITED, clans.invite(leader, outsider, ROOMY, 11L, EXPIRY).status());
        assertEquals(1, clans.invitesFor(outsider, 12L).size());
        assertEquals(0, clans.invitesFor(outsider, 10L + EXPIRY).size());
        assertEquals(ClanManager.Status.NO_INVITE, clans.accept(outsider, "o", null, ROOMY, 10L + EXPIRY).status());
        assertTrue(clans.invite(leader, outsider, ROOMY, 20L, EXPIRY).ok());
        assertTrue(clans.deny(outsider, clanId, 21L).ok());
        assertTrue(clans.invite(leader, outsider, ROOMY, 22L, EXPIRY).ok());
        assertTrue(clans.kick(moderator, outsider).ok());
        assertFalse(clan().isInvited(outsider));
        assertEquals(ClanManager.Status.NOT_ALLOWED, clans.invite(member, outsider, ROOMY, 23L, EXPIRY).status());
        assertEquals(ClanManager.Status.ALREADY_MEMBER, clans.invite(leader, member, ROOMY, 23L, EXPIRY).status());
    }

    @Test
    void openClansTakeEveryoneClosedOnlyInvitedAndFullOnesNobody() {
        assertEquals(ClanManager.Status.CLOSED, clans.join(outsider, "o", clanId, ROOMY, 5L).status());
        assertTrue(clans.access(coLeader, ClanAccess.OPEN).ok());
        assertEquals(ClanManager.Status.NOT_ALLOWED, clans.access(moderator, ClanAccess.INVITE_ONLY).status());
        assertEquals(ClanManager.Status.FULL, clans.join(outsider, "o", clanId, leader -> 4, 5L).status());
        assertTrue(clans.join(outsider, "o", clanId, ROOMY, 5L).ok());
        assertEquals(ClanManager.Status.IN_CLAN, clans.join(outsider, "o", clanId, ROOMY, 6L).status());
        assertEquals(5, clan().size());
    }

    @Test
    void kickingFollowsRanks() {
        assertEquals(ClanManager.Status.NOT_ALLOWED, clans.kick(member, moderator).status());
        assertEquals(ClanManager.Status.NOT_ALLOWED, clans.kick(moderator, coLeader).status());
        assertEquals(ClanManager.Status.NOT_ALLOWED, clans.kick(coLeader, leader).status());
        assertEquals(ClanManager.Status.SELF, clans.kick(moderator, moderator).status());
        assertTrue(clans.kick(moderator, member).ok());
        assertTrue(clans.kick(coLeader, moderator).ok());
        assertEquals(Optional.empty(), clans.clanOf(member));
        assertEquals(2, clan().size());
    }

    @Test
    void promotingAndDemotingStayBelowTheOwnRole() {
        assertEquals(ClanManager.Status.NOT_ALLOWED, clans.promote(moderator, member).status());
        assertTrue(clans.promote(coLeader, member).ok());
        assertEquals(Optional.of(ClanRole.MODERATOR), clan().role(member));
        assertEquals(ClanManager.Status.NOT_ALLOWED, clans.promote(coLeader, member).status());
        assertTrue(clans.demote(coLeader, member).ok());
        assertEquals(ClanManager.Status.SELF, clans.demote(coLeader, coLeader).status());
        assertTrue(clans.demote(leader, coLeader).ok());
        assertEquals(Optional.of(ClanRole.MODERATOR), clan().role(coLeader));
    }

    @Test
    void theLeaderHandsOverBeforeLeavingAndTheLastOneEndsTheClan() {
        assertEquals(ClanManager.Status.TRANSFER_FIRST, clans.leave(leader).status());
        assertEquals(ClanManager.Status.NOT_ALLOWED, clans.transfer(coLeader, member).status());
        assertTrue(clans.transfer(leader, member).ok());
        assertEquals(member, clan().leader());
        assertEquals(Optional.of(ClanRole.CO_LEADER), clan().role(leader));
        assertTrue(clans.leave(leader).ok());
        assertTrue(clans.leave(coLeader).ok());
        assertTrue(clans.leave(moderator).ok());
        ClanManager.Result last = clans.leave(member);
        assertTrue(last.disbanded());
        assertEquals(Optional.empty(), clans.clan(clanId));
        assertEquals(Optional.empty(), clans.find("OWL"));
        assertTrue(clans.create(outsider, "Sam", "Nightowls", "OWL", ClanAccess.OPEN, 9L).ok());
    }

    @Test
    void onlyTheLeaderDisbandsAndColourNeedsTheRight() {
        assertEquals(ClanManager.Status.NOT_ALLOWED, clans.disband(coLeader).status());
        assertEquals(ClanManager.Status.NOT_ALLOWED, clans.color(moderator, "red").status());
        assertTrue(clans.color(coLeader, "red").ok());
        assertEquals(ClanManager.Status.UNCHANGED, clans.color(leader, "red").status());
        assertEquals("red", clan().color());
        ClanManager.Result disbanded = clans.disband(leader);
        assertTrue(disbanded.disbanded());
        assertEquals(4, disbanded.clan().size());
        assertEquals(Optional.empty(), clans.clanOf(member));
    }

    @Test
    void configuredRightsAreUsed() {
        ClanManager strict = new ClanManager(ClanRights.DEFAULT.with(ClanAction.INVITE, ClanRole.LEADER)
                .with(ClanAction.KICK, ClanRole.LEADER));
        UUID owner = UUID.randomUUID();
        UUID helper = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        strict.create(owner, "Owner", "Larks", "LRK", ClanAccess.OPEN, 1L);
        UUID id = strict.clanOf(owner).orElseThrow().id();
        assertTrue(strict.add(id, helper, "Helper", ClanRole.CO_LEADER, ROOMY, 2L).ok());
        assertEquals(ClanManager.Status.NOT_ALLOWED, strict.invite(helper, guest, ROOMY, 3L, EXPIRY).status());
        assertTrue(strict.join(guest, "Guest", id, ROOMY, 3L).ok());
        assertEquals(ClanManager.Status.NOT_ALLOWED, strict.kick(helper, guest).status());
        assertTrue(strict.kick(owner, guest).ok());
    }

    @Test
    void playersInAnotherClanCannotBeAdded() {
        assertTrue(clans.create(outsider, "Sam", "Larks", "LRK", ClanAccess.OPEN, 4L).ok());
        assertEquals(ClanManager.Status.TARGET_IN_CLAN,
                clans.add(clanId, outsider, "Sam", ClanRole.MEMBER, ROOMY, 5L).status());
        assertEquals(ClanManager.Status.TARGET_IN_CLAN, clans.invite(leader, outsider, ROOMY, 5L, EXPIRY).status());
        assertEquals(2, clans.count());
    }

    @Test
    void renamesMembersRolesAndDataWithoutAnActor() {
        assertTrue(clans.add(clanId, outsider, "Sam", ClanRole.MODERATOR, ROOMY, 5L).ok());
        assertEquals(Optional.of(ClanRole.MODERATOR), clan().role(outsider));
        assertEquals(ClanManager.Status.NOT_ALLOWED, clans.add(clanId, UUID.randomUUID(), "X", ClanRole.LEADER,
                ROOMY, 5L).status());
        assertTrue(clans.setRole(clanId, outsider, ClanRole.LEADER).ok());
        assertEquals(outsider, clan().leader());
        assertEquals(Optional.of(ClanRole.CO_LEADER), clan().role(leader));
        assertEquals(ClanManager.Status.TRANSFER_FIRST, clans.setRole(clanId, outsider, ClanRole.MEMBER).status());
        assertEquals(ClanManager.Status.TRANSFER_FIRST, clans.removeMember(clanId, outsider).status());
        assertTrue(clans.removeMember(clanId, member).ok());
        assertTrue(clans.rename(clanId, "Owls", null).ok());
        assertEquals("OWL", clan().tag());
        assertEquals(Optional.of(clanId), clans.find("owls").map(Clan::id));
        assertEquals(Optional.empty(), clans.find("nightowls"));
        assertTrue(clans.setData(clanId, "wars:points", "10").ok());
        assertEquals(ClanManager.Status.UNCHANGED, clans.setData(clanId, "wars:points", "10").status());
        assertEquals(ClanManager.Status.INVALID, clans.setData(clanId, "Bad Key", "1").status());
        assertEquals(Map.of("wars:points", "10"), clan().data());
        assertTrue(clans.setData(clanId, "wars:points", null).ok());
        assertEquals(Map.of(), clan().data());
        assertTrue(clans.setColor(clanId, "#ff8800").ok());
        assertTrue(clans.setAccess(clanId, ClanAccess.OPEN).ok());
        assertTrue(clans.dissolve(clanId).disbanded());
        assertEquals(ClanManager.Status.NO_CLAN, clans.rename(clanId, "X", "X").status());
    }

    @Test
    void reloadedClansReplaceTheOldStateAndKeepInvites() {
        assertTrue(clans.invite(leader, outsider, ROOMY, 30L, EXPIRY).ok());
        Clan stored = clan();
        Clan changed = new Clan(stored.id(), stored.name(), stored.tag(), "aqua", stored.access(), stored.leader(),
                stored.members().subList(0, 2), stored.invited(), stored.data(), stored.created());
        clans.load(changed);
        assertEquals("aqua", clan().color());
        assertEquals(2, clan().size());
        assertTrue(clan().isInvited(outsider));
        assertEquals(Optional.empty(), clans.clanOf(member));
        assertTrue(clans.forget(clanId));
        assertEquals(Optional.empty(), clans.clanOf(leader));
        assertEquals(0, clans.count());
    }

    @Test
    void storedNamesFollowTheOnlineName() {
        assertTrue(clans.refreshName(member, "Alexander").isPresent());
        assertEquals("Alexander", clan().member(member).orElseThrow().name());
        assertTrue(clans.refreshName(member, "Alexander").isEmpty());
        assertTrue(clans.refreshName(outsider, "Nobody").isEmpty());
    }
}
