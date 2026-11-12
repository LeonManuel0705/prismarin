package net.prismarin.lobby.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.queue.QueueApi;
import net.prismarin.api.queue.QueueInfo;
import net.prismarin.api.queue.QueueJoinedEvent;
import net.prismarin.api.queue.QueueLeftEvent;
import net.prismarin.api.queue.QueueSentEvent;
import net.prismarin.api.queue.QueueService;
import net.prismarin.api.queue.QueueTarget;
import net.prismarin.shared.event.SimpleEventBus;
import net.prismarin.shared.network.RemoteCallException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class QueueBoardTest {

    private static final QueueTarget BEDWARS = QueueTarget.group("bedwars");

    private final SimpleEventBus events = new SimpleEventBus();
    private final QueueService queue = mock(QueueService.class);
    private final QueueBoard board = new QueueBoard(events);

    @AfterEach
    void tearDown() {
        board.stop();
        QueueApi.unregister();
    }

    @Test
    void sizesComeFromTheProxyAndFollowTheRelayedEvents() {
        when(queue.queues()).thenReturn(CompletableFuture.completedFuture(List.of(
                new QueueInfo(BEDWARS, "BedWars", 4, 6, false, List.of()),
                new QueueInfo(QueueTarget.server("bw-1"), "bw-1", 0, 0, true, List.of()))));
        QueueApi.register(queue);
        board.start();
        assertEquals(4, board.waiting(BEDWARS));
        assertEquals(0, board.waiting(QueueTarget.server("bw-1")));
        assertTrue(board.available());
        UUID player = UUID.randomUUID();
        events.publish(new QueueJoinedEvent(BEDWARS, player, List.of(), 5, 5));
        assertEquals(5, board.waiting(BEDWARS));
        events.publish(new QueueSentEvent(BEDWARS, "bw-2", player, List.of(), 1_000L, 2));
        assertEquals(2, board.waiting(BEDWARS));
        events.publish(new QueueLeftEvent(BEDWARS, player, List.of(), QueueLeftEvent.Reason.LEFT, 0));
        assertEquals(0, board.waiting(BEDWARS));
        verify(queue, times(1)).queues();
    }

    @Test
    void aProxyWithoutQueueIsRememberedAsUnavailable() {
        when(queue.queues()).thenReturn(CompletableFuture.failedFuture(
                new RemoteCallException("the queue is off on the proxy")));
        QueueApi.register(queue);
        assertFalse(board.available());
        assertEquals(0, board.waiting(BEDWARS));
    }

    @Test
    void withoutTheApiNothingIsShown() {
        assertFalse(board.available());
        assertEquals(0, board.waiting(BEDWARS));
    }
}
