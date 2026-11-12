package net.prismarin.shared.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.prismarin.api.social.Clan;
import net.prismarin.api.social.ClanAccess;
import net.prismarin.api.social.ClanMember;
import net.prismarin.api.social.ClanPage;
import net.prismarin.api.social.ClanResult;
import net.prismarin.api.social.ClanRole;
import net.prismarin.api.social.ClanService;
import net.prismarin.api.social.SocialResult;
import org.junit.jupiter.api.Test;

class ClanCallsTest {

    private final UUID leader = UUID.randomUUID();
    private final UUID player = UUID.randomUUID();
    private final Clan clan = new Clan(UUID.randomUUID(), "Nightowls", "OWL", "gold", ClanAccess.OPEN, leader,
            List.of(new ClanMember(leader, "Leon", ClanRole.LEADER, 1L)), Set.of(), Map.of("wars:points", "9"), 1L);
    private final ClanService proxy = mock(ClanService.class);
    private final ClanCalls calls = new ClanCalls((action, body, avoid) -> {
        assertTrue(NetworkCalls.fits(body));
        return ClanCalls.dispatch(proxy, action, body);
    });

    @Test
    void lookupsReturnTheClanOfTheProxy() {
        when(proxy.lookup(player)).thenReturn(CompletableFuture.completedFuture(Optional.of(clan)));
        when(proxy.byId(clan.id())).thenReturn(CompletableFuture.completedFuture(Optional.of(clan)));
        when(proxy.find("owl")).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        assertEquals(Optional.of(clan), calls.lookup(player).join());
        assertEquals(Optional.of(clan), calls.byId(clan.id()).join());
        assertEquals(Optional.empty(), calls.find("owl").join());
    }

    @Test
    void pagesKeepTheirPositionAndAreCappedInSize() {
        when(proxy.list(anyInt(), anyInt())).thenReturn(CompletableFuture.completedFuture(
                new ClanPage(List.of(clan), 2, 5, 41)));
        ClanPage page = calls.list(2, 500).join();
        assertEquals(List.of(clan), page.clans());
        assertEquals(2, page.page());
        assertEquals(5, page.pages());
        assertEquals(41, page.total());
        verify(proxy).list(2, ClanCalls.MAX_PAGE_SIZE);
    }

    @Test
    void managementCallsCarryEveryArgument() {
        when(proxy.addMember(clan.id(), player, ClanRole.MODERATOR)).thenReturn(CompletableFuture.completedFuture(
                ClanResult.of(SocialResult.SUCCESS, clan)));
        when(proxy.setData(clan.id(), "wars:points", null)).thenReturn(CompletableFuture.completedFuture(
                ClanResult.of(SocialResult.SUCCESS, clan)));
        when(proxy.renameClan(clan.id(), "Owls", null)).thenReturn(CompletableFuture.completedFuture(
                ClanResult.of(SocialResult.NAME_TAKEN, clan)));
        when(proxy.broadcast(clan.id(), "&6War starts")).thenReturn(CompletableFuture.completedFuture(
                SocialResult.SUCCESS));
        when(proxy.createClan(leader, "Owls", "OWL")).thenReturn(CompletableFuture.completedFuture(
                ClanResult.of(SocialResult.ALREADY_IN_CLAN)));
        ClanResult added = calls.manage("add", clan.id(), player, null, null, "moderator").join();
        assertEquals(SocialResult.SUCCESS, added.result());
        assertEquals(Optional.of(clan), added.clan());
        assertTrue(calls.manage("data", clan.id(), null, "wars:points", null, null).join().success());
        assertEquals(SocialResult.NAME_TAKEN, calls.manage("rename", clan.id(), null, "Owls", null, null).join()
                .result());
        assertEquals(SocialResult.SUCCESS, calls.manage("broadcast", clan.id(), null, null, null, "&6War starts")
                .join().result());
        assertEquals(SocialResult.ALREADY_IN_CLAN, calls.manage("create", null, leader, "Owls", "OWL", null).join()
                .result());
    }

    @Test
    void playerActionsRunAsTheActor() {
        when(proxy.changeAccess(player, ClanAccess.OPEN)).thenReturn(CompletableFuture.completedFuture(
                SocialResult.SUCCESS));
        when(proxy.chat(player, "hi")).thenReturn(CompletableFuture.completedFuture(SocialResult.NOT_IN_CLAN));
        when(proxy.join(player, clan.id())).thenReturn(CompletableFuture.completedFuture(SocialResult.CLOSED));
        assertEquals(SocialResult.SUCCESS, calls.act("access", player, null, null, null, "open").join());
        assertEquals(SocialResult.NOT_IN_CLAN, calls.act("chat", player, null, null, null, "hi").join());
        assertEquals(SocialResult.CLOSED, calls.act("join", player, clan.id(), null, null, null).join());
    }

    @Test
    void unknownActionsAndMissingRoutesFailCleanly() {
        JsonObject body = new JsonObject();
        body.addProperty("op", "explode");
        body.addProperty("clan", clan.id().toString());
        assertThrows(IllegalArgumentException.class,
                () -> ClanCalls.dispatch(proxy, NetworkCalls.Action.CLAN_MANAGE, body));
        ClanCalls offline = new ClanCalls((action, request, avoid) -> CompletableFuture.failedFuture(
                new CompletionException(new NoRouteException())));
        assertEquals(SocialResult.UNAVAILABLE, offline.act("leave", player, null, null, null, null).join());
        assertEquals(SocialResult.UNAVAILABLE, offline.manage("disband", clan.id(), null, null, null, null).join()
                .result());
        when(proxy.disbandClan(any())).thenReturn(CompletableFuture.completedFuture(ClanResult.of(
                SocialResult.CANCELLED, clan)));
        assertEquals(SocialResult.CANCELLED, calls.manage("disband", clan.id(), null, null, null, null).join()
                .result());
    }
}
