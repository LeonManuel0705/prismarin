package net.prismarin.shared.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.prismarin.api.server.GameState;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerStatus;
import org.junit.jupiter.api.Test;

class ServerSelectorTest {

    private static NetworkServer server(String name) {
        return new NetworkServer(name, name, "127.0.0.1", 1, "bedwars", Map.of());
    }

    private static ServerStatus status(String name, int players, int max, GameState state) {
        return new ServerStatus(name, true, players, max, 1L, "", true, 20, 1, 1, 1, 1, Instant.EPOCH, state, "");
    }

    @Test
    void prefersStartingThenFullestWaiting() {
        Map<String, ServerStatus> statuses = Map.of(
                "a", status("a", 2, 8, GameState.WAITING),
                "b", status("b", 5, 8, GameState.WAITING),
                "c", status("c", 1, 8, GameState.STARTING),
                "d", status("d", 4, 8, GameState.RUNNING));
        Optional<NetworkServer> picked = ServerSelector.pick(
                List.of(server("a"), server("b"), server("c"), server("d")),
                name -> Optional.ofNullable(statuses.get(name)));
        assertEquals("c", picked.orElseThrow().name());
    }

    @Test
    void skipsFullRunningAndOfflineServers() {
        Map<String, ServerStatus> statuses = Map.of(
                "a", status("a", 8, 8, GameState.WAITING),
                "b", status("b", 2, 8, GameState.ENDING),
                "c", ServerStatus.offline("c", Instant.EPOCH));
        assertTrue(ServerSelector.pick(List.of(server("a"), server("b"), server("c")),
                name -> Optional.ofNullable(statuses.get(name))).isEmpty());
    }

    @Test
    void picksMostPlayersAmongWaiting() {
        Map<String, ServerStatus> statuses = Map.of(
                "a", status("a", 2, 8, GameState.WAITING),
                "b", status("b", 6, 8, GameState.WAITING));
        assertEquals("b", ServerSelector.pick(List.of(server("a"), server("b")),
                name -> Optional.ofNullable(statuses.get(name))).orElseThrow().name());
    }
}
