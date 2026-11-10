package net.prismarin.shared.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import net.prismarin.shared.status.LobbySelector.Candidate;
import net.prismarin.shared.status.LobbySelector.Strategy;
import org.junit.jupiter.api.Test;

class LobbySelectorTest {

    private static Candidate<String> lobby(String name, int players, int softMax) {
        return new Candidate<>(name, players, softMax, true, false, true);
    }

    private static Candidate<String> premium(String name, int players, boolean permitted) {
        return new Candidate<>(name, players, 0, true, true, permitted);
    }

    @Test
    void leastPlayersPicksTheEmptiestLobby() {
        List<Candidate<String>> lobbies = List.of(lobby("a", 12, 50), lobby("b", 3, 50), lobby("c", 7, 50));
        assertEquals(Optional.of("b"), LobbySelector.pick(lobbies, Strategy.LEAST_PLAYERS, false));
    }

    @Test
    void leastPlayersKeepsConfigOrderOnTies() {
        List<Candidate<String>> lobbies = List.of(lobby("a", 4, 0), lobby("b", 4, 0));
        assertEquals(Optional.of("a"), LobbySelector.pick(lobbies, Strategy.LEAST_PLAYERS, false));
    }

    @Test
    void leastPlayersSkipsLobbiesAtTheirSoftMax() {
        List<Candidate<String>> lobbies = List.of(lobby("small", 5, 5), lobby("big", 20, 100));
        assertEquals(Optional.of("big"), LobbySelector.pick(lobbies, Strategy.LEAST_PLAYERS, false));
    }

    @Test
    void fillFirstFillsLobbiesInOrderUpToTheSoftMax() {
        assertEquals(Optional.of("a"), LobbySelector.pick(
                List.of(lobby("a", 49, 50), lobby("b", 0, 50)), Strategy.FILL_FIRST, false));
        assertEquals(Optional.of("b"), LobbySelector.pick(
                List.of(lobby("a", 50, 50), lobby("b", 10, 50), lobby("c", 0, 50)), Strategy.FILL_FIRST, false));
    }

    @Test
    void overflowGoesToTheEmptiestLobbyWhenAllAreAtTheirSoftMax() {
        List<Candidate<String>> lobbies = List.of(lobby("a", 60, 50), lobby("b", 52, 50));
        assertEquals(Optional.of("b"), LobbySelector.pick(lobbies, Strategy.FILL_FIRST, false));
        assertEquals(Optional.of("b"), LobbySelector.pick(lobbies, Strategy.LEAST_PLAYERS, false));
    }

    @Test
    void premiumLobbiesNeedExplicitOptIn() {
        List<Candidate<String>> lobbies = List.of(lobby("normal", 30, 0), premium("vip", 1, true));
        assertEquals(Optional.of("normal"), LobbySelector.pick(lobbies, Strategy.LEAST_PLAYERS, false));
        assertEquals(Optional.of("vip"), LobbySelector.pick(lobbies, Strategy.LEAST_PLAYERS, true));
    }

    @Test
    void neverPicksLobbiesWithoutPermissionOrOffline() {
        List<Candidate<String>> lobbies = List.of(
                premium("vip", 0, false),
                new Candidate<>("down", 0, 0, false, false, true),
                lobby("open", 40, 0));
        assertEquals(Optional.of("open"), LobbySelector.pick(lobbies, Strategy.LEAST_PLAYERS, true));
        assertEquals(Optional.empty(), LobbySelector.pick(
                List.of(premium("vip", 0, false), new Candidate<>("down", 0, 0, false, false, true)),
                Strategy.LEAST_PLAYERS, true));
    }

    @Test
    void randomStaysAmongOpenLobbies() {
        List<Candidate<String>> lobbies = List.of(lobby("full", 10, 10), lobby("a", 1, 10), lobby("b", 2, 10));
        Random random = new Random(7);
        for (int i = 0; i < 50; i++) {
            String picked = LobbySelector.pick(lobbies, Strategy.RANDOM, false, random).orElseThrow();
            assertTrue(picked.equals("a") || picked.equals("b"));
        }
    }

    @Test
    void parsesStrategyNamesLeniently() {
        assertEquals(Optional.of(Strategy.LEAST_PLAYERS), Strategy.parse("least-players"));
        assertEquals(Optional.of(Strategy.FILL_FIRST), Strategy.parse(" Fill_First "));
        assertEquals(Optional.of(Strategy.RANDOM), Strategy.parse("random"));
        assertEquals(Optional.empty(), Strategy.parse("fullest"));
        assertEquals(Optional.empty(), Strategy.parse(null));
        assertEquals("fill-first", Strategy.FILL_FIRST.id());
    }
}
