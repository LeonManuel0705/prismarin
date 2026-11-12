package net.prismarin.shared.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.admin.AuditService;
import net.prismarin.api.joinme.JoinMe;
import net.prismarin.api.joinme.JoinMeAudience;
import net.prismarin.api.joinme.JoinMeRequest;
import net.prismarin.api.joinme.JoinMeResult;
import net.prismarin.api.joinme.JoinMeSpot;
import net.prismarin.api.server.GameState;
import net.prismarin.shared.joinme.JoinMeDraft;
import net.prismarin.shared.joinme.JoinMeEndpoint;
import net.prismarin.shared.joinme.JoinMeTerms;
import org.junit.jupiter.api.Test;

class JoinMeCallsTest {

    private final UUID creator = UUID.randomUUID();
    private final UUID player = UUID.randomUUID();
    private final JoinMe joinMe = new JoinMe("k3f9a2", creator, "Lea", "Lea", "bedwars-2", "bedwars", "arena-1",
            GameState.WAITING, "", Map.of("map", "Castle"), JoinMeAudience.NETWORK, 0, 0, 1L, 2L);
    private final JoinMeDraft draft = JoinMeDraft.of(new JoinMeSpot(creator, "bedwars-2", "bedwars", "arena-1",
            GameState.WAITING), JoinMeRequest.of(creator)).withHandler("", true);
    private final JoinMeEndpoint proxy = mock(JoinMeEndpoint.class);
    private final List<UUID> carriers = new ArrayList<>();
    private final JoinMeCalls calls = new JoinMeCalls(new ProxyLink() {
        @Override
        public CompletableFuture<JsonObject> call(NetworkCalls.Action action, JsonObject body, UUID avoid) {
            carriers.add(null);
            return send(action, body);
        }

        @Override
        public CompletableFuture<JsonObject> callAs(UUID carrier, NetworkCalls.Action action, JsonObject body) {
            carriers.add(carrier);
            return send(action, body);
        }
    });

    private CompletableFuture<JsonObject> send(NetworkCalls.Action action, JsonObject body) {
        assertTrue(NetworkCalls.fits(body));
        JsonObject wire = JsonParser.parseString(body.toString()).getAsJsonObject();
        return JoinMeCalls.dispatch(proxy, action, wire)
                .thenApply(answer -> JsonParser.parseString(answer.toString()).getAsJsonObject());
    }

    @Test
    void checkAndCreateTravelAsTheCreator() {
        JoinMeTerms terms = new JoinMeTerms(JoinMeResult.Status.SUCCESS, 0L, "coins", 25L, 60_000L, 4,
                JoinMeAudience.groups("lobby"), true);
        when(proxy.check(draft)).thenReturn(CompletableFuture.completedFuture(terms));
        when(proxy.create(draft)).thenReturn(CompletableFuture.completedFuture(JoinMeResult.success(joinMe)));
        assertEquals(terms, calls.check(draft).join());
        assertEquals(JoinMeResult.success(joinMe), calls.create(draft).join());
        assertEquals(List.of(creator, creator), carriers);
    }

    @Test
    void aPreviewTravelsAsTheCreatorAndAnswersWithoutAJoinMe() {
        when(proxy.preview(draft)).thenReturn(CompletableFuture.completedFuture(
                JoinMeResult.of(JoinMeResult.Status.SUCCESS)));

        JoinMeResult result = calls.preview(draft).join();

        assertEquals(JoinMeResult.Status.SUCCESS, result.status());
        assertTrue(result.joinMe().isEmpty());
        assertEquals(List.of(creator), carriers);
        verify(proxy, never()).create(any());
        verify(proxy, never()).check(any());
        assertEquals("joinme-preview", NetworkCalls.Action.JOINME_PREVIEW.id());
        JoinMeCalls offline = new JoinMeCalls((action, body, avoid) ->
                CompletableFuture.failedFuture(new NoRouteException()));
        assertEquals(JoinMeResult.Status.UNAVAILABLE, offline.preview(draft).join().status());
    }

    @Test
    void joinsListsAndCancelsReachTheProxy() {
        when(proxy.join(player, "k3f9a2")).thenReturn(CompletableFuture.completedFuture(
                JoinMeResult.of(JoinMeResult.Status.USED_UP, joinMe)));
        when(proxy.list(player)).thenReturn(CompletableFuture.completedFuture(List.of(joinMe)));
        when(proxy.list(null)).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(proxy.find("k3f9a2")).thenReturn(CompletableFuture.completedFuture(Optional.of(joinMe)));
        when(proxy.cancel(null, creator)).thenReturn(CompletableFuture.completedFuture(true));
        assertEquals(JoinMeResult.of(JoinMeResult.Status.USED_UP, joinMe), calls.join(player, "k3f9a2").join());
        assertEquals(List.of(joinMe), calls.list(player).join());
        assertEquals(List.of(), calls.list(null).join());
        assertEquals(Optional.of(joinMe), calls.find("k3f9a2").join());
        assertTrue(calls.cancel(null, creator).join());
        assertEquals(player, carriers.getFirst());
    }

    @Test
    void invalidIdsNeverLeaveTheServer() {
        assertEquals(JoinMeResult.Status.NOT_FOUND, calls.join(player, "../../x").join().status());
        assertEquals(Optional.empty(), calls.find("WRONG").join());
        verify(proxy, never()).join(any(), any());
        verify(proxy, never()).find(any());
    }

    @Test
    void anUnreachableOrSwitchedOffProxyIsReported() {
        JoinMeCalls offline = new JoinMeCalls((action, body, avoid) ->
                CompletableFuture.failedFuture(new NoRouteException()));
        assertEquals(JoinMeResult.Status.UNAVAILABLE, offline.check(draft).join().status());
        assertEquals(JoinMeResult.Status.UNAVAILABLE, offline.join(player, "k3f9a2").join().status());
        RemoteDispatcher dispatcher = new RemoteDispatcher(mock(AuditService.class), Optional::empty,
                mock(RemoteDispatcher.NoticeSink.class), Optional::empty, Optional::empty);
        JoinMeCalls off = new JoinMeCalls((action, body, avoid) -> dispatcher.dispatch(
                new NetworkCalls.Request(1, action, body), "bedwars-2").thenCompose(answer -> {
                    NetworkCalls.Answer parsed = NetworkCalls.parseAnswer(answer).orElseThrow();
                    return parsed.ok() ? CompletableFuture.completedFuture(parsed.body())
                            : CompletableFuture.failedFuture(new RemoteCallException(parsed.error()));
                }));
        assertEquals(JoinMeResult.Status.DISABLED, off.create(draft).join().status());
        assertEquals(JoinMeResult.Status.DISABLED, off.join(player, "k3f9a2").join().status());
    }

    @Test
    void theDispatcherForwardsToTheRegisteredEndpoint() {
        when(proxy.find(eq("k3f9a2"))).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        RemoteDispatcher dispatcher = new RemoteDispatcher(mock(AuditService.class), Optional::empty,
                mock(RemoteDispatcher.NoticeSink.class), Optional::empty, Optional::empty, Optional::empty,
                Optional::empty, Optional::empty, Optional::empty, () -> Optional.of(proxy));
        JsonObject body = new JsonObject();
        body.addProperty("id", "k3f9a2");
        JsonObject answer = dispatcher.dispatch(new NetworkCalls.Request(5, NetworkCalls.Action.JOINME_FIND, body),
                "lobby-1").join();
        NetworkCalls.Answer parsed = NetworkCalls.parseAnswer(answer).orElseThrow();
        assertTrue(parsed.ok());
        assertFalse(parsed.body().has("joinme"));
    }
}
