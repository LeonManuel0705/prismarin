package net.prismarin.api.joinme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.prismarin.api.server.GameState;
import org.junit.jupiter.api.Test;

class JoinMeModelTest {

    private final UUID creator = UUID.randomUUID();

    private JoinMe joinMe(int maxJoins, int joins, long expires) {
        return new JoinMe("abc234", creator, "Lea", "Shadow", "bedwars-2", "bedwars", "arena-1", GameState.WAITING,
                "", Map.of("map", "Castle"), JoinMeAudience.groups("Lobby"), maxJoins, joins, 0L, expires);
    }

    @Test
    void aJoinMeIsOpenUntilItExpiresOrIsUsedUp() {
        JoinMe open = joinMe(3, 2, 1_000L);
        assertTrue(open.open(999L));
        assertFalse(open.open(1_000L));
        assertTrue(open.expired(1_000L));
        assertEquals(1, open.joinsLeft());
        assertTrue(open.withJoins(3).usedUp());
        assertFalse(open.withJoins(3).open(0L));
        assertEquals(-1, joinMe(0, 40, 1_000L).joinsLeft());
        assertFalse(joinMe(0, 40, 1_000L).usedUp());
        assertEquals(400L, open.remainingMillis(600L));
        assertEquals(0L, open.remainingMillis(5_000L));
    }

    @Test
    void theNameDependsOnWhetherTheViewerMaySeeThroughTheNick() {
        JoinMe nicked = joinMe(0, 0, 1L);
        assertTrue(nicked.nicked());
        assertEquals("Lea", nicked.name(true));
        assertEquals("Shadow", nicked.name(false));
        JoinMe plain = new JoinMe("abc234", creator, "Lea", "", "s", "g", "", null, null, null, null, -4, -1, 0L, 1L);
        assertFalse(plain.nicked());
        assertEquals("Lea", plain.name(false));
        assertEquals(GameState.UNKNOWN, plain.state());
        assertEquals(JoinMeAudience.NETWORK, plain.audience());
        assertEquals(0, plain.maxJoins());
        assertEquals(0, plain.joins());
        assertFalse(plain.handled());
    }

    @Test
    void placeholdersAndTheSpotComeFromTheRecord() {
        JoinMe joinMe = joinMe(0, 0, 1L);
        assertEquals(Optional.of("Castle"), joinMe.placeholder("map"));
        assertEquals(Optional.empty(), joinMe.placeholder("mode"));
        JoinMeSpot spot = joinMe.spot();
        assertEquals(creator, spot.creator());
        assertTrue(spot.inWorld("ARENA-1"));
        assertTrue(spot.inGroup("BedWars"));
        assertEquals("arena-2", joinMe.withWorld("arena-2").world());
        assertThrows(UnsupportedOperationException.class, () -> joinMe.placeholders().put("x", "y"));
    }

    @Test
    void audiencesNormaliseGroupsAndCircles() {
        JoinMeAudience lobbies = JoinMeAudience.groups(" Lobby ", "PREMIUM").only(JoinMeAudience.Circle.FRIENDS);
        assertEquals(Set.of("lobby", "premium"), lobbies.groups());
        assertTrue(lobbies.covers("LOBBY"));
        assertFalse(lobbies.covers("bedwars"));
        assertTrue(lobbies.social());
        assertTrue(JoinMeAudience.network().covers("anything"));
        assertFalse(JoinMeAudience.network().social());
        assertEquals(Optional.of(JoinMeAudience.Scope.GROUPS), JoinMeAudience.Scope.byId(" groups "));
        assertEquals(Optional.of(JoinMeAudience.Circle.CLAN), JoinMeAudience.Circle.byId("Clan"));
        assertEquals(Optional.empty(), JoinMeAudience.Circle.byId("guild"));
    }

    @Test
    void scopesMatchServersWorldsGroupsAndAnythingElse() {
        JoinMeSpot spot = new JoinMeSpot(creator, "bw-1", "BedWars", "Arena-3", GameState.WAITING);
        assertTrue(JoinMeScope.server().covers(spot));
        assertTrue(JoinMeScope.worlds("arena-3").covers(spot));
        assertFalse(JoinMeScope.worlds("arena-1").covers(spot));
        assertTrue(JoinMeScope.groups("bedwars").covers(spot));
        assertTrue(JoinMeScope.when(found -> found.state().joinable()).covers(spot));
        assertTrue(JoinMeScope.server().specificity() < JoinMeScope.groups("x").specificity());
        assertTrue(JoinMeScope.groups("x").specificity() < JoinMeScope.worlds("x").specificity());
        assertTrue(JoinMeScope.worlds("x").specificity() < JoinMeScope.when(found -> true).specificity());
    }

