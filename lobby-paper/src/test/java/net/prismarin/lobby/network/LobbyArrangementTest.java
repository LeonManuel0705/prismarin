package net.prismarin.lobby.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.lobby.config.WorldLobby;
import org.junit.jupiter.api.Test;

class LobbyArrangementTest {

    private static NetworkServer server(String name, Map<String, String> properties) {
        return new NetworkServer(name, name, "127.0.0.1", 25565, NetworkServer.GROUP_LOBBY, properties);
    }

    private static WorldLobby world(String id) {
        return new WorldLobby(id, true, id, id, "PRISMARINE", 0, "");
    }

    private static List<String> ids(List<LobbyTarget> targets) {
        return targets.stream().map(LobbyTarget::id).toList();
    }

    @Test
    void singleWorldLobbyShowsThisServerInItsPlace() {
        List<LobbyTarget> targets = LobbyArrangement.arrange(List.of(world("main")),
                List.of(server("lobby-1", Map.of()), server("lobby-2", Map.of())), "lobby-1");
        assertEquals(List.of("lobby-1", "lobby-2"), ids(targets));
        assertTrue(targets.getFirst() instanceof LobbyTarget.ServerTarget);
    }

    @Test
    void severalWorldLobbiesReplaceThisServer() {
        List<LobbyTarget> targets = LobbyArrangement.arrange(List.of(world("a"), world("b")),
                List.of(server("lobby-0", Map.of()), server("lobby-1", Map.of()), server("lobby-2", Map.of())),
                "lobby-1");
        assertEquals(List.of("lobby-0", "a", "b", "lobby-2"), ids(targets));
    }

    @Test
    void worldLobbiesComeFirstWhenThisServerIsNotListed() {
        List<LobbyTarget> targets = LobbyArrangement.arrange(List.of(world("main")),
                List.of(server("lobby-2", Map.of())), "standalone");
        assertEquals(List.of("main", "lobby-2"), ids(targets));
        assertEquals(List.of("main"), ids(LobbyArrangement.arrange(List.of(world("main")), List.of(), "x")));
    }

    @Test
    void premiumComesFromThePermissionProperty() {
        LobbyTarget vip = new LobbyTarget.ServerTarget(server("vip", Map.of("permission", " rank.vip ")));
        assertTrue(vip.premium());
        assertEquals("rank.vip", vip.permission());
        assertFalse(new LobbyTarget.ServerTarget(server("free", Map.of())).premium());
        assertTrue(new LobbyTarget.WorldTarget(new WorldLobby("p", true, "p", "P", "GOLD_BLOCK", 0, "x.y"))
                .premium());
    }

    @Test
    void readsSoftMaxFromServerProperties() {
        assertEquals(60, LobbyArrangement.softMax(server("a", Map.of("soft-max-players", "60"))));
        assertEquals(0, LobbyArrangement.softMax(server("b", Map.of("soft-max-players", "many"))));
        assertEquals(0, LobbyArrangement.softMax(server("c", Map.of())));
    }
}
