package net.prismarin.shared.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.prismarin.api.server.GameState;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.api.server.ServerStatus;
import net.prismarin.shared.messaging.LocalMessageBus;
import org.junit.jupiter.api.Test;

class NetworkStatusServiceTest {

    private static final NetworkServer LOBBY = new NetworkServer("lobby", "Lobby", "127.0.0.1", 1, "lobby", Map.of());
    private static final NetworkServer BEDWARS = new NetworkServer("bw", "BW", "127.0.0.1", 2, "bedwars", Map.of());

    private final ServerDirectory directory = new ServerDirectory() {
        @Override
        public List<NetworkServer> servers() {
            return List.of(LOBBY, BEDWARS);
        }

        @Override
        public Optional<NetworkServer> byName(String name) {
            return servers().stream().filter(server -> server.name().equalsIgnoreCase(name)).findFirst();
        }
    };

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC);

    private NetworkStatusService service(NetworkStatusService.Pinger pinger) {
        return new NetworkStatusService(directory, new LocalMessageBus("lobby"), Runnable::run, clock, pinger);
    }

    @Test
    void unknownServersStartOffline() {
        assertTrue(service((host, port, timeout) -> null).statuses().stream().noneMatch(ServerStatus::online));
    }

    @Test
    void pingWithoutHeartbeatHasNoMetrics() {
        NetworkStatusService service = service((host, port, timeout) ->
                new MinecraftPinger.Result(port * 10, 50, "Paper", 4L));
        List<ServerStatus> statuses = service.refresh().join();
        ServerStatus bedwars = statuses.get(1);
        assertTrue(bedwars.online());
        assertEquals(20, bedwars.players());
        assertFalse(bedwars.prismarin());
    }

    @Test
    void heartbeatAddsMetricsAndCoversFailedPings() {
        NetworkStatusService service = service((host, port, timeout) -> {
            if (port == 2) {
                throw new IOException("down");
            }
            return new MinecraftPinger.Result(3, 50, "Paper", 4L);
        });
        service.publish(new Heartbeat("lobby", 3, 50, 19.5, 12.0, 900, 2048, 3600, "1.21.11", 0L, null, null));
        service.publish(new Heartbeat("BW", 8, 16, 17.0, 40.0, 1200, 4096, 60, "1.21.11", 0L, null, null));
        List<ServerStatus> statuses = service.refresh().join();
        assertTrue(statuses.get(0).prismarin());
        assertEquals(19.5, statuses.get(0).tps());
        assertEquals(4L, statuses.get(0).latencyMillis());
        ServerStatus bedwars = statuses.get(1);
        assertTrue(bedwars.online());
        assertEquals(8, bedwars.players());
        assertEquals(-1L, bedwars.latencyMillis());
    }

    @Test
    void unreachableWithoutHeartbeatIsOffline() {
        NetworkStatusService service = service((host, port, timeout) -> {
            throw new IOException("down");
        });
        assertTrue(service.refresh().join().stream().noneMatch(ServerStatus::online));
    }

    @Test
    void stateComesFromHeartbeatOrMotd() {
        NetworkStatusService service = service((host, port, timeout) -> port == 1
                ? new MinecraftPinger.Result(3, 50, "Paper", 4L, "prismarin:RUNNING:Map Castle")
                : new MinecraftPinger.Result(8, 8, "Paper", 4L, "Waiting for players"));
        List<ServerStatus> statuses = service.refresh().join();
        assertEquals(GameState.RUNNING, statuses.get(0).state());
        assertEquals("Map Castle", statuses.get(0).stateText());
        assertFalse(statuses.get(0).joinable());
        assertEquals(GameState.WAITING, statuses.get(1).state());
        assertTrue(statuses.get(1).full());
        assertFalse(statuses.get(1).joinable());

        service.publish(new Heartbeat("bw", 2, 8, 20, 1, 1, 1, 1, "1.21.11", 0L, "STARTING", "10s"));
        assertEquals(GameState.STARTING, service.refresh().join().get(1).state());
    }
}
