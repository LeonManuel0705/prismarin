package net.prismarin.shared.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.admin.AuditService;
import net.prismarin.api.queue.QueueEntry;
import net.prismarin.api.queue.QueueInfo;
import net.prismarin.api.queue.QueuePosition;
import net.prismarin.api.queue.QueueResult;
import net.prismarin.api.queue.QueueService;
import net.prismarin.api.queue.QueueStatus;
import net.prismarin.api.queue.QueueTarget;
import org.junit.jupiter.api.Test;

class QueueCallsTest {

    private static final QueueTarget BEDWARS = QueueTarget.group("bedwars");

    private final UUID leader = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final QueueService proxy = mock(QueueService.class);
    private final QueueCalls calls = new QueueCalls((action, body, avoid) -> {
        assertTrue(NetworkCalls.fits(NetworkCalls.request(1, action, body)));
        return QueueCalls.dispatch(proxy, action, body)
                .thenApply(answer -> NetworkCalls.parseAnswer(NetworkCalls.answer(1, answer)).orElseThrow().body());
    });

    private QueuePosition position() {
        return new QueuePosition(BEDWARS, leader, List.of(member), 3, 12, 15, 10, false, 1_000L, true);
    }

    @Test
    void joinAnswersWithThePlaceInTheQueue() {
        when(proxy.join(leader, BEDWARS)).thenReturn(CompletableFuture.completedFuture(
                QueueResult.waiting(QueueStatus.QUEUED, position())));
        QueueResult result = calls.join(leader, BEDWARS).join();
        assertEquals(QueueStatus.QUEUED, result.status());
        assertEquals(position(), result.position().orElseThrow());
        assertTrue(result.waiting());
        when(proxy.join(member, QueueTarget.server("bw-1"))).thenReturn(CompletableFuture.completedFuture(
                QueueResult.sent("bw-1")));
        QueueResult sent = calls.join(member, QueueTarget.server("bw-1")).join();
        assertTrue(sent.sent());
        assertEquals(Optional.of("bw-1"), sent.server());
        assertEquals(Optional.empty(), sent.position());
    }

    @Test
    void refusedJoinMeansTheQueueIsOffAndOtherErrorsFail() {
        QueueCalls off = new QueueCalls((action, body, avoid) ->
                CompletableFuture.failedFuture(new RemoteCallException("the queue is off on the proxy")));
        assertEquals(QueueStatus.DISABLED, off.join(leader, BEDWARS).join().status());
        QueueCalls lost = new QueueCalls((action, body, avoid) ->
                CompletableFuture.failedFuture(new NoRouteException()));
        assertEquals(QueueStatus.FAILED, lost.join(leader, BEDWARS).join().status());
    }

    @Test
    void leaveAndPositionsReachTheProxy() {
        when(proxy.leave(leader)).thenReturn(CompletableFuture.completedFuture(true));
        when(proxy.leave(member, BEDWARS)).thenReturn(CompletableFuture.completedFuture(false));
        when(proxy.positions(leader)).thenReturn(CompletableFuture.completedFuture(List.of(position())));
        assertTrue(calls.leave(leader).join());
        assertFalse(calls.leave(member, BEDWARS).join());
        assertEquals(List.of(position()), calls.positions(leader).join());
        assertEquals(Optional.of(position()), calls.position(leader).join());
    }

    @Test
    void listsCarryEntriesOnlyForOneQueue() {
        QueueEntry entry = new QueueEntry(leader, "Leon", List.of(member), 1, 10, 1_000L, false);
        QueueInfo info = new QueueInfo(BEDWARS, "&bBedWars", 1, 2, true, List.of(entry));
        when(proxy.queues()).thenReturn(CompletableFuture.completedFuture(List.of(info)));
        when(proxy.queue(BEDWARS)).thenReturn(CompletableFuture.completedFuture(Optional.of(info)));
        when(proxy.queue(QueueTarget.group("skywars"))).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        QueueInfo listed = calls.queues().join().getFirst();
        assertEquals(BEDWARS, listed.target());
        assertEquals("&bBedWars", listed.displayName());
        assertTrue(listed.paused());
        assertTrue(listed.entries().isEmpty());
        assertEquals(info, calls.queue(BEDWARS).join().orElseThrow());
        assertEquals(1, calls.size(BEDWARS).join());
        assertEquals(Optional.empty(), calls.queue(QueueTarget.group("skywars")).join());
    }

    @Test
    void staffActionsReachTheProxy() {
        when(proxy.pause(BEDWARS, "Leon")).thenReturn(CompletableFuture.completedFuture(true));
        when(proxy.resume(BEDWARS, "Leon")).thenReturn(CompletableFuture.completedFuture(false));
        when(proxy.clear(BEDWARS)).thenReturn(CompletableFuture.completedFuture(4));
        when(proxy.sendNext(BEDWARS)).thenReturn(CompletableFuture.completedFuture(Optional.of(leader)));
        when(proxy.sendNext(QueueTarget.server("bw-1"))).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(proxy.joinable("bw-1", false)).thenReturn(CompletableFuture.completedFuture(true));
        when(proxy.closedServers()).thenReturn(CompletableFuture.completedFuture(Set.of("bw-1", "bw-2")));
        assertTrue(calls.pause(BEDWARS, "Leon").join());
        assertFalse(calls.resume(BEDWARS, "Leon").join());
        assertEquals(4, calls.clear(BEDWARS).join());
        assertEquals(Optional.of(leader), calls.sendNext(BEDWARS).join());
        assertEquals(Optional.empty(), calls.sendNext(QueueTarget.server("bw-1")).join());
        assertTrue(calls.joinable("bw-1", false).join());
        assertEquals(Set.of("bw-1", "bw-2"), calls.closedServers().join());
        verify(proxy).joinable("bw-1", false);
    }

    @Test
    void invalidBodiesAreRefused() {
        JsonObject body = new JsonObject();
        body.addProperty("player", leader.toString());
        body.addProperty("target", "planet:mars");
        assertThrows(IllegalArgumentException.class,
                () -> QueueCalls.dispatch(proxy, NetworkCalls.Action.QUEUE_JOIN, body));
        JsonObject manage = new JsonObject();
        manage.addProperty("op", "explode");
        assertThrows(IllegalArgumentException.class,
                () -> QueueCalls.dispatch(proxy, NetworkCalls.Action.QUEUE_MANAGE, manage));
        assertThrows(IllegalArgumentException.class,
                () -> QueueCalls.dispatch(proxy, NetworkCalls.Action.CLAN_GET, new JsonObject()));
    }

    @Test
    void dispatcherRefusesQueueCallsWhileTheQueueIsOff() {
        RemoteDispatcher dispatcher = new RemoteDispatcher(mock(AuditService.class),
                Optional::empty, mock(RemoteDispatcher.NoticeSink.class), Optional::empty, Optional::empty,
                Optional::empty, Optional::empty, Optional::empty, Optional::empty, Optional::empty);
        JsonObject body = new JsonObject();
        body.addProperty("player", leader.toString());
        NetworkCalls.Answer answer = NetworkCalls.parseAnswer(dispatcher.dispatch(
                new NetworkCalls.Request(5, NetworkCalls.Action.QUEUE_POSITIONS, body), "lobby-1").join()).orElseThrow();
        assertFalse(answer.ok());
        assertEquals("the queue is off on the proxy", answer.error());
        assertEquals("queue-join", NetworkCalls.Action.QUEUE_JOIN.id());
    }
}