    @Test
    void requestsKeepTheirOverrides() {
        JoinMeRequest request = JoinMeRequest.of(creator);
        assertFalse(request.customMaxJoins());
        assertEquals(Optional.empty(), request.customLifetime());
        JoinMeRequest changed = request.withLifetime(Duration.ofMinutes(5)).withMaxJoins(4)
                .withAudience(JoinMeAudience.network()).withPlaceholder("map", "Castle").ignoringCooldown()
                .withoutCost();
        assertEquals(Optional.of(Duration.ofMinutes(5)), changed.customLifetime());
        assertTrue(changed.customMaxJoins());
        assertEquals(4, changed.maxJoins());
        assertEquals(Map.of("map", "Castle"), changed.placeholders());
        assertTrue(changed.ignoreCooldown());
        assertTrue(changed.free());
        assertEquals(Optional.empty(), request.withLifetime(Duration.ZERO).customLifetime());
    }

    @Test
    void resultStatusesHaveStableIds() {
        for (JoinMeResult.Status status : JoinMeResult.Status.values()) {
            assertEquals(Optional.of(status), JoinMeResult.Status.byId(status.id()));
        }
        assertEquals("not-enough-money", JoinMeResult.Status.NOT_ENOUGH_MONEY.id());
        assertEquals(1_500L, JoinMeResult.cooldown(1_500L).waitMillis());
        assertTrue(JoinMeResult.success(joinMe(0, 0, 1L)).success());
        assertEquals(Optional.empty(), JoinMeResult.of(JoinMeResult.Status.FAILED).joinMe());
        for (JoinMeExpiredEvent.Reason reason : JoinMeExpiredEvent.Reason.values()) {
            assertEquals(Optional.of(reason), JoinMeExpiredEvent.Reason.byId(reason.id()));
        }
    }

    @Test
    void theCreateEventCanBeChangedAndCancelled() {
        JoinMeCreateEvent event = new JoinMeCreateEvent(joinMe(0, 0, 1L).spot(), Map.of("map", "Castle"),
                Duration.ofMinutes(2), 0, null);
        event.placeholder("mode", "2x4");
        event.lifetime(Duration.ofMinutes(3));
        event.lifetime(Duration.ZERO);
        event.maxJoins(-3);
        event.audience(JoinMeAudience.groups("lobby"));
        event.line(9, "<gold>Castle</gold>");
        event.setCancelled(true);
        assertEquals(Map.of(9, "<gold>Castle</gold>"), event.lines());
        assertThrows(IllegalArgumentException.class, () -> event.line(0, "x"));
        assertEquals(Map.of(3, "x"), new JoinMeCreateEvent(joinMe(0, 0, 1L).spot(), Map.of(), Map.of(3, "x"),
                Duration.ofMinutes(1), 0, null).lines());
        assertEquals(Map.of("map", "Castle", "mode", "2x4"), event.placeholders());
        assertEquals(Duration.ofMinutes(3), event.lifetime());
        assertEquals(0, event.maxJoins());
        assertEquals(Set.of("lobby"), event.audience().groups());
        assertEquals(creator, event.creator());
        assertTrue(event.isCancelled());
    }

    @Test
    void handlersCanBeBuiltFromALambda() {
        JoinMeHandler handler = JoinMeHandler.of("arena", arrival -> JoinMeOutcome.HANDLED);
        assertEquals("arena", handler.id());
        assertEquals(0, handler.priority());
        assertTrue(handler.allows(joinMe(0, 0, 1L).spot()));
        assertEquals(Map.of(), handler.placeholders(joinMe(0, 0, 1L).spot()));
        assertEquals(Map.of(), handler.lines(joinMe(0, 0, 1L).spot()));
        assertEquals(JoinMeOutcome.HANDLED, handler.arrive(new JoinMeArrival(UUID.randomUUID(), joinMe(0, 0, 1L),
                false)));
    }
}
