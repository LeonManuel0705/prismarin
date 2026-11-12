package net.prismarin.shared.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.prismarin.api.queue.QueuePosition;
import net.prismarin.api.queue.QueueResult;
import net.prismarin.api.queue.QueueService;
import net.prismarin.api.queue.QueueStatus;
import net.prismarin.api.queue.QueueTarget;
import net.prismarin.api.server.NetworkConnector;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import org.junit.jupiter.api.Test;

class QueueingConnectorTest {

    private static final NetworkServer BW_1 = new NetworkServer("bw-1", "&bBedWars 1", "localhost", 25_566, "bedwars",
            Map.of());

    private final UUID player = UUID.randomUUID();
    private final NetworkConnector direct = mock(NetworkConnector.class);
    private final QueueService queue = mock(QueueService.class);
    private final ServerDirectory directory = new ServerDirectory() {
        @Override
        public List<NetworkServer> servers() {
            return List.of(BW_1);
        }

        @Override
        public Optional<NetworkServer> byName(String name) {
            return BW_1.name().equalsIgnoreCase(name) ? Optional.of(BW_1) : Optional.empty();
        }
    };

    @Test
    void withoutAQueueGroupsConnectDirectly() {
        QueueingConnector connector = new QueueingConnector(direct, directory, Optional::empty);
        when(direct.connectToGroup(player, "bedwars")).thenReturn(CompletableFuture.completedFuture(Optional.of(BW_1)));
        assertEquals(QueueResult.sent("bw-1"), connector.travel(player, QueueTarget.group("bedwars")).join());
        assertEquals(Optional.of(BW_1), connector.connectToGroup(player, "bedwars").join());
        assertEquals(QueueStatus.UNKNOWN_TARGET, connector.travel(player, QueueTarget.group("mars")).join().status());
        when(direct.connectToGroup(player, "bedwars")).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        assertEquals(QueueStatus.NO_SERVER, connector.travel(player, QueueTarget.group("bedwars")).join().status());
    }

    @Test
    void withoutAQueueServersConnectDirectly() {
        QueueingConnector connector = new QueueingConnector(direct, directory, Optional::empty);
        when(direct.connect(player, "bw-1")).thenReturn(CompletableFuture.completedFuture(true));
        assertEquals(QueueResult.sent("bw-1"), connector.travel(player, QueueTarget.server("BW-1")).join());
        assertEquals(QueueStatus.UNKNOWN_TARGET, connector.travel(player, QueueTarget.server("bw-9")).join().status());
        when(direct.connect(player, "bw-1")).thenReturn(CompletableFuture.completedFuture(false));
        assertEquals(QueueStatus.FAILED, connector.travel(player, QueueTarget.server("bw-1")).join().status());
    }

    @Test
    void queuedPlayersGetNoServerYet() {
        QueueingConnector connector = new QueueingConnector(direct, directory, () -> Optional.of(queue));
        QueuePosition position = new QueuePosition(QueueTarget.group("bedwars"), player, List.of(), 2, 5, 5, 0, false,
                1L, true);
        when(queue.join(player, QueueTarget.group("bedwars"))).thenReturn(CompletableFuture.completedFuture(
                QueueResult.waiting(QueueStatus.QUEUED, position)));
        assertEquals(Optional.empty(), connector.connectToGroup(player, "bedwars").join());
        assertEquals(QueueStatus.QUEUED, connector.travel(player, QueueTarget.group("bedwars")).join().status());
        verify(direct, never()).connectToGroup(any(), anyString());
        when(queue.join(player, QueueTarget.group("bedwars"))).thenReturn(CompletableFuture.completedFuture(
                QueueResult.sent("bw-1")));
        assertEquals(Optional.of(BW_1), connector.connectToGroup(player, "bedwars").join());
    }

    @Test
    void switchedOffOrUnreachableQueueFallsBackToDirectConnects() {
        QueueingConnector connector = new QueueingConnector(direct, directory, () -> Optional.of(queue));
        when(direct.connect(player, "bw-1")).thenReturn(CompletableFuture.completedFuture(true));
        when(queue.join(player, QueueTarget.server("bw-1"))).thenReturn(CompletableFuture.completedFuture(
                QueueResult.of(QueueStatus.DISABLED)));
        assertEquals(QueueResult.sent("bw-1"), connector.travel(player, QueueTarget.server("bw-1")).join());
        when(queue.join(player, QueueTarget.server("bw-1"))).thenReturn(CompletableFuture.failedFuture(
                new IllegalStateException("timeout")));
        assertEquals(QueueResult.sent("bw-1"), connector.travel(player, QueueTarget.server("bw-1")).join());
        when(direct.connect(player, "bw-1")).thenReturn(CompletableFuture.completedFuture(true));
        assertEquals(true, connector.connect(player, "bw-1").join());
    }
}
