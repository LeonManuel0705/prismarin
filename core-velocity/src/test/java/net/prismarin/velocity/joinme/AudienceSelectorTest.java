package net.prismarin.velocity.joinme;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.prismarin.api.joinme.JoinMe;
import net.prismarin.api.joinme.JoinMeAudience;
import net.prismarin.api.server.GameState;
import org.junit.jupiter.api.Test;

class AudienceSelectorTest {

    private final UUID creator = UUID.randomUUID();
    private final UUID lobbyFriend = UUID.randomUUID();
    private final UUID lobbyStranger = UUID.randomUUID();
    private final UUID gameMate = UUID.randomUUID();
    private final UUID partyMember = UUID.randomUUID();
    private final UUID clanMember = UUID.randomUUID();
    private final UUID sameWorld = UUID.randomUUID();
    private final UUID muted = UUID.randomUUID();

    private final List<AudienceSelector.Candidate> candidates = List.of(
            new AudienceSelector.Candidate(creator, "bw-1", "bedwars", "arena-1", true),
            new AudienceSelector.Candidate(lobbyFriend, "lobby-1", "lobby", "world", true),
            new AudienceSelector.Candidate(lobbyStranger, "lobby-2", "lobby", "world", true),
            new AudienceSelector.Candidate(gameMate, "sw-1", "skywars", "map", true),
            new AudienceSelector.Candidate(partyMember, "lobby-1", "lobby", "world", true),
            new AudienceSelector.Candidate(clanMember, "sw-1", "skywars", "map", true),
            new AudienceSelector.Candidate(sameWorld, "bw-1", "bedwars", "ARENA-1", true),
            new AudienceSelector.Candidate(muted, "lobby-1", "lobby", "world", false));
    private final AudienceSelector.Circles circles = new AudienceSelector.Circles(Set.of(lobbyFriend, sameWorld),
            Set.of(partyMember, creator), Set.of(clanMember));

    private JoinMe joinMe(JoinMeAudience audience) {
        return new JoinMe("k3f9a2", creator, "Lea", "Lea", "bw-1", "bedwars", "arena-1", GameState.WAITING, "",
                Map.of(), audience, 0, 0, 0L, 1L);
    }

    @Test
    void groupScopesReachOnlyPlayersOnThoseGroups() {
        assertEquals(List.of(lobbyFriend, lobbyStranger, partyMember), AudienceSelector.select(
                joinMe(JoinMeAudience.groups("lobby")), candidates, circles, true));
        assertEquals(List.of(lobbyFriend, lobbyStranger, gameMate, partyMember, clanMember),
                AudienceSelector.select(joinMe(JoinMeAudience.groups("lobby", "skywars")), candidates, circles,
                        true));
    }

    @Test
    void theNetworkScopeReachesEverybodyButTheCreator() {
        assertEquals(List.of(lobbyFriend, lobbyStranger, gameMate, partyMember, clanMember, sameWorld),
                AudienceSelector.select(joinMe(JoinMeAudience.network()), candidates, circles, false));
        assertEquals(List.of(lobbyFriend, lobbyStranger, gameMate, partyMember, clanMember),
                AudienceSelector.select(joinMe(JoinMeAudience.network()), candidates, circles, true));
    }

    @Test
    void socialCirclesNarrowTheScope() {
        assertEquals(List.of(lobbyFriend), AudienceSelector.select(joinMe(JoinMeAudience.groups("lobby")
                .only(JoinMeAudience.Circle.FRIENDS)), candidates, circles, true));
        assertEquals(List.of(partyMember, clanMember), AudienceSelector.select(joinMe(JoinMeAudience.network()
                .only(JoinMeAudience.Circle.PARTY, JoinMeAudience.Circle.CLAN)), candidates, circles, true));
        assertEquals(List.of(lobbyFriend, sameWorld), AudienceSelector.select(joinMe(JoinMeAudience.network()
                .only(JoinMeAudience.Circle.FRIENDS)), candidates, circles, false));
        assertEquals(List.of(), AudienceSelector.select(joinMe(JoinMeAudience.network()
                .only(JoinMeAudience.Circle.FRIENDS)), candidates, AudienceSelector.Circles.NONE, true));
    }

    @Test
    void circlesWithoutSocialFiltersContainEverybody() {
        assertEquals(true, AudienceSelector.Circles.NONE.contains(JoinMeAudience.network(), lobbyStranger));
        assertEquals(true, circles.contains(JoinMeAudience.network().only(JoinMeAudience.Circle.CLAN), clanMember));
        assertEquals(false, circles.contains(JoinMeAudience.network().only(JoinMeAudience.Circle.CLAN),
                lobbyFriend));
    }
}
